package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.tracers.CallTracer.CallFrame
import io.ethers.core.types.tracers.CallTracer.CallLog
import kotlin.jvm.JvmOverloads

/**
 * Splits bundle transactions into the user operations they executed, see [Bundle] and [ExecutedUserOperation].
 *
 * Decoding only reads data the caller provides, it makes no RPC calls. [BundleReader] fetches that data from a node.
 *
 * **From the receipt alone**, operations are split by the EntryPoint's events. The EntryPoint validates every
 * operation, emits `BeforeExecution`, then executes the operations in order, each ending with its
 * `UserOperationEvent`. So an operation's execution logs are exactly the logs between the previous operation's
 * `UserOperationEvent` (or `BeforeExecution`) and its own. Validation-phase logs are only attributed when they are
 * EntryPoint events carrying the operation's hash.
 *
 * **With the transaction**, if it calls the EntryPoint directly, the bundle calldata is decoded too, adding the
 * submitted [UserOperation] and its decoded calls.
 *
 * **With a `callTracer` trace** (`withLog` enabled), bundles sent through other contracts are decoded as well, every
 * validation-phase log is attributed to its operation, the paymaster's `postOp` logs are split out, and the
 * EntryPoint's call frames are attached to the operations they belong to.
 *
 * Only logs and frames of the [entryPoints] given are considered EntryPoint markers, so a contract emitting
 * look-alike events cannot disturb the split. A reverted transaction executed no bundles.
 *
 * Without a trace, a bundle nested inside an operation of another bundle (an operation calling `handleOps` of a
 * different EntryPoint) is not detected, and the operations around it may be attributed to the wrong bundle.
 *
 * @param entryPoints the EntryPoints whose bundles to decode.
 * @param callDataDecoder decodes an operation's call data into calls, or null to skip decoding calls.
 */
class BundleDecoder @JvmOverloads constructor(
    val entryPoints: List<EntryPoint> = EntryPoint.CANONICAL,
    private val callDataDecoder: CallDataDecoder? = StandardCallDataDecoder,
) {
    /**
     * Failure to decode a transaction into bundles.
     */
    data class Error(override val message: String, override val cause: Throwable? = null) : ThrowableError

    /**
     * Decode the bundles of the transaction with [receipt].
     *
     * @param transaction the transaction, used to decode the bundle calldata when it calls an EntryPoint directly.
     * @param trace a `callTracer` trace of the transaction with logs, see [BundleDecoder].
     * @return the bundles in execution order, empty if the transaction executed none.
     */
    @JvmOverloads
    fun decode(
        receipt: TransactionReceipt,
        transaction: RPCTransaction? = null,
        trace: CallFrame? = null,
    ): Result<List<Bundle>, Error> {
        if (transaction != null && transaction.hash != receipt.transactionHash) {
            return failure(Error("Transaction ${transaction.hash} does not match receipt ${receipt.transactionHash}"))
        }

        // a reverted transaction rolled back every bundle it executed
        if (!receipt.isSuccessful) return success(emptyList())

        return try {
            success(decodeUnchecked(receipt, transaction, trace))
        } catch (e: BundleDecodingException) {
            failure(Error(e.message ?: "Failed to decode bundle", e.cause))
        } catch (e: Exception) {
            // malformed calldata surfaces as ABI decoding, cast or argument errors
            failure(Error("Malformed bundle: ${e.message}", e))
        }
    }

    private fun decodeUnchecked(receipt: TransactionReceipt, transaction: RPCTransaction?, trace: CallFrame?): List<Bundle> {
        val logs = receipt.logs
        val traceIndex = trace?.let { TraceIndex.build(it, logs, entryPoints) }

        // the bundle calls in execution order, from the trace, or else the transaction if it calls an EntryPoint
        val bundleCalls: List<BundleCall>? = when {
            traceIndex != null -> traceIndex.bundleCalls
            transaction != null -> {
                val entryPoint = entryPoints.firstOrNull { it.address == transaction.to }
                val input = transaction.data ?: Bytes.EMPTY
                val codec = entryPoint?.let { EntryPointCodec.of(it.version) }
                if (codec != null && codec.isBundleCall(input)) {
                    listOf(BundleCall(entryPoint, codec.decode(input), null))
                } else {
                    null
                }
            }

            else -> null
        }

        val segments = splitIntoSegments(logs)
        if (bundleCalls != null && bundleCalls.size != segments.size) {
            throw BundleDecodingException(
                "Transaction ${receipt.transactionHash} executed ${segments.size} bundles according to its logs, " +
                    "but ${bundleCalls.size} according to its calls",
            )
        }

        return segments.mapIndexed { i, segment ->
            val call = bundleCalls?.get(i)
            if (call != null && call.entryPoint != segment.entryPoint) {
                throw BundleDecodingException("Bundle $i was sent to ${call.entryPoint.address}, but its logs are from ${segment.entryPoint.address}")
            }
            buildBundle(receipt, segment, call, traceIndex)
        }
    }

    /**
     * A bundle's span of the receipt logs: validation logs before its `BeforeExecution`, and execution logs up to
     * and including its last `UserOperationEvent`.
     */
    private class Segment(
        val entryPoint: EntryPoint,
        val validation: List<Log>,
        val beforeExecution: Log,
        val execution: List<Log>,
        val trailing: List<Log>,
    )

    private fun splitIntoSegments(logs: List<Log>): List<Segment> {
        val markers = logs.indices.filter { i ->
            val log = logs[i]
            entryPointAt(log.address) != null && EntryPointV09.BeforeExecution.isLogValid(log)
        }
        if (markers.isEmpty()) return emptyList()

        val segments = ArrayList<Segment>(markers.size)
        var validationStart = 0
        for ((k, marker) in markers.withIndex()) {
            val entryPoint = entryPointAt(logs[marker].address)!!
            val nextMarker = if (k + 1 < markers.size) markers[k + 1] else logs.size

            // execution ends at the last UserOperationEvent of this EntryPoint before the next bundle
            var executionEnd = marker + 1
            for (i in marker + 1 until nextMarker) {
                if (logs[i].address == entryPoint.address && EntryPointV09.UserOperationEvent.isLogValid(logs[i])) {
                    executionEnd = i + 1
                }
            }

            // logs after the last bundle's execution are trailing logs of that bundle, e.g. the beneficiary
            // compensation, while logs between two bundles are part of the next bundle's validation
            val trailingEnd = if (k + 1 < markers.size) executionEnd else logs.size

            segments.add(
                Segment(
                    entryPoint = entryPoint,
                    validation = logs.subList(validationStart, marker),
                    beforeExecution = logs[marker],
                    execution = logs.subList(marker + 1, executionEnd),
                    trailing = logs.subList(executionEnd, trailingEnd),
                ),
            )
            validationStart = executionEnd
        }
        return segments
    }

    private fun entryPointAt(address: Address): EntryPoint? = entryPoints.firstOrNull { it.address == address }

    private fun buildBundle(
        receipt: TransactionReceipt,
        segment: Segment,
        call: BundleCall?,
        traceIndex: TraceIndex?,
    ): Bundle {
        val entryPoint = segment.entryPoint
        val epAddress = entryPoint.address
        val unattributedLogs = ArrayList<Log>()
        unattributedLogs.add(segment.beforeExecution)

        // split execution logs at each UserOperationEvent
        val opEvents = ArrayList<EntryPointV09.UserOperationEvent>()
        val opExecutionLogs = ArrayList<List<Log>>()
        val opAggregatorMarkers = ArrayList<Address?>()
        var current = ArrayList<Log>()
        var currentAggregator: Address? = null
        for (log in segment.execution) {
            if (log.address == epAddress) {
                val aggregatorChanged = EntryPointV09.SignatureAggregatorChanged.decodeOrNull(log)
                if (aggregatorChanged != null) {
                    currentAggregator = aggregatorChanged.aggregator.takeIf { it != Address.ZERO }
                    unattributedLogs.add(log)
                    continue
                }

                val event = EntryPointV09.UserOperationEvent.decodeOrNull(log)
                if (event != null) {
                    current.add(log)
                    opEvents.add(event)
                    opExecutionLogs.add(current)
                    opAggregatorMarkers.add(currentAggregator)
                    current = ArrayList()
                    continue
                }
            }
            current.add(log)
        }
        // never happens by construction, execution ends with a UserOperationEvent
        unattributedLogs.addAll(current)
        unattributedLogs.addAll(segment.trailing)

        val decoded = call?.decoded
        if (decoded != null && decoded.userOperations.size != opEvents.size) {
            throw BundleDecodingException(
                "Bundle calldata has ${decoded.userOperations.size} operations, but ${opEvents.size} were executed",
            )
        }

        val hashes = opEvents.map { Hash(it.userOpHash.asByteArray()) }
        val indexByHash = HashMap<Hash, Int>(hashes.size)
        hashes.forEachIndexed { i, hash -> indexByHash[hash] = i }

        val frames = if (traceIndex != null && call?.node != null) traceIndex.attributeFrames(call, hashes) else null

        // validation logs: EntryPoint events carrying an operation hash, or anything the trace attributes
        val opValidationLogs = List(opEvents.size) { ArrayList<Log>() }
        for (log in segment.validation) {
            val owner = traceIndex?.validationOwner(log, call)
                ?: if (log.address == epAddress) validationEventHash(log)?.let { indexByHash[it] } else null
            if (owner != null) opValidationLogs[owner].add(log) else unattributedLogs.add(log)
        }

        unattributedLogs.sortBy { it.logIndex }

        val operations = opEvents.indices.map { i ->
            val event = opEvents[i]
            val userOperation = decoded?.userOperations?.get(i)
            if (userOperation != null && (userOperation.sender != event.sender || userOperation.nonce != event.nonce)) {
                throw BundleDecodingException(
                    "Operation $i of the bundle calldata (${userOperation.sender}, nonce ${userOperation.nonce}) does " +
                        "not match its UserOperationEvent (${event.sender}, nonce ${event.nonce})",
                )
            }

            val validationLogs = opValidationLogs[i]
            var executionLogs: List<Log> = opExecutionLogs[i]
            var postOpLogs: List<Log> = emptyList()
            val opFrames = frames?.get(i)
            if (opFrames != null && traceIndex != null) {
                val postOpIndices = traceIndex.logIndicesOf(opFrames.postOp)
                if (postOpIndices.isNotEmpty()) {
                    postOpLogs = executionLogs.filter { traceIndex.receiptIndexOf(it) in postOpIndices }
                    executionLogs = executionLogs.filter { traceIndex.receiptIndexOf(it) !in postOpIndices }
                }
            }

            val hash = hashes[i]
            val allLogs = validationLogs + executionLogs + postOpLogs
            ExecutedUserOperation(
                userOpHash = hash,
                entryPoint = entryPoint,
                transactionHash = receipt.transactionHash,
                blockHash = receipt.blockHash,
                blockNumber = receipt.blockNumber,
                index = i,
                sender = event.sender,
                nonce = event.nonce,
                paymaster = event.paymaster.takeIf { it != Address.ZERO },
                success = event.success,
                actualGasCost = event.actualGasCost,
                actualGasUsed = event.actualGasUsed,
                userOperation = userOperation,
                aggregator = decoded?.aggregators?.get(i) ?: opAggregatorMarkers[i],
                factory = findEvent(allLogs, epAddress, hash) { EntryPointV09.AccountDeployed.decodeOrNull(it) }?.factory,
                eip7702Delegate = findEvent(allLogs, epAddress, hash) { EntryPointV09.EIP7702AccountInitialized.decodeOrNull(it) }?.delegate,
                revertReason = findEvent(allLogs, epAddress, hash) { EntryPointV09.UserOperationRevertReason.decodeOrNull(it) }?.revertReason,
                postOpRevertReason = findEvent(allLogs, epAddress, hash) { EntryPointV09.PostOpRevertReason.decodeOrNull(it) }?.revertReason,
                prefundTooLow = findEvent(allLogs, epAddress, hash) { EntryPointV09.UserOperationPrefundTooLow.decodeOrNull(it) } != null,
                calls = userOperation?.let { decodeCalls(it) },
                validationLogs = validationLogs,
                executionLogs = executionLogs,
                postOpLogs = postOpLogs,
                validationFrames = opFrames?.validation?.map { it.frame },
                executionFrames = opFrames?.execution?.map { it.frame },
            )
        }

        return Bundle(
            transactionHash = receipt.transactionHash,
            blockHash = receipt.blockHash,
            blockNumber = receipt.blockNumber,
            transactionIndex = receipt.transactionIndex,
            bundler = receipt.from,
            entryPoint = entryPoint,
            beneficiary = decoded?.beneficiary,
            userOperations = operations,
            unattributedLogs = unattributedLogs,
            unattributedCalls = if (traceIndex != null && call != null) traceIndex.unattributedFrames(call, frames) else emptyList(),
        )
    }

    private fun decodeCalls(userOperation: UserOperation): List<Call>? {
        val decoder = callDataDecoder ?: return null
        return try {
            decoder.decodeCalls(userOperation.sender, userOperation.callData)
        } catch (e: Exception) {
            null
        }
    }

    private fun validationEventHash(log: Log): Hash? {
        if (log.topics.size < 2) return null
        val isValidationEvent = EntryPointV09.AccountDeployed.isLogValid(log) ||
            EntryPointV09.IgnoredInitCode.isLogValid(log) ||
            EntryPointV09.EIP7702AccountInitialized.isLogValid(log)
        return if (isValidationEvent) log.topics[1] else null
    }

    private inline fun <T : Any> findEvent(logs: List<Log>, entryPoint: Address, hash: Hash, decode: (Log) -> T?): T? {
        for (log in logs) {
            if (log.address != entryPoint || log.topics.size < 2 || log.topics[1] != hash) continue
            val event = decode(log)
            if (event != null) return event
        }
        return null
    }

    internal class BundleDecodingException(message: String) : RuntimeException(message)
}

/**
 * A bundle call: its decoded calldata, and its trace node if a trace was given.
 */
internal class BundleCall(val entryPoint: EntryPoint, val decoded: DecodedHandleOps, val node: TraceNode?) {
    val frame: CallFrame?
        get() = node?.frame
}

/**
 * A trace frame with identity semantics, so equal-looking frames (e.g. two identical calls) stay distinct.
 */
internal class TraceNode(val frame: CallFrame, val children: List<TraceNode>) {
    fun flatten(out: MutableList<TraceNode> = ArrayList()): List<TraceNode> {
        out.add(this)
        children.forEach { it.flatten(out) }
        return out
    }

    companion object {
        fun of(frame: CallFrame): TraceNode = TraceNode(frame, frame.calls?.map(::of) ?: emptyList())
    }
}

/**
 * Frames of one operation: its validation frames, its execution frames, and the paymaster `postOp` frames among
 * the execution frames' descendants.
 */
internal class OperationFrames(
    val validation: List<TraceNode>,
    val execution: List<TraceNode>,
    val postOp: List<TraceNode>,
)

/**
 * Index of a `callTracer` trace: the bundle calls it contains, and which frame emitted each receipt log.
 */
internal class TraceIndex private constructor(
    val bundleCalls: List<BundleCall>,
    // receipt log index -> nodes from the root to the node that emitted the log; null if logs could not be matched
    private val logOwners: List<List<TraceNode>>?,
    receiptLogs: List<Log>,
) {
    private val receiptIndexByLog = HashMap<Log, Int>(receiptLogs.size).apply {
        receiptLogs.forEachIndexed { i, log -> put(log, i) }
    }
    private val validationAttribution = HashMap<TraceNode, Map<TraceNode, Int>>()
    private val frameAttribution = HashMap<TraceNode, List<OperationFrames>?>()

    fun receiptIndexOf(log: Log): Int = receiptIndexByLog[log] ?: -1

    /**
     * Receipt indices of all logs emitted by [nodes] and their descendants.
     */
    fun logIndicesOf(nodes: List<TraceNode>): Set<Int> {
        val owners = logOwners ?: return emptySet()
        if (nodes.isEmpty()) return emptySet()
        val targets = nodes.toHashSet()
        val ret = HashSet<Int>()
        owners.forEachIndexed { i, chain -> if (chain.any { it in targets }) ret.add(i) }
        return ret
    }

    /**
     * Return the index of the operation whose validation frame emitted [log], or null.
     */
    fun validationOwner(log: Log, call: BundleCall?): Int? {
        val owners = logOwners ?: return null
        val node = call?.node ?: return null
        val chain = owners.getOrNull(receiptIndexOf(log)) ?: return null
        val attribution = validationAttribution[node] ?: return null
        for (n in chain) {
            val owner = attribution[n]
            if (owner != null) return owner
        }
        return null
    }

    /**
     * Attribute the direct subcalls of the bundle's EntryPoint frame to its operations, whose hashes are [hashes].
     * Returns null if the frames do not have the expected shape. Must be called before [validationOwner].
     */
    fun attributeFrames(call: BundleCall, hashes: List<Hash>): List<OperationFrames>? {
        val node = call.node ?: return null
        if (frameAttribution.containsKey(node)) return frameAttribution[node]

        val result = computeFrames(call, node, hashes)
        frameAttribution[node] = result
        return result
    }

    fun unattributedFrames(call: BundleCall, attributed: List<OperationFrames>?): List<CallFrame> {
        val node = call.node ?: return emptyList()
        if (attributed == null) return node.children.map { it.frame }
        val used = HashSet<TraceNode>()
        attributed.forEach {
            used.addAll(it.validation)
            used.addAll(it.execution)
        }
        return node.children.filter { it !in used }.map { it.frame }
    }

    private fun computeFrames(call: BundleCall, node: TraceNode, hashes: List<Hash>): List<OperationFrames>? {
        val frame = node.frame
        val codec = EntryPointCodec.of(call.entryPoint.version)
        val subcalls = node.children
        val beforeExecution = frame.logs?.firstOrNull { it.topics.firstOrNull() == EntryPointV09.BeforeExecution.abi.topicId }
            ?: return null
        if (beforeExecution.position < 0) return null
        val executionStart = beforeExecution.position.coerceAtMost(subcalls.size)

        val indexByHash = HashMap<Hash, Int>()
        hashes.forEachIndexed { i, hash -> indexByHash[hash] = i }
        val aggregators = call.decoded.aggregators.filterNotNull().toSet()

        // validation: account creation frames precede their validateUserOp, which carries the operation hash
        val validation = List(hashes.size) { ArrayList<TraceNode>() }
        val attribution = HashMap<TraceNode, Int>()
        var pending = ArrayList<TraceNode>()
        for (i in 0 until executionStart) {
            val sub = subcalls[i]
            val input = sub.frame.input
            val isAccountValidation = input.startsWith(codec.validateUserOpSelector)
            val isPaymasterValidation = input.startsWith(codec.validatePaymasterUserOpSelector)
            val owner = if (isAccountValidation || isPaymasterValidation) {
                EntryPointCodec.userOpHashArgument(input)?.let { indexByHash[it] }
            } else {
                null
            }

            when {
                owner != null && isAccountValidation -> {
                    pending.add(sub)
                    pending.forEach { attribution[it] = owner }
                    validation[owner].addAll(pending)
                    pending = ArrayList()
                }

                owner != null -> {
                    attribution[sub] = owner
                    validation[owner].add(sub)
                }

                // aggregator signature checks belong to the whole bundle
                sub.frame.to != null && sub.frame.to in aggregators -> Unit

                else -> pending.add(sub)
            }
        }
        validationAttribution[node] = attribution

        // execution: each operation starts with the EntryPoint's self-call to innerHandleOp
        val execution = List(hashes.size) { ArrayList<TraceNode>() }
        var op = -1
        for (i in executionStart until subcalls.size) {
            val sub = subcalls[i]
            if (sub.frame.to == call.entryPoint.address && sub.frame.input.startsWith(codec.innerHandleOpSelector)) {
                op++
                if (op >= hashes.size) return null
            }

            if (op < 0) continue
            // the final value transfer to the beneficiary belongs to the bundle
            val isCompensation = i == subcalls.size - 1 && sub.frame.to == call.decoded.beneficiary && sub.frame.input.isEmpty
            if (!isCompensation) execution[op].add(sub)
        }
        if (op != hashes.size - 1) return null

        return hashes.indices.map { i ->
            val paymaster = call.decoded.userOperations[i].paymaster
            val postOp = if (paymaster == null) {
                emptyList()
            } else {
                execution[i].flatMap { it.flatten() }.filter {
                    it.frame.to == paymaster && it.frame.input.startsWith(codec.postOpSelector)
                }
            }
            OperationFrames(validation[i], execution[i], postOp)
        }
    }

    companion object {
        fun build(root: CallFrame, receiptLogs: List<Log>, entryPoints: List<EntryPoint>): TraceIndex {
            val rootNode = TraceNode.of(root)
            val bundleCalls = ArrayList<BundleCall>()
            collectBundleCalls(rootNode, entryPoints, bundleCalls)

            // logs in emission order, each with the chain of nodes that emitted it
            val traced = ArrayList<Pair<CallLog, List<TraceNode>>>()
            collectLogs(rootNode, ArrayList(), traced)
            if (traced.isNotEmpty() && traced.all { it.first.index >= 0 }) {
                traced.sortBy { it.first.index }
            }

            val owners = if (logsMatch(traced, receiptLogs)) traced.map { it.second } else null
            return TraceIndex(bundleCalls, owners, receiptLogs)
        }

        private fun collectBundleCalls(node: TraceNode, entryPoints: List<EntryPoint>, out: MutableList<BundleCall>) {
            val frame = node.frame
            if (frame.isError) return

            val entryPoint = entryPoints.firstOrNull { it.address == frame.to }
            if (entryPoint != null && frame.type == "CALL") {
                val codec = EntryPointCodec.of(entryPoint.version)
                if (codec.isBundleCall(frame.input)) {
                    out.add(BundleCall(entryPoint, codec.decode(frame.input), node))
                    return
                }
            }

            node.children.forEach { collectBundleCalls(it, entryPoints, out) }
        }

        /**
         * Walk the frames in execution order. `CallLog.position` is the number of subcalls the frame made before
         * emitting the log, which interleaves a frame's own logs with its subcalls' logs. Reverted frames are
         * skipped, as their logs are not part of the receipt.
         */
        private fun collectLogs(node: TraceNode, chain: ArrayList<TraceNode>, out: MutableList<Pair<CallLog, List<TraceNode>>>) {
            if (node.frame.isError) return

            chain.add(node)
            val snapshot = chain.toList()
            val logs = node.frame.logs ?: emptyList()

            var logIdx = 0
            for (i in node.children.indices) {
                while (logIdx < logs.size && logs[logIdx].position in 0..i) {
                    out.add(logs[logIdx] to snapshot)
                    logIdx++
                }
                collectLogs(node.children[i], chain, out)
            }
            while (logIdx < logs.size) {
                out.add(logs[logIdx] to snapshot)
                logIdx++
            }
            chain.removeAt(chain.size - 1)
        }

        private fun logsMatch(traced: List<Pair<CallLog, List<TraceNode>>>, receiptLogs: List<Log>): Boolean {
            if (traced.size != receiptLogs.size) return false
            for (i in traced.indices) {
                val log = traced[i].first
                val receiptLog = receiptLogs[i]
                if (log.address != receiptLog.address || log.topics != receiptLog.topics || log.data != receiptLog.data) {
                    return false
                }
            }
            return true
        }
    }
}
