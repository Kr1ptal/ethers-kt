package io.ethers.providers

import io.ethers.core.Kotlinx
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The response id a tree gives: what WsClient routes on when it parses a message into a tree. */
private fun treeResponseId(text: String): Long {
    val obj = try {
        Kotlinx.DEFAULT.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return WsMessageScanner.NONE
    if ("method" in obj) return WsMessageScanner.NONE
    return (obj["id"] as? JsonPrimitive)?.longOrNull ?: WsMessageScanner.NONE
}

class WsMessageScannerTest : FunSpec({
    test("reads the id a tree reads") {
        val messages = listOf(
            """{"jsonrpc":"2.0","id":1,"result":"0x1"}""",
            """{"result":"0x1","id":42,"jsonrpc":"2.0"}""",
            """ { "jsonrpc" : "2.0" ,
               "id" : 7 , "result" : null } """,
            """{"id":0,"result":1}""",
            """{"id":-5,"result":1}""",
            """{"id":9223372036854775807,"result":1}""",
            """{"id":-9223372036854775808,"result":1}""",
            """{"id":9223372036854775808,"result":1}""",
            """{"id":123456789012345678,"result":1}""",
            """{"id":1234567890123456789,"result":1}""",
            """{"id":"12","result":1}""",
            """{"id":"abc","result":1}""",
            """{"id":null,"result":1}""",
            """{"id":true,"result":1}""",
            """{"id":1.0,"result":1}""",
            """{"id":1.5,"result":1}""",
            """{"id":1e2,"result":1}""",
            """{"id":007,"result":1}""",
            """{"id":"-3","result":1}""",
            """{"result":1}""",
            """{}""",
            """{"result":{"id":5,"method":"m","s":"}{\"]["},"id":3}""",
            """{"result":["id",{"id":5}],"id":3,"error":{"code":1,"message":"}"}}""",
            """{"result":"a\\\"b","id":3}""",
            """{"method":null,"id":3,"result":1}""",
            """{"jsonrpc":"2.0","method":"accountNotification","params":{"result":1,"subscription":23784}}""",
            """[{"id":1,"result":1}]""",
            // a result before the id
            """{"jsonrpc":"2.0","result":{"id":5,"method":"m","list":[1,"]}"]},"id":42}""",
            """{"result":1 , "id" : 42 }${"\n"}""",
            """{"result":1,"id":"7"}""",
            """{"result":1,"id":-9223372036854775808}""",
            """{"result":1,"id":1.5}""",
            """{"result":1,"id":null}""",
            "{\"result\":1,\"id\":\"1\\u0032\"}",
            """{"result":1,"id":3,"jsonrpc":"2.0"}""",
            """{"result":1,"method":"m","id":3,"x":1}""",
            """{"result":{"id":5},"x":{"id":6}}""",
            """{"result":1,"x":{"id":6}}""",
            """{"result":1,"error":{"code":1,"message":"m"},"id":3}""",
            """"id"""",
            """null""",
            """42""",
        )

        for (message in messages) {
            withClue(message) { WsMessageScanner.responseId(message) shouldBe treeResponseId(message) }
        }
    }

    // declining only costs the tree route; routing a message the tree would not route as this response would be wrong
    test("declines messages it cannot read rather than misreading them") {
        val messages = listOf(
            // keys with escapes, which might spell id or method
            "{\"\\u0069d\":1,\"result\":1}",
            "{\"\\u006dethod\":\"m\",\"id\":1,\"params\":{},\"result\":1}",
            // malformed before the id
            """{"id":,"result":1}""",
            """{id:1,"result":1}""",
            """{"result":[1,{"a":"]"},"id":1""",
            """{"result":"1,"id":1}""",
            "",
            "not json",
            "   ",
        )

        for (message in messages) {
            withClue(message) { WsMessageScanner.responseId(message) shouldBe WsMessageScanner.NONE }
        }
    }

    // WsClient decodes the whole message next, and routes it through the tree unless it has no method and the same id
    test("stops at the first id, leaving what follows to the decode") {
        val messages = listOf(
            """{"id":1,"id":2,"result":1}""",
            """{"id":1,"method":"eth_subscription","params":{}}""",
            "{\"id\":1,\"\\u006dethod\":\"m\",\"params\":{},\"result\":1}",
            """{"id":1,"result":1""",
            """{"id":1,"result":"1}""",
            """{"id":1 "result":1}""",
        )

        for (message in messages) {
            withClue(message) { WsMessageScanner.responseId(message) shouldBe 1L }
        }
    }

    test("finds a notification's method and subscription where a tree finds them") {
        val messages = listOf(
            """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xabc","result":{"a":1}}}""",
            """{"jsonrpc":"2.0","method":"accountNotification","params":{"result":{"a":"x"},"subscription":23784}}""",
            """{"params":{"result":[1,"]",{"x":"}"}],"subscription":-5},"method":"m"}""",
            """ { "method" : "m" , "params" : { "subscription" : "s" , "result" : 1 } } """,
            """{"method":"m","params":{"s":"a\\","t":"\"}{","subscription":"s","result":1}}""",
            """{"method":"m","params":{"subscription":1e2,"result":1}}""",
            """{"method":"m","x":{"method":"other","params":{"subscription":"inner"}},"params":{"subscription":"s"}}""",
            """{"method":"m","params":{"result":{"data":["${"A".repeat(5000)}","base64"]},"subscription" : "s" } } """,
            """{"method":"m","params":{"result":1,"subscription":"a\\b"}}""",
        )

        for (message in messages) {
            withClue(message) {
                val tree = Kotlinx.DEFAULT.parseToJsonElement(message).jsonObject
                val position = WsMessageScanner.NotificationPosition()
                WsMessageScanner.notification(message, position) shouldBe true
                position.isMethod(message, tree.getValue("method").jsonPrimitive.content) shouldBe true
                position.isMethod(message, "other") shouldBe false
                position.subscriptionId(message) shouldBe tree.getValue("params").jsonObject.getValue("subscription")
            }
        }
    }

    // declining only costs the tree route
    test("declines notifications it cannot read") {
        val messages = listOf(
            """{"method":"m","params":{"result":1}}""",
            """{"method":"m"}""",
            """{"params":{"subscription":"s"}}""",
            """{"method":1,"params":{"subscription":"s"}}""",
            """{"method":null,"params":{"subscription":"s"}}""",
            """{"method":"m","params":["s"]}""",
            """{"method":"m","params":{"result":1""",
            """{"params":{"subscription":"s"},"method":"m""",
            "{\"method\":\"\\u006d\",\"params\":{\"subscription\":\"s\"}}",
            "{\"\\u006dethod\":\"m\",\"params\":{\"subscription\":\"s\"}}",
            "{\"method\":\"m\",\"params\":{\"\\u0073ubscription\":\"s\"}}",
            """[{"method":"m","params":{"subscription":"s"}}]""",
            "",
            "not json",
        )

        for (message in messages) {
            withClue(message) { WsMessageScanner.notification(message, WsMessageScanner.NotificationPosition()) shouldBe false }
        }
    }

    // WsClient keeps one position, which caches the last id it read
    test("a cached subscription id is reused only for the same id") {
        val position = WsMessageScanner.NotificationPosition()
        val subscriptions = listOf("123", "12", "1234", "1234", "\"12\"", "12", "\"0xab\"", "\"0xabc\"", "\"0xab\"", "-12", "12")

        for (subscription in subscriptions) {
            for (message in listOf(
                """{"method":"m","params":{"subscription":$subscription,"result":1}}""",
                """{"method":"m","params":{"result":1,"subscription":$subscription}}""",
            )) {
                withClue(message) {
                    WsMessageScanner.notification(message, position) shouldBe true
                    position.subscriptionId(message) shouldBe Kotlinx.DEFAULT.parseToJsonElement(subscription)
                }
            }
        }
    }

    // read backwards, the end of a message is taken on trust: the decode reads the whole message and rejects it unless
    // its last params.subscription is this one
    test("a trailing subscription is read without checking what comes before it") {
        val messages = listOf(
            """{"method":"m","params":{"subscription":"s","result":1},"x":{"subscription":"t"}}""",
            """{"method":"m","params":{"result":"1,"subscription":"t"}}""",
        )

        for (message in messages) {
            withClue(message) {
                val position = WsMessageScanner.NotificationPosition()
                WsMessageScanner.notification(message, position) shouldBe true
                position.subscriptionId(message) shouldBe JsonPrimitive("t")
            }
        }
    }
})
