package io.ethers.providers

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class MockServerTest : FunSpec({
    // A test that replaces the client it started with can have the old client's session open on the server after
    // the new client's has already spoken: on a slow iOS simulator, the resubscribe test then closed the stale
    // session, the client under test never reconnected, and the test timed out.
    test("a session opened after the client under test has spoken does not take its place") {
        val server = mockServerWebsocket()
        val ktor = HttpClient { install(WebSockets) }
        try {
            val current = ktor.webSocketSession(server.url)
            current.send(Frame.Text("hello"))
            server.takeReceivedText() shouldBe "hello"

            // a client that never speaks, whose session the server opens after the current one's
            val stale = ktor.webSocketSession(server.url)
            eventually(5.seconds) { server.openSessions shouldBe 2 }

            server.sendJson("""{"to":"current"}""")
            val frame = withTimeoutOrNull(5_000) { current.incoming.receive() }
            (frame as? Frame.Text)?.readText() shouldBe """{"to":"current"}"""

            server.closeConnection()
            val close = withTimeoutOrNull(5_000) { current.incoming.receiveCatching() }
            close shouldNotBe null
            (close!!.getOrNull() == null || close.getOrNull() is Frame.Close) shouldBe true

            stale.incoming.cancel()
        } finally {
            ktor.close()
            server.stop()
        }
    }
})
