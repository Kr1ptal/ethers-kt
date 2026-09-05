package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.types.RPCTransaction
import io.ethers.solana.types.transaction.SolanaTxType
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class RPCTransactionTest : FunSpec({
    test("legacy, v0 and unsupported versions preserve raw payloads and metadata") {
        for ((version, type) in listOf(
            "\"legacy\"" to SolanaTxType.Legacy,
            "0" to SolanaTxType.V0,
            "1" to SolanaTxType.Unsupported(1),
            "127" to SolanaTxType.Unsupported(127),
            "255" to SolanaTxType.Unsupported(255),
        )) {
            // No binary decoder or signature verifier should run, even for a recognized version.
            val json = Kotlinx.DEFAULT.parseToJsonElement(
                """{"slot":18446744073709551615,"blockTime":-1,"version":$version,
                    "transaction":{"signatures":["uninterpreted"],"message":{"futureLayout":[1,{"new":true}]}},
                    "meta":{"err":{"FutureError":[7]},"futureCounter":18446744073709551615},
                    "futureField":{"nested":null}}""",
            ).jsonObject
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.type shouldBe type
            tx.slot shouldBe BigInteger("18446744073709551615")
            tx.blockTime shouldBe -1L
            tx.transaction shouldBe json.getValue("transaction")
            tx.meta shouldBe json.getValue("meta")
            tx.otherFields shouldBe mapOf("futureField" to json.getValue("futureField"))
            tx.raw shouldBe json
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        }
    }

    test("omitted, null and unrecognized versions are not silently classified as legacy") {
        for (version in listOf("", ",\"version\":null", ",\"version\":\"future\"", ",\"version\":{\"future\":true}", ",\"version\":4294967296")) {
            val json = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"transaction":{},"meta":null$version}""")
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.type shouldBe null
            tx.meta shouldBe null
            tx.blockTime shouldBe null
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        }
    }

    test("encoded and future transaction payload representations remain readable") {
        for (payload in listOf("[\"uninterpreted\",\"base64\"]", "\"opaque\"", "{\"newFormat\":true}")) {
            val json = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"blockTime":null,"transaction":$payload,"version":1}""")
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.type shouldBe SolanaTxType.Unsupported(1)
            tx.raw["blockTime"] shouldBe JsonNull
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        }
    }

    test("malformed slot quantities still fail instead of being treated as unsupported versions") {
        for (slot in listOf("-1", "18446744073709551616", "\"1\"")) {
            shouldThrow<IllegalArgumentException> {
                Kotlinx.DEFAULT.decodeFromString<RPCTransaction>("""{"slot":$slot,"transaction":{},"version":1}""")
            }
        }
    }

    test("type support is independent of RPC readability") {
        SolanaTxType.Legacy.version shouldBe null
        SolanaTxType.Legacy.isSupported shouldBe true
        SolanaTxType.fromVersion(0) shouldBe SolanaTxType.V0
        SolanaTxType.V0.isSupported shouldBe true
        SolanaTxType.fromVersion(1) shouldBe SolanaTxType.Unsupported(1)
        SolanaTxType.Unsupported(1).version shouldBe 1
        SolanaTxType.Unsupported(1).isSupported shouldBe false
        shouldThrow<IllegalArgumentException> { SolanaTxType.fromVersion(-1) }
        shouldThrow<IllegalArgumentException> { SolanaTxType.Unsupported(0) }
    }
})
