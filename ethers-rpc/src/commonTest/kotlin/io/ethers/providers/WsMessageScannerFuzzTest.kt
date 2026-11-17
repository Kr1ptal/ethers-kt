package io.ethers.providers

import io.ethers.core.Kotlinx
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random

/** A generated JSON value, rendered with random whitespace so the same value is written many ways. */
private sealed interface Node {
    fun render(ws: () -> String): String

    /** A number, literal or string, written exactly as [text]. */
    class Raw(val text: String) : Node {
        override fun render(ws: () -> String) = text

        /** Whether [WsMessageScanner] reads this value backwards when it is written last: a token, or a string without escapes. */
        val readableBackwards: Boolean
            get() = if (text.startsWith('"')) '\\' !in text else text.all { it.isLetterOrDigit() || it in "-+." }
    }

    class Arr(val items: List<Node>) : Node {
        override fun render(ws: () -> String) = items.joinToString(",", "[${ws()}", "${ws()}]") { ws() + it.render(ws) + ws() }
    }

    class Obj(val entries: List<Pair<String, Node>>) : Node {
        override fun render(ws: () -> String) = entries.joinToString(",", "{${ws()}", "${ws()}}") { (key, value) ->
            "${ws()}\"$key\"${ws()}:${ws()}${value.render(ws)}${ws()}"
        }

        fun value(key: String) = entries.firstOrNull { it.first == key }?.second
    }
}

/**
 * Generates WebSocket messages: responses, notifications and anything in between, with the keys the scanner looks for
 * at the top level, inside `params`, and nested where it must not look. Keys are never repeated within an object and
 * top-level keys have no escapes; the scanner's handling of those is tested on its own.
 */
private class MessageGen(val random: Random) {
    fun ws(): String = if (random.nextInt(3) == 0) WHITESPACE[random.nextInt(WHITESPACE.size)] else ""

    fun message(): Node.Obj {
        val entries = ArrayList<Pair<String, Node>>()
        if (random.nextBoolean()) entries += "jsonrpc" to Node.Raw("\"2.0\"")
        // mostly shaped as a response or as a notification, sometimes as a mix of both
        val shape = random.nextInt(3)
        if (if (shape == 1) random.nextInt(6) == 0 else random.nextInt(4) != 0) entries += "id" to id()
        if (if (shape == 1) random.nextInt(8) != 0 else random.nextInt(3) == 0) entries += "method" to method()
        if (if (shape == 1) random.nextInt(8) != 0 else random.nextInt(3) == 0) entries += "params" to params()
        if (random.nextBoolean()) entries += "result" to value(0)
        if (random.nextInt(4) == 0) entries += "error" to Node.Obj(listOf("code" to Node.Raw("-32000"), "message" to string()))
        if (random.nextInt(3) == 0) entries += "extra" to value(0)
        entries.shuffle(random)
        return Node.Obj(entries)
    }

    private fun id(): Node = when (random.nextInt(8)) {
        0, 1 -> Node.Raw(random.nextLong(0, 1_000_000).toString())
        2 -> Node.Raw(random.nextLong().toString())
        3 -> Node.Raw(NUMBERS[random.nextInt(NUMBERS.size)])
        4 -> Node.Raw("\"${random.nextInt(1000)}\"")
        else -> value(1)
    }

    private fun method(): Node = when (random.nextInt(10)) {
        0, 1, 2, 3 -> Node.Raw("\"eth_subscription\"")
        4, 5 -> Node.Raw("\"accountNotification\"")
        6 -> Node.Raw("\"\\u0061ccountNotification\"")
        7 -> Node.Raw("1")
        8 -> Node.Raw("null")
        else -> string()
    }

    private fun params(): Node {
        if (random.nextInt(6) == 0) return value(1)
        val entries = ArrayList<Pair<String, Node>>()
        if (random.nextInt(5) != 0) entries += "subscription" to subscription()
        if (random.nextBoolean()) entries += "result" to value(1)
        if (random.nextBoolean()) entries += KEYS[random.nextInt(KEYS.size)] to value(1)
        entries.shuffle(random)
        return Node.Obj(entries.distinctBy { it.first })
    }

    private fun subscription(): Node = when (random.nextInt(6)) {
        0 -> Node.Raw("\"0x${random.nextLong().toULong().toString(16)}\"")
        1 -> Node.Raw(random.nextInt(0, 100_000).toString())
        2 -> Node.Raw(NUMBERS[random.nextInt(NUMBERS.size)])
        3 -> string()
        4 -> Node.Raw("\"0x9cef478923ff08bf67fde6c64013158d\"")
        else -> value(2)
    }

    private fun value(depth: Int): Node = when (random.nextInt(if (depth >= 3) 3 else 5)) {
        0 -> Node.Raw(NUMBERS[random.nextInt(NUMBERS.size)])
        1 -> string()
        2 -> Node.Raw(LITERALS[random.nextInt(LITERALS.size)])
        3 -> Node.Arr(List(random.nextInt(4)) { value(depth + 1) })
        else -> Node.Obj(
            List(random.nextInt(4)) { KEYS[random.nextInt(KEYS.size)] to value(depth + 1) }.distinctBy { it.first },
        )
    }

    /** A string that may hold escapes and the characters the scanner matches on. */
    private fun string(): Node.Raw {
        val sb = StringBuilder("\"")
        repeat(random.nextInt(12)) { sb.append(STRING_PARTS[random.nextInt(STRING_PARTS.size)]) }
        return Node.Raw(sb.append('"').toString())
    }

    companion object {
        val WHITESPACE = listOf(" ", "  ", "\n", "\r\n", "\t", " \t ")
        val KEYS = listOf("id", "method", "params", "subscription", "result", "error", "a", "data", "_id", "_subscription", "methods")
        val LITERALS = listOf("true", "false", "null")
        val NUMBERS = listOf(
            "0", "-0", "7", "-5", "1.5", "1.0", "1e2", "1E+2", "-2.5e-3", "007",
            "9223372036854775807", "-9223372036854775808", "9223372036854775808", "123456789012345678901",
        )
        val STRING_PARTS = listOf(
            "a", "Z", "0", " ", "id", "method", "subscription", "{", "}", "[", "]", ",", ":",
            "\\\"", "\\\\", "\\/", "\\n", "\\u0041", "\\\\\\\"", "é", "🙂",
        )
    }
}

/**
 * The id [WsMessageScanner.responseId] gives, as a tree reads it: one written last, read backwards and taken on trust,
 * if a `result` comes before any `id` or `method`; otherwise the first top-level `id`, unless `method` comes first.
 */
private fun expectedResponseId(message: Node.Obj): Long {
    val first = message.entries.firstOrNull { it.first == "id" || it.first == "method" || it.first == "result" }
    val last = message.entries.lastOrNull()
    val value = if (first?.first == "result" && last?.first == "id" && (last.second as? Node.Raw)?.readableBackwards == true) {
        last.second
    } else {
        val (key, value) = message.entries.firstOrNull { it.first == "id" || it.first == "method" } ?: return WsMessageScanner.NONE
        if (key == "method") return WsMessageScanner.NONE
        value
    }
    val id = Kotlinx.DEFAULT.parseToJsonElement(value.render(EXACT))
    return (id as? JsonPrimitive)?.longOrNull ?: WsMessageScanner.NONE
}

/**
 * The subscription [WsMessageScanner.notification] finds, as a tree reads it: one written last in the last top-level
 * object, read backwards and taken on trust, otherwise `params.subscription`. Null if it finds none.
 */
private fun expectedSubscription(message: Node.Obj): Node? {
    val last = message.entries.lastOrNull()?.second
    if (last is Node.Obj) {
        val (key, value) = last.entries.lastOrNull() ?: (null to null)
        if (key == "subscription" && value is Node.Raw && value.readableBackwards) return value
    }
    return (message.value("params") as? Node.Obj)?.value("subscription")
}

/** Renders a value without whitespace. */
private val EXACT = { "" }

class WsMessageScannerFuzzTest : FunSpec({
    test("generated messages are read as a tree reads them") {
        // one position for every message, as WsClient keeps one, so its cached subscription id is exercised too
        val position = WsMessageScanner.NotificationPosition()
        var responses = 0
        var notifications = 0
        for (seed in 0 until 4000) {
            val gen = MessageGen(Random(seed))
            val message = gen.message()
            val text = message.render(gen::ws)
            withClue("seed $seed: $text") {
                val tree = Kotlinx.DEFAULT.parseToJsonElement(text).jsonObject

                val id = WsMessageScanner.responseId(text)
                id shouldBe expectedResponseId(message)
                if (id != WsMessageScanner.NONE) responses++

                val method = tree["method"]
                val methodReadable = method is JsonPrimitive && method.isString && message.value("method")!!.render(EXACT).none { it == '\\' }
                val subscription = expectedSubscription(message)
                val found = WsMessageScanner.notification(text, position)
                found shouldBe (methodReadable && subscription != null)
                if (found) {
                    notifications++
                    position.isMethod(text, (method as JsonPrimitive).content) shouldBe true
                    position.isMethod(text, method.content + "x") shouldBe false
                    position.subscriptionId(text) shouldBe (Kotlinx.DEFAULT.parseToJsonElement(subscription!!.render(EXACT)) as? JsonPrimitive)
                }
            }
        }

        // the generator reaches both kinds of message often enough to matter
        withClue("$responses responses, $notifications notifications") {
            (responses > 1000) shouldBe true
            (notifications > 500) shouldBe true
        }
    }

    // WsClient routes a message the scanner cannot read through the tree, but one that makes it throw is dropped
    test("corrupted messages never make the scanner throw") {
        val position = WsMessageScanner.NotificationPosition()
        val corruption = "{}[]\":,\\ 0a-.e\n"
        for (seed in 0 until 2000) {
            val gen = MessageGen(Random(seed))
            val text = gen.message().render(gen::ws)
            val random = Random(seed)
            repeat(8) { round ->
                val at = random.nextInt(text.length + 1)
                val c = corruption[random.nextInt(corruption.length)]
                val corrupted = when (round % 4) {
                    0 -> text.substring(0, at)
                    1 -> text.substring(at)
                    2 -> if (at < text.length) text.substring(0, at) + c + text.substring(at + 1) else text + c
                    else -> text.substring(0, at) + c + text.substring(at)
                }
                withClue("seed $seed: $corrupted") {
                    WsMessageScanner.responseId(corrupted)
                    if (WsMessageScanner.notification(corrupted, position)) {
                        position.subscriptionId(corrupted)
                        position.isMethod(corrupted, "eth_subscription")
                    }
                }
            }
        }
    }
})
