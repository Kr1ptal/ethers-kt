package io.ethers.core.types

import io.ethers.core.Kotlinx
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.FourByteTracer
import io.ethers.core.types.tracers.JSTracer
import io.ethers.core.types.tracers.MuxTracer
import io.ethers.core.types.tracers.NoopTracer
import io.ethers.core.types.tracers.PrestateTracer
import io.ethers.core.types.tracers.StructTracer
import io.ethers.core.types.tracers.Tracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TracerConfigSerializer
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/**
 * The serializers that write their fields directly write exactly the text their earlier versions wrote by building a
 * JSON tree, kept in [LegacyCallRequestSerializer] and the others next to it, for inputs with every combination of
 * fields set and unset, and edge values: empty lists and maps, zero and max numbers.
 */
class StreamingSerializersTest : FunSpec({
    test("CallRequest") {
        val fields = callRequestFields()
        val random = Random(1)

        val requests = ArrayList<CallRequest>()
        requests.add(CallRequest {})
        requests.add(CallRequest { fields.forEach { it(this, random) } })
        for (field in fields) requests.add(CallRequest { field(this, random) })
        repeat(500) {
            requests.add(CallRequest { fields.forEach { if (random.nextBoolean()) it(this, random) } })
        }

        for (request in requests) {
            encodesAsLegacy(CallRequestSerializer, LegacyCallRequestSerializer, request)
        }
    }

    test("BlockOverride") {
        val fields = blockOverrideFields()
        val random = Random(2)

        val overrides = ArrayList<BlockOverride>()
        overrides.add(BlockOverride {})
        overrides.add(BlockOverride { fields.forEach { it(this, random) } })
        for (field in fields) overrides.add(BlockOverride { field(this, random) })
        repeat(300) {
            overrides.add(BlockOverride { fields.forEach { if (random.nextBoolean()) it(this, random) } })
        }

        for (override in overrides) {
            encodesAsLegacy(BlockOverrideSerializer, LegacyBlockOverrideSerializer, override)
        }
    }

    test("AccountOverride") {
        val fields = accountOverrideFields()
        val random = Random(3)

        val overrides = ArrayList<AccountOverride>()
        overrides.add(AccountOverride())
        overrides.add(AccountOverride().apply { fields.forEach { it(this, random) } })
        for (field in fields) overrides.add(AccountOverride().apply { field(this, random) })
        repeat(300) {
            overrides.add(AccountOverride().apply { fields.forEach { if (random.nextBoolean()) it(this, random) } })
        }

        for (override in overrides) {
            encodesAsLegacy(AccountOverrideSerializer, LegacyAccountOverrideSerializer, override)
        }
    }

    test("StateOverride") {
        val random = Random(4)

        val overrides = ArrayList<StateOverride>()
        overrides.add(StateOverride())
        overrides.add(StateOverride(Address.ZERO to AccountOverride()))
        overrides.add(StateOverride.wrap(linkedMapOf(maxAddress() to AccountOverride(), Address.ZERO to AccountOverride())))
        repeat(100) { overrides.add(randomStateOverride(random, 1 + random.nextInt(5))) }

        for (override in overrides) {
            encodesAsLegacy(StateOverrideSerializer, LegacyStateOverrideSerializer, override)
        }
    }

    test("TracerConfig") {
        val random = Random(5)
        val stateOverrides = listOf(null, StateOverride(), randomStateOverride(random, 1), randomStateOverride(random, 4))
        val blockOverrides = listOf(null, BlockOverride {}, BlockOverride { blockOverrideFields().forEach { it(this, random) } })

        for (tracer in tracers()) {
            for (timeoutMs in longArrayOf(-1L, 0L, 1L, Long.MAX_VALUE)) {
                for (reexec in longArrayOf(-1L, 0L, Long.MAX_VALUE)) {
                    for (state in stateOverrides) {
                        for (block in blockOverrides) {
                            for (txIndex in intArrayOf(-1, 0, Int.MAX_VALUE)) {
                                val config = TracerConfig(tracer, timeoutMs, reexec, state, block, txIndex)
                                encodesAsLegacy(TracerConfigSerializer, LegacyTracerConfigSerializer, config)
                            }
                        }
                    }
                }
            }
        }
    }

    test("StructTracer config fields never have the name of a field TracerConfig writes after them") {
        // the streaming serializer writes each key once, which is only what a JSON object of them all would hold if
        // no config field shares its name with one of these
        val tracer = StructTracer(true, true, true, true, true, 1, buildJsonObject { put("timeout", "1ms") })
        val tail = setOf("timeout", "reexec", "stateOverrides", "blockOverrides", "txIndex")
        tracer.config.keys.filter { it in tail }.shouldBeEmpty()
    }

    test("LogFilter") {
        val random = Random(6)
        val blocks = listOf<(LogFilter.Builder) -> Unit>(
            {},
            { it.atBlock(randomHash(random)) },
            { it.atBlock(Long.MAX_VALUE) },
            { it.blockRange(0L, Long.MAX_VALUE) },
            { it.blockRange(BlockId.EARLIEST, BlockId.LATEST) },
        )
        val addresses = listOf<(LogFilter.Builder) -> Unit>(
            {},
            { it.address(emptyList()) },
            { it.address(maxAddress()) },
            { it.address(Address.ZERO, randomAddress(random), maxAddress()) },
        )
        // per topic: unset, one hash, several hashes, no hashes
        val topicChoices = 4

        for (block in blocks) {
            for (address in addresses) {
                for (topicsCase in 0 until topicChoices * topicChoices * topicChoices * topicChoices) {
                    val filter = LogFilter.Builder()
                    block(filter)
                    address(filter)
                    var choices = topicsCase
                    for (topic in 0 until 4) {
                        val hashes = when (choices % topicChoices) {
                            0 -> null
                            1 -> listOf(randomHash(random))
                            2 -> listOf(randomHash(random), Hash.ZERO, randomHash(random))
                            else -> emptyList()
                        }
                        choices /= topicChoices
                        if (hashes != null) {
                            when (topic) {
                                0 -> filter.topic0(hashes)
                                1 -> filter.topic1(hashes)
                                2 -> filter.topic2(hashes)
                                else -> filter.topic3(hashes)
                            }
                        }
                    }
                    encodesAsLegacy(LogFilterSerializer, LegacyLogFilterSerializer, filter.build())
                }
            }
        }
    }
})

/**
 * [value] encodes with [serializer] to exactly the text [legacy] writes, alone and as an element of a list (as params
 * are written), and to the same JSON tree.
 */
private fun <T> encodesAsLegacy(serializer: KSerializer<T>, legacy: KSerializer<T>, value: T) {
    val expected = Kotlinx.DEFAULT.encodeToString(legacy, value)
    Kotlinx.DEFAULT.encodeToString(serializer, value) shouldBe expected
    Kotlinx.DEFAULT.encodeToString(ListSerializer(serializer), listOf(value, value)) shouldBe "[$expected,$expected]"
    Kotlinx.DEFAULT.encodeToJsonElement(serializer, value) shouldBe Kotlinx.DEFAULT.encodeToJsonElement(legacy, value)
}

private val MAX_UINT256 = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935")

private fun maxAddress() = Address(ByteArray(20) { -1 })

private fun randomAddress(random: Random) = Address(random.nextBytes(20))

private fun randomHash(random: Random) = Hash(random.nextBytes(32))

private fun randomLong(random: Random): Long = when (random.nextInt(4)) {
    0 -> 0L
    1 -> Long.MAX_VALUE
    else -> random.nextLong(0, Long.MAX_VALUE)
}

private fun randomBigInteger(random: Random): BigInteger = when (random.nextInt(4)) {
    0 -> bigIntegerOf(0)
    1 -> MAX_UINT256
    else -> BigInteger(random.nextLong(0, Long.MAX_VALUE).toString())
}

private fun randomBytes(random: Random): Bytes = Bytes(random.nextBytes(random.nextInt(3) * 17))

private fun randomStorage(random: Random): Map<Hash, Hash> {
    val size = when (random.nextInt(3)) {
        0 -> 0
        1 -> 1
        else -> 4
    }
    val storage = LinkedHashMap<Hash, Hash>()
    repeat(size) { storage[randomHash(random)] = if (random.nextBoolean()) Hash.ZERO else randomHash(random) }
    return storage
}

private fun randomStateOverride(random: Random, size: Int): StateOverride {
    val fields = accountOverrideFields()
    val state = StateOverride()
    repeat(size) {
        state[randomAddress(random)] = AccountOverride().apply { fields.forEach { if (random.nextBoolean()) it(this, random) } }
    }
    return state
}

private fun callRequestFields(): List<CallRequest.Builder.(Random) -> Unit> = listOf(
    { from(if (it.nextBoolean()) Address.ZERO else randomAddress(it)) },
    { to(if (it.nextBoolean()) maxAddress() else randomAddress(it)) },
    { gas(randomLong(it)) },
    { gasPrice(randomBigInteger(it)) },
    { gasFeeCap(randomBigInteger(it)) },
    { gasTipCap(randomBigInteger(it)) },
    { value(randomBigInteger(it)) },
    { nonce(randomLong(it)) },
    { data(randomBytes(it)) },
    { random ->
        accessList(
            List(random.nextInt(4)) {
                AccessList.Item(randomAddress(random), List(random.nextInt(3)) { randomHash(random) })
            },
        )
    },
    { chainId(randomLong(it)) },
    { blobFeeCap(randomBigInteger(it)) },
    { random -> blobVersionedHashes(List(random.nextInt(4)) { randomHash(random) }) },
)

private fun blockOverrideFields(): List<BlockOverride.Builder.(Random) -> Unit> = listOf(
    { number(randomLong(it)) },
    { difficulty(randomBigInteger(it)) },
    { time(randomLong(it)) },
    { gasLimit(randomLong(it)) },
    { coinbase(randomAddress(it)) },
    { random(randomHash(it)) },
    { baseFee(randomBigInteger(it)) },
)

private fun accountOverrideFields(): List<AccountOverride.(Random) -> Unit> = listOf(
    { nonce(randomLong(it)) },
    { code(randomBytes(it)) },
    { balance(randomBigInteger(it)) },
    // only one of the two can be set
    { if (it.nextBoolean()) state(randomStorage(it)) else stateDiff(randomStorage(it)) },
)

@OptIn(ExperimentalSerializationApi::class)
private object CustomTracer : Tracer<JsonObject> {
    override val name: String = "custom\"Tracer\n"
    override val resultSerializer: KSerializer<JsonObject> = JsonObject.serializer()
    override val config: JsonObject = buildJsonObject {
        put("enabled", true)
        put("count", Long.MAX_VALUE)
        put("ratio", 0.5)
        put("big", JsonUnquotedLiteral(MAX_UINT256.toString()))
        put("none", JsonNull)
        put("quoted", "a \"b\"\t\u0001 é")
        putJsonArray("steps") {
            add(1)
            add(JsonPrimitive("two"))
        }
        putJsonObject("nested") {
            putJsonObject("empty") {}
            putJsonArray("empty list") {}
        }
    }
}

private fun tracers() = listOf(
    CallTracer(onlyTopCall = true, withLog = false),
    CallTracer(onlyTopCall = false, withLog = true),
    PrestateTracer(diffMode = true),
    NoopTracer,
    FourByteTracer,
    MuxTracer(CallTracer(onlyTopCall = true, withLog = true), PrestateTracer(diffMode = false)),
    JSTracer("{ result: function() { return \"ok\\n\"; } }", buildJsonObject { put("depth", 2) }),
    CustomTracer,
    StructTracer(),
    StructTracer(
        enableMemory = true,
        disableStack = true,
        disableStorage = true,
        enableReturnData = true,
        debug = true,
        limit = Int.MAX_VALUE,
        overrides = buildJsonObject { put("timeout", "5ms") },
    ),
)
