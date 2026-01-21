package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.io.SegmentedStringWriter
import com.fasterxml.jackson.core.util.BufferRecycler
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.util.TokenBuffer
import io.channels.core.Channel
import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.forEachObjectField
import io.ethers.core.isNextTokenArrayEnd
import io.ethers.core.success
import io.ethers.logger.dbg
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.inf
import io.ethers.logger.trc
import io.ethers.logger.wrn
import io.ethers.providers.types.BatchRpcRequest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.jctools.queues.MpscUnboundedXaddArrayQueue
import org.jctools.queues.SpscUnboundedArrayQueue
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * [JsonRpcClient] implementation via WS transport. Supports single, batch, and subscription requests.
 *
 * Reconnection happens automatically when WS connection is in dropped / fail state. All unfinished requests for which
 * the response was not received are automatically resubmitted when new connection is established.
 */
class WsClient(
    url: String,
    private val client: OkHttpClient,
    headers: Map<String, String> = emptyMap(),
    private val jsonMapper: JsonMapper = Jackson.MAPPER,
) : JsonRpcClient {
    @JvmOverloads
    constructor(url: String, config: RpcClientConfig = RpcClientConfig()) : this(
        url,
        config.client!!,
        config.requestHeaders,
        config.jsonMapper,
    )

    private val LOG = getLogger()

    // these are modified by a single thread
    private val inFlightRequests = HashMap<Long, CompletableRequest<*>>()
    private val inFlightBatchRequests = HashMap<Long, Pair<CompletableBatchRequest, HashMap<Long, Int>>>()
    private val inFlightSubscriptionRequests = HashMap<Long, CompletableSubscriptionRequest<*>>()

    // these are modified by multiple threads when unsubscribing
    private val requestIdToSubscription = ConcurrentHashMap<Long, Subscription<*>>()
    private val serverIdToSubscription = ConcurrentHashMap<String, Subscription<*>>()

    // use lock instead of "synchronized" to avoid thread pinning if using virtual thread for processor
    private val eventLock = ReentrantLock()
    private val newEventCondition = eventLock.newCondition()
    private val connectionOpenedCondition = eventLock.newCondition()
    private val connectionClosedCondition = eventLock.newCondition()

    // queues chosen based on https://vmlens.com/articles/scale/scalability_queue/
    private val messageQueue = SpscUnboundedArrayQueue<String>(512)
    private val requestQueue = MpscUnboundedXaddArrayQueue<CompletableRequest<*>>(512)
    private val batchRequestQueue = MpscUnboundedXaddArrayQueue<CompletableBatchRequest>(256)
    private val subscriptionQueue = MpscUnboundedXaddArrayQueue<CompletableSubscriptionRequest<*>>(128)

    @Volatile
    private var reconnect = false

    @Volatile
    private var stopping = false

    init {
        val requestHeaders = Headers.Builder().apply { headers.forEach { (key, value) -> add(key, value) } }.build()
        val wsRequest = Request.Builder().url(url).headers(requestHeaders).build()
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                LOG.inf { "WebSocket connection opened" }

                eventLock.withLock { connectionOpenedCondition.signalAll() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                messageQueue.add(text)

                eventLock.withLock { newEventCondition.signalAll() }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // will trigger "onFailure" callback
                throw IOException("Binary messages are not supported")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                LOG.dbg { "WebSocket connection closing: $code $reason. Closing our side as well." }

                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LOG.dbg { "WebSocket connection closed: $code $reason" }

                eventLock.withLock { connectionClosedCondition.signalAll() }

                requestReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                eventLock.withLock { connectionClosedCondition.signalAll() }

                if (stopping) {
                    LOG.dbg(t) { "WebSocket failure ignored because we are stopping" }
                    return
                }

                LOG.err(t) { "WebSocket failure" }
                requestReconnect()
            }

            private fun requestReconnect() = eventLock.withLock {
                reconnect = true

                // wake up the processor thread, in case it's waiting for new events
                newEventCondition.signalAll()
            }
        }

        val processorThread = AsyncExecutor.maybeVirtualThread {
            LOG.inf { "Starting WebSocket processor thread and connecting to websocket" }

            var websocket: WebSocket
            eventLock.withLock {
                websocket = client.newWebSocket(wsRequest, wsListener)

                connectionOpenedCondition.await(
                    client.connectTimeoutMillis.toLong(),
                    TimeUnit.MILLISECONDS,
                )
            }

            var requestId = 1L
            var msg: String?
            var request: CompletableRequest<*>?
            var batchRequest: CompletableBatchRequest?
            var subscriptionRequest: CompletableSubscriptionRequest<*>?

            val bufferRecycler = BufferRecycler()
            var lastTimeoutCheck = TimeSource.Monotonic.markNow()

            while (!stopping) {
                try {
                    // first, process all messages in the queue
                    while (messageQueue.poll().also { msg = it } != null) {

                        // messages are terminated by a new line. Remove it when logging to get nicer output
                        LOG.trc { "Processing message: ${msg?.removeSuffix(System.lineSeparator())}" }

                        try {
                            handleMessage(msg!!)
                        } catch (e: Exception) {
                            LOG.err(e) { "Error processing message, skipping: $msg" }
                        }
                    }

                    // second, check if we need to reconnect, initiating re-subscription for existing subs
                    if (reconnect) {
                        var reconnectSuccessful = false

                        while (!reconnectSuccessful && !stopping) {
                            LOG.dbg { "Trying to reconnect WebSocket" }

                            // close the old websocket, just in case. Do this while holding a lock, so we don't start
                            // awaiting on the condition after the ws listener already notified it, which would lead
                            // to a deadlock
                            eventLock.withLock {
                                if (websocket.close(1000, "Close")) {
                                    connectionClosedCondition.await()
                                }
                            }

                            // Clear the flag in each iteration since it might be set again while reconnecting.
                            // Has to be done after closing in case connection is still open, since it will trigger
                            // a new reconnection attempt
                            reconnect = false

                            // and wait explicitly for the new connection to be opened, the delay acting as a back-off
                            reconnectSuccessful = eventLock.withLock {
                                // sends the connection request asynchronously, so the lock won't be held very long
                                websocket = client.newWebSocket(wsRequest, wsListener)

                                connectionOpenedCondition.await(
                                    client.connectTimeoutMillis.toLong(),
                                    TimeUnit.MILLISECONDS,
                                )
                            }

                            if (!reconnectSuccessful) {
                                handleTimeouts(client.readTimeoutMillis.toLong().milliseconds)

                                Thread.sleep(2000L)
                            }
                        }

                        if (stopping) {
                            break
                        }

                        // re-queue all requests
                        if (inFlightRequests.isNotEmpty()) {
                            val iter = inFlightRequests.iterator()
                            while (iter.hasNext()) {
                                val value = iter.next().value
                                LOG.dbg { "Re-queued in-flight request: $value" }

                                requestQueue.add(value)
                                iter.remove()
                            }
                        }

                        // re-queue all batch requests
                        if (inFlightBatchRequests.isNotEmpty()) {
                            val iter = inFlightBatchRequests.iterator()
                            while (iter.hasNext()) {
                                val (batchRequest, _) = iter.next().value
                                LOG.dbg { "Re-queued in-flight batch request: $batchRequest" }

                                batchRequestQueue.add(batchRequest)
                                iter.remove()
                            }
                        }

                        // re-queue all subscription requests
                        if (inFlightSubscriptionRequests.isNotEmpty()) {
                            val iter = inFlightSubscriptionRequests.iterator()
                            while (iter.hasNext()) {
                                val value = iter.next().value
                                LOG.dbg { "Re-queued in-flight subscription request: $value" }

                                subscriptionQueue.add(value)
                                iter.remove()
                            }
                        }

                        // send resubscribe requests for all existing streams, without clearing the map - we need to preserve streams
                        if (requestIdToSubscription.isNotEmpty()) {
                            for ((id, sub) in requestIdToSubscription) {
                                LOG.dbg { "Resent stream re-subscription: $id" }

                                val writer = SegmentedStringWriter(bufferRecycler)
                                jsonMapper.createGenerator(writer).use { gen ->
                                    gen.writeJsonRpcRequest("eth_subscribe", id, sub.params)
                                }

                                websocket.send(writer.andClear)
                            }
                        }
                    }

                    // third, process all single and batch requests in the queue
                    while (requestQueue.poll().also { request = it } != null) {
                        val id = requestId++
                        val writer = SegmentedStringWriter(bufferRecycler)
                        jsonMapper.createGenerator(writer).use { gen ->
                            gen.writeJsonRpcRequest(request!!.method, id, request.params)
                        }

                        val req = writer.andClear

                        LOG.trc { "Processing request: $req" }
                        inFlightRequests[id] = request!!
                        websocket.send(req)
                    }

                    while (batchRequestQueue.poll().also { batchRequest = it } != null) {
                        var batchId = -1L
                        val idToIndex = HashMap<Long, Int>(batchRequest!!.request.requests.size, 1.0F)
                        val writer = SegmentedStringWriter(bufferRecycler)
                        jsonMapper.createGenerator(writer).use { gen ->
                            gen.writeStartArray()

                            for (i in batchRequest.request.requests.indices) {
                                val req = batchRequest.request.requests[i]

                                // use id of the first request to identify the batch
                                val id = requestId++
                                if (batchId == -1L) {
                                    batchId = id
                                }
                                idToIndex[id] = i
                                gen.writeJsonRpcRequest(req.method, id, req.params)
                            }

                            gen.writeEndArray()
                        }

                        val req = writer.andClear

                        LOG.trc { "Processing batch request: $req" }
                        inFlightBatchRequests[batchId] = Pair(batchRequest, idToIndex)
                        websocket.send(req)
                        batchRequest.request.markAsSent()
                    }

                    // fourth, process all subscription requests in the queue
                    while (subscriptionQueue.poll().also { subscriptionRequest = it } != null) {
                        val id = requestId++
                        val writer = SegmentedStringWriter(bufferRecycler)
                        jsonMapper.createGenerator(writer).use { gen ->
                            gen.writeJsonRpcRequest("eth_subscribe", id, subscriptionRequest!!.params)
                        }

                        val req = writer.andClear

                        LOG.trc { "Processing subscription request: $req" }
                        inFlightSubscriptionRequests[id] = subscriptionRequest!!
                        websocket.send(req)
                    }

                    // check and handle timed-out requests every 1000ms
                    if (lastTimeoutCheck.elapsedNow() > 1000.milliseconds) {
                        handleTimeouts(client.readTimeoutMillis.toLong().milliseconds)
                        lastTimeoutCheck = TimeSource.Monotonic.markNow()
                    }

                    eventLock.withLock {
                        // do a quick check if any new events arrived while processing requests, while holding the lock
                        // to prevent race conditions, so we don't wait unnecessarily. We wait for max 1 second, so we
                        // still process timeouts in a timely manner, even if there are no new events.
                        if (messageQueue.isEmpty && requestQueue.isEmpty && batchRequestQueue.isEmpty && subscriptionQueue.isEmpty) {
                            newEventCondition.await(1, TimeUnit.SECONDS)
                        }
                    }
                } catch (e: Exception) {
                    LOG.err(e) { "Exception when processing events, reconnecting WebSocket" }

                    reconnect = true
                }
            }

            // close the websocket, expire all remaining requests, and unsubscribe from all subscriptions
            websocket.close(1000, "Close")
            handleTimeouts(Duration.ZERO)
            requestIdToSubscription.values.forEach { it.stream.close() }
        }

        processorThread.name = "WsClient-Processor-${processorThread.id}"
        processorThread.start()
    }

    private fun handleTimeouts(timeout: Duration) {
        removeTimedOutRequests(inFlightRequests, timeout)
        removeTimedOutRequests(inFlightSubscriptionRequests, timeout)
        removeTimedOutBatchRequests(timeout)
    }

    private fun removeTimedOutBatchRequests(timeout: Duration) {
        // skip expiration if timeout is not set or requests are empty
        if (timeout.inWholeMilliseconds < 0 || inFlightBatchRequests.isEmpty()) {
            return
        }

        val iter = inFlightBatchRequests.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val (batchRequest, _) = entry.value
            if (batchRequest.expireIfTimedOut(timeout)) {
                LOG.wrn { "Batch request timed out: ID ${entry.key}" }
                iter.remove()
            }
        }
    }

    private fun <T : ExpiringRequest> removeTimedOutRequests(requests: MutableMap<Long, T>, timeout: Duration) {
        // skip expiration if timeout is not set or requests are empty
        if (timeout.inWholeMilliseconds < 0 || requests.isEmpty()) {
            return
        }

        val iter = requests.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.expireIfTimedOut(timeout)) {
                LOG.wrn { "Request timed out: ID ${entry.key}" }
                iter.remove()
            }
        }
    }

    private fun handleMessage(text: String) {
        jsonMapper.createAndInitParser(text).use { parser ->
            if (parser.currentToken == JsonToken.START_ARRAY) {
                handleBatchResponse(text, parser)
                return
            }

            var id: Long = -1
            var method: String? = null
            var resultBuffer: TokenBuffer? = null
            var paramsBuffer: TokenBuffer? = null
            var error: RpcError? = null

            parser.forEachObjectField { field ->
                when (field) {
                    "jsonrpc" -> {}
                    "id" -> id = parser.longValue
                    "method" -> method = parser.text
                    "result" -> {
                        // avoid buffering if possible
                        if (id != -1L) {
                            handleResponse(id, parser, error)
                            return@use
                        }

                        resultBuffer = TokenBuffer(parser)
                        resultBuffer.copyCurrentStructure(parser)
                    }

                    "params" -> {
                        // avoid buffering if possible
                        if (method != null) {
                            handleNotification(parser)
                            return@use
                        }

                        paramsBuffer = TokenBuffer(parser)
                        paramsBuffer.copyCurrentStructure(parser)
                    }

                    "error" -> error = jsonMapper.readValue(parser, RpcError::class.java)

                    // if both id and error are present treat it as a valid error response
                    else -> {
                        if (id != -1L && error != null) {
                            handleResponse(id, parser, error)
                            return@use
                        }

                        val invalid = RpcError(
                            RpcError.CODE_INVALID_RESPONSE,
                            "Invalid response",
                            jsonMapper.valueToTree(text),
                        )

                        if (id != -1L) {
                            handleResponse(id, parser, invalid)
                            return@use
                        }

                        throw Exception("Invalid response: $text")
                    }
                }
            }

            // DO NOT CHANGE ORDER OF THESE OPERATIONS
            // order of operations matters here. All response messages have "id" field, but only notifications have "method"
            when {
                method != null && paramsBuffer != null -> paramsBuffer.use { buff ->
                    buff.asParser().use {
                        it.nextToken()
                        handleNotification(it)
                    }
                }

                id != -1L && error != null -> handleResponse(id, parser, error)

                id != -1L && resultBuffer != null -> resultBuffer.use { buff ->
                    buff.asParser().use {
                        it.nextToken()
                        handleResponse(id, it, null)
                    }
                }

                else -> {
                    val invalid = RpcError(
                        RpcError.CODE_INVALID_RESPONSE,
                        "Invalid response",
                        jsonMapper.valueToTree(text),
                    )

                    if (id != -1L) {
                        handleResponse(id, parser, invalid)
                    } else {
                        throw Exception("Invalid response: $text")
                    }
                }
            }
        }
    }

    private fun handleBatchResponse(text: String, p: JsonParser) {
        var batch: CompletableBatchRequest? = null
        var requestIndexPerId: HashMap<Long, Int>? = null

        while (!p.isNextTokenArrayEnd()) {
            var responseId = -1L
            var result: Any? = null
            var error: RpcError? = null
            var buffer: TokenBuffer? = null

            p.forEachObjectField { field ->
                when (field) {
                    "id" -> {
                        responseId = p.longValue
                        if (batch == null) {
                            val data = getBatchFromRequestId(responseId)
                            if (data != null) {
                                batch = data.first
                                requestIndexPerId = data.second
                            }
                        }
                    }
                    "jsonrpc" -> {}
                    "result" -> {
                        if (batch != null && requestIndexPerId != null && responseId != -1L) {
                            val responseIndex = requestIndexPerId[responseId]!!
                            result = batch.request.requests[responseIndex].resultDecoder.apply(p)
                        } else {
                            buffer = TokenBuffer(p)
                            buffer.copyCurrentStructure(p)
                        }
                    }
                    "error" -> error = jsonMapper.readValue(p, RpcError::class.java)
                    else -> throw Exception("Invalid response: $text")
                }
            }

            if (batch == null || requestIndexPerId == null || responseId == -1L) {
                throw Exception("Invalid response, no matching batch found for ID $responseId: $text")
            }

            val responseIndex = requestIndexPerId[responseId]!!

            // if we had to buffer, read the result from it now
            buffer?.use {
                result = batch.request.requests[responseIndex].resultDecoder.apply(it.asParserOnFirstToken())
            }

            val response = when {
                result == null && error == null -> HttpClient.ERROR_INVALID_RESPONSE
                result != null -> success(result)
                else -> failure(error!!)
            }

            batch.request.responses[responseIndex].complete(response)
        }

        batch?.future?.complete(true)
    }

    private fun getBatchFromRequestId(requestId: Long): Pair<CompletableBatchRequest, HashMap<Long, Int>>? {
        // Handle if the batch returned responses in the same order as they were sent
        val directMatch = inFlightBatchRequests.remove(requestId)
        if (directMatch != null) {
            return directMatch
        }

        // Find a batch by looking through all in-flight batches for one containing this ID
        for ((batchId, data) in inFlightBatchRequests) {
            if (data.second.containsKey(requestId)) {
                return inFlightBatchRequests.remove(batchId)
            }
        }

        return null
    }

    private fun handleResponse(id: Long, resultParser: JsonParser, error: RpcError?) {
        val request = inFlightRequests.remove(id)
        if (request != null) {
            handleRequestResponse(id, request, resultParser, error)
            return
        }

        val subscriptionRequest = inFlightSubscriptionRequests.remove(id)
        if (subscriptionRequest != null) {
            handleSubscriptionResponse(id, subscriptionRequest, resultParser, error)
            return
        }

        val resubscribed = requestIdToSubscription[id]
        if (resubscribed != null) {
            handleResubscriptionResponse(id, resubscribed, resultParser, error)
        }
    }

    private fun <T> handleRequestResponse(
        id: Long,
        request: CompletableRequest<T>,
        resultParser: JsonParser,
        error: RpcError?,
    ) {
        val result = if (error == null) request.resultDecoder.apply(resultParser) else null

        val response = when {
            result == null && error == null -> HttpClient.ERROR_INVALID_RESPONSE
            result != null -> success<T>(result)
            else -> failure(error!!)
        }

        LOG.trc { "Handled response for request $id: $response" }

        request.future.complete(response)
    }

    private fun <T : Any> handleSubscriptionResponse(
        id: Long,
        request: CompletableSubscriptionRequest<T>,
        resultParser: JsonParser,
        error: RpcError?,
    ) {
        if (error != null) {
            request.future.complete(failure(error))
        } else {
            val subscription = Subscription(
                serverId = resultParser.text,
                params = request.params,
                resultDecoder = request.resultDecoder,
                stream = QueueChannel.spscUnbounded {
                    // requestId is constant even across re-subscriptions
                    val sub = requestIdToSubscription.remove(id)
                    if (sub != null) {
                        LOG.trc { "Unsubscribing from stream: ${sub.serverId}" }

                        serverIdToSubscription.remove(sub.serverId)
                        request("eth_unsubscribe", arrayOf(sub.serverId), Boolean::class.java)
                    }
                },
            )

            requestIdToSubscription[id] = subscription
            serverIdToSubscription[subscription.serverId] = subscription

            request.future.complete(success(subscription.stream))
        }

        LOG.trc { "Handled response for subscription request $id" }
    }

    private fun <T : Any> handleResubscriptionResponse(
        id: Long,
        subscription: Subscription<T>,
        resultParser: JsonParser,
        error: RpcError?,
    ) {
        if (error != null) {
            // will cause re-subscription to be attempted again
            throw Exception("Error re-subscribing to stream: ${subscription.serverId}, error: $error")
        } else {
            // remove old serverId
            serverIdToSubscription.remove(subscription.serverId)

            // update serverId with new value
            subscription.serverId = resultParser.text
            serverIdToSubscription[subscription.serverId] = subscription
        }

        LOG.trc { "Handled response for re-subscription request $id" }
    }

    private fun handleNotification(paramsParser: JsonParser) {
        var subscriptionId: String? = null
        var resultBuff: TokenBuffer? = null
        paramsParser.forEachObjectField { field ->
            when (field) {
                "subscription" -> subscriptionId = paramsParser.text
                "result" -> {
                    // avoid buffering if possible
                    if (subscriptionId != null) {
                        val subscription = serverIdToSubscription[subscriptionId] ?: return
                        subscription.handleNotification(paramsParser)
                        return
                    }

                    resultBuff = TokenBuffer(paramsParser)
                    resultBuff.copyCurrentStructure(paramsParser)
                }

                else -> throw Exception("Invalid notification: $paramsParser")
            }
        }

        val subscription = serverIdToSubscription[subscriptionId] ?: return
        resultBuff!!.use { buff ->
            buff.asParser().use {
                it.nextToken()
                subscription.handleNotification(it)
            }
        }
    }

    /**
     * Signal to close WS connection.
     */
    override fun close() {
        LOG.inf { "Requesting to close WebSocket" }

        stopping = true

        // wake up the event loop thread so it can exit
        eventLock.withLock { newEventCondition.signalAll() }

        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean> {
        if (batch.isEmpty) {
            batch.markAsSent()
            return CompletableFuture.completedFuture(true)
        }

        val request = CompletableBatchRequest(batch, CompletableFuture())

        batchRequestQueue.add(request)
        eventLock.withLock { newEventCondition.signalAll() }
        return request.future
    }

    override fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<Result<T, RpcError>> {
        val request = CompletableRequest(
            method,
            params,
            resultDecoder,
            CompletableFuture(),
        )

        requestQueue.add(request)
        eventLock.withLock { newEventCondition.signalAll() }
        return request.future
    }

    override fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>> {
        val request = CompletableSubscriptionRequest(
            params,
            resultDecoder,
            CompletableFuture(),
        )

        subscriptionQueue.add(request)
        eventLock.withLock { newEventCondition.signalAll() }
        return request.future
    }

    private abstract class ExpiringRequest {
        private val initiated = TimeSource.Monotonic.markNow()

        fun expireIfTimedOut(duration: Duration): Boolean {
            if (initiated.elapsedNow() > duration) {
                expireRequest()
                return true
            }

            return false
        }

        abstract fun expireRequest()
    }

    private class CompletableRequest<T>(
        val method: String,
        val params: Array<*>,
        val resultDecoder: Function<JsonParser, T>,
        val future: CompletableFuture<Result<T, RpcError>>,
    ) : ExpiringRequest() {
        override fun expireRequest() {
            future.complete(HttpClient.ERROR_CALL_TIMEOUT)
        }
    }

    private data class CompletableBatchRequest(
        val request: BatchRpcRequest,
        val future: CompletableFuture<Boolean>,
    ) : ExpiringRequest() {
        override fun expireRequest() {
            for (i in request.responses.indices) {
                val response = request.responses[i]
                response.complete(HttpClient.ERROR_CALL_TIMEOUT)
            }

            future.complete(false)
        }
    }

    private class CompletableSubscriptionRequest<T : Any>(
        val params: Array<*>,
        val resultDecoder: Function<JsonParser, T>,
        val future: CompletableFuture<Result<ChannelReceiver<T>, RpcError>>,
    ) : ExpiringRequest() {
        override fun expireRequest() {
            future.complete(HttpClient.ERROR_CALL_TIMEOUT)
        }
    }

    private class Subscription<T : Any>(
        var serverId: String,
        val params: Array<*>,
        val resultDecoder: Function<JsonParser, T>,
        val stream: Channel<T>,
    ) {
        fun handleNotification(event: JsonParser) {
            stream.offer(resultDecoder.apply(event))
        }
    }
}
