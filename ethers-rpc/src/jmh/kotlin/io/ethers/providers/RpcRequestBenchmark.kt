package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.AddressSerializer
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TracerConfigSerializer
import io.ethers.providers.types.RpcParams
import io.ethers.providers.types.buildRpcParams
import io.ethers.providers.types.rpcParams
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Writing a JSON-RPC request up to the UTF-8 bytes handed to Ktor, as `HttpClient` and `WsClient` send it, with params
 * built as `Provider` builds them.
 *
 * - `send*`: the params are built and encoded, then the request is written around them: a request sent for the first
 *   time.
 * - `resend*`: the params were encoded before, so only the request is written around their kept encoding: a retry, the
 *   same call in another batch, or params a caching wrapper encoded for its key.
 * - `build*`: only building the params, without encoding them: `rpcParams` for `eth_getBalance`, `buildRpcParams` for
 *   `debug_traceCall`.
 * - `address*`: encoding an `Address` as a JSON string, the first time (a new `Address`, whose hex isn't cached yet) and
 *   again (the same `Address`, whose hex is cached).
 */
@Fork(value = 2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class RpcRequestBenchmark {
    private val address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
    private val block = "latest"
    private val call = CallRequest {
        from = Address("0x00000000000000000000000000000000000000aa")
        to = address
        data = Bytes("0x70a082310000000000000000000000000d4a11d5eeaac28ec3f61d100daf4d40471f1852")
    }
    private val tracerConfig = TracerConfig(CallTracer(onlyTopCall = true, withLog = false))

    private fun balanceParams(): RpcParams = rpcParams(address, block)

    private fun callParams(): RpcParams = rpcParams(call, block)

    private fun traceCallParams(): RpcParams = buildRpcParams {
        add(call)
        add(block)
        add(tracerConfig, TracerConfigSerializer)
    }

    /** The bytes of a request, as handed to Ktor. */
    private fun request(method: String, params: RpcParams): ByteArray = buildJsonRpcRequest(method, 1L, params)

    // sent once, so their encoding is kept as a client keeps it
    private val encodedBalance = balanceParams().also { request("eth_getBalance", it) }
    private val encodedCall = callParams().also { request("eth_call", it) }
    private val encodedTraceCall = traceCallParams().also { request("debug_traceCall", it) }

    private val addressBytes = address.toByteArray()

    @Benchmark
    open fun sendGetBalance(): ByteArray = request("eth_getBalance", balanceParams())

    @Benchmark
    open fun sendCall(): ByteArray = request("eth_call", callParams())

    @Benchmark
    open fun sendTraceCall(): ByteArray = request("debug_traceCall", traceCallParams())

    @Benchmark
    open fun sendBlockNumber(): ByteArray = request("eth_blockNumber", RpcParams.EMPTY)

    @Benchmark
    open fun resendGetBalance(): ByteArray = request("eth_getBalance", encodedBalance)

    @Benchmark
    open fun resendCall(): ByteArray = request("eth_call", encodedCall)

    @Benchmark
    open fun resendTraceCall(): ByteArray = request("debug_traceCall", encodedTraceCall)

    @Benchmark
    open fun buildGetBalance(): RpcParams = balanceParams()

    @Benchmark
    open fun buildTraceCall(): RpcParams = traceCallParams()

    // a new Address around the same bytes (not copied), so its hex is encoded on each call
    @Benchmark
    open fun addressFirstEncode(): String = Kotlinx.DEFAULT.encodeToString(AddressSerializer, Address(addressBytes))

    @Benchmark
    open fun addressCachedEncode(): String = Kotlinx.DEFAULT.encodeToString(AddressSerializer, address)

    // what the two above share: creating the Address alone
    @Benchmark
    open fun addressCreate(): Address = Address(addressBytes)
}
