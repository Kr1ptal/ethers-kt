package io.ethers.providers

import io.ethers.core.types.Address
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
 * Writing the text of a JSON-RPC request, as `HttpClient` and `WsClient` send it, with params built as `Provider`
 * builds them.
 *
 * - `send*`: the params are built and encoded, then the request is written around them: a request sent for the first
 *   time.
 * - `resend*`: the params were encoded before, so only the request is written around their kept encoding: a retry, the
 *   same call in another batch, or params a caching wrapper encoded for its key.
 * - `build*`: only building the params, without encoding them: `rpcParams` for `eth_getBalance`, `buildRpcParams` for
 *   `debug_traceCall`.
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
    private val call = CallRequest().apply {
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

    private val encodedBalance = balanceParams().also { it.encode() }
    private val encodedCall = callParams().also { it.encode() }
    private val encodedTraceCall = traceCallParams().also { it.encode() }

    @Benchmark
    open fun sendGetBalance(): String = buildJsonRpcRequest("eth_getBalance", 1L, balanceParams())

    @Benchmark
    open fun sendCall(): String = buildJsonRpcRequest("eth_call", 1L, callParams())

    @Benchmark
    open fun sendTraceCall(): String = buildJsonRpcRequest("debug_traceCall", 1L, traceCallParams())

    @Benchmark
    open fun sendBlockNumber(): String = buildJsonRpcRequest("eth_blockNumber", 1L, RpcParams.EMPTY)

    @Benchmark
    open fun resendGetBalance(): String = buildJsonRpcRequest("eth_getBalance", 1L, encodedBalance)

    @Benchmark
    open fun resendCall(): String = buildJsonRpcRequest("eth_call", 1L, encodedCall)

    @Benchmark
    open fun resendTraceCall(): String = buildJsonRpcRequest("debug_traceCall", 1L, encodedTraceCall)

    @Benchmark
    open fun buildGetBalance(): RpcParams = balanceParams()

    @Benchmark
    open fun buildTraceCall(): RpcParams = traceCallParams()
}
