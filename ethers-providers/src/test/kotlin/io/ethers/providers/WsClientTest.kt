package io.ethers.providers

import io.kotest.core.spec.style.FunSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * WsClient tests demonstrating the funSpec factory pattern for JsonRpcClient testing.
 *
 * This shows how the same factory pattern used for HttpClient can conceptually be applied
 * to WsClient, though WebSocket testing with MockWebServer has additional complexity.
 * The main demonstration is showing WebSocket-specific capabilities like subscriptions.
 */
class WsClientTest : FunSpec({
    @Suppress("MoveLambdaOutsideParentheses")
    val commonJsonRpcTests = JsonRpcClientTestFactory.commonTests(
        JsonRpcClientTestFactory.Variant.WS,
        { url ->
            val okhttp = OkHttpClient.Builder().readTimeout(50, TimeUnit.MILLISECONDS).build()
            WsClient(url, okhttp)
        },
    )
    include(commonJsonRpcTests)
})
