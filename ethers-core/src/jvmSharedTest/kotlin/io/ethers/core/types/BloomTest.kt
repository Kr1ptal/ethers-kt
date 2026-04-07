package io.ethers.core.types

import io.ethers.core.FastHex
import io.ethers.core.Jackson
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

// logs bloom filter from block 17743820 (0xbbf3e28cbb304dcdf75df088a7ba11ff1a55f94ff41f056165a7ecd486f92554) on eth mainnet
private val ETH_BLOOM = Bloom(
    "0x42288a0b4225490888104341a05000c771200000104122032409008a00af0700004004084580a400c0047a307630150622135585a803f13000121110002128482002cb28e044195c0810c4184c0200a9c6cc50c000f23e4026251800c5340640c2004a8483840e8842c040000118881c12026401984b8e00325014db336940028b90c8810410c0c0004900cb0f2a982d900b8215810406188d012c44009a0905033c08900508247088540086608c94c010802e9a41060b0e1400746017051723a00321d280ca20402ac9f440003a242035405020812042190017d11fc9c4a0680454a084c1a0c4e412950500000008100b2e3084480c945c401e1211400ba050",
)

class BloomTest : FunSpec({
    context("contains address") {
        withData(
            "0x74911b2b9192c0da4171c7a7bb48b02af9365797" to true,
            "0x1111111254eeb25477b68fb85ed929f73a960582" to true,
            "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2" to true,
            "0x4a926d2d8ed99838fe3ae2941ba4460c09b5493c" to true,
            "0x0000000000000038fe3ae2941ba4460c09b5493c" to false,
            "0xdeadeadeadeadeadeadeadeadeadeadeadeadead" to false,
            "0x0000000000000000000000000000000000000000" to false,
        ) { (address, result) ->
            ETH_BLOOM.contains(Address(address)) shouldBe result
        }
    }

    context("contains topic") {
        withData(
            "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925" to true,
            "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822" to true,
            "0x1c411e9a96e071241c2f21f7726b17ae89e3cab4c78be50e062b03a9fffbbad1" to true,
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef" to true,
            "0x0000000000000000000000000000000000000000000000000000000000000000" to true,
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3e3" to false,
            "0x000000000000000b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef" to false,
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a0000000000000000000" to false,
            "0xdeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadead" to false,
        ) { (address, result) ->
            ETH_BLOOM.contains(Hash(address)) shouldBe result
        }
    }

    context("add elements to empty bloom filter") {
        context("add Hash-es") {
            val included = listOf(
                Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
            )
            val excluded = listOf(
                Hash("0xdeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadead"),
                Hash("0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"),
            )
            val bloom = Bloom()
            included.forEach { bloom.add(it) }

            withData(
                included.map { it to true } + excluded.map { it to false },
            ) { (value, result) ->
                bloom.contains(value) shouldBe result
            }
        }

        context("add Address-es") {
            val included = listOf(
                Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"),
                Address("0x3b64216ad1a58f61538b4fa1b27327675ab7ed67"),
                Address("0x1264f83b093abbf840ea80a361988d19c7f5a686"),
            )
            val excluded = listOf(
                Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
                Address("0xb0bababe78a9be0810fadf99dd2ed31ed12568be"),
            )
            val bloom = Bloom()
            included.forEach { bloom.add(it) }

            withData(
                included.map { it to true } + excluded.map { it to false },
            ) { (value, result) ->
                bloom.contains(value) shouldBe result
            }
        }

        context("add ByteArray-s") {
            val included = listOf(
                "dog",
                "cat",
                "hello",
                "world",
                "hodl",
                "0x1111111254eeb25477b68fb85ed929f73a960582",
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
            )
            val excluded = listOf(
                "0xdeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadeadead",
                "apple",
                "pear",
                "near",
                "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925",
                "0x0000000000000038fe3ae2941ba4460c09b5493c",
            )
            val bloom = Bloom()
            included.forEach { bloom.add(it.toByteArray()) }

            withData(
                included.map { it to true } + excluded.map { it to false },
            ) { (value, result) ->
                bloom.contains(value.toByteArray()) shouldBe result
            }
        }
    }

    test("serialization / deserialization") {
        val jsonString = Jackson.MAPPER.writeValueAsString(ETH_BLOOM)
        val deserializedObject = Jackson.MAPPER.readValue(jsonString, Bloom::class.java)

        deserializedObject shouldBe ETH_BLOOM
    }

    context("Bloom.Companion.fromHex") {
        test("valid hex returns Success") {
            val hex = FastHex.encodeWithPrefix(ByteArray(256))
            val result = Bloom.fromHex(hex)
            result.isSuccess() shouldBe true
            result.unwrap().asByteArray() shouldBe ByteArray(256)
        }

        test("invalid hex chars returns Failure") {
            val result = Bloom.fromHex("zzinvalid")
            result.isFailure() shouldBe true
        }

        test("valid hex with wrong size returns Failure") {
            val result = Bloom.fromHex("0xaabb")
            result.isFailure() shouldBe true
        }
    }

    context("Bloom.Companion.fromHexUnsafe") {
        test("valid hex creates Bloom") {
            val hex = FastHex.encodeWithPrefix(ByteArray(256) { 0x01 })
            val bloom = Bloom.fromHexUnsafe(hex)
            bloom.asByteArray().all { it == 0x01.toByte() } shouldBe true
        }
    }

    context("asByteArray and toByteArray") {
        test("asByteArray returns same reference") {
            val bytes = ByteArray(256) { 0x42 }
            val bloom = Bloom(bytes)
            (bloom.asByteArray() === bytes) shouldBe true
        }

        test("toByteArray returns copy") {
            val bytes = ByteArray(256) { 0x42 }
            val bloom = Bloom(bytes)
            val copy = bloom.toByteArray()
            copy shouldBe bytes
            (copy !== bytes) shouldBe true
        }
    }

    context("constructor validation") {
        test("throws on wrong-size ByteArray") {
            shouldThrow<IllegalArgumentException> { Bloom(ByteArray(10)) }
        }

        test("no-arg constructor creates 256-byte empty bloom") {
            val bloom = Bloom()
            bloom.asByteArray().size shouldBe 256
            bloom.asByteArray().all { it == 0.toByte() } shouldBe true
        }
    }

    context("equals and hashCode") {
        test("equal Blooms") {
            val a = Bloom(ByteArray(256) { 0x01 })
            val b = Bloom(ByteArray(256) { 0x01 })
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("different Blooms") {
            val a = Bloom(ByteArray(256) { 0x01 })
            val b = Bloom(ByteArray(256) { 0x02 })
            (a == b) shouldBe false
        }

        test("same instance") {
            ETH_BLOOM shouldBe ETH_BLOOM
        }

        test("not equal to null or different type") {
            ETH_BLOOM.equals(null) shouldBe false
            ETH_BLOOM.equals("string") shouldBe false
        }
    }
})
