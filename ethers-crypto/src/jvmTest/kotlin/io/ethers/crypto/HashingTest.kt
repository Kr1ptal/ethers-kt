package io.ethers.crypto

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class HashingTest : FunSpec({
    val testCases = listOf(
        MessageTestCase("ethers-kt", "d3755e706a641e047aebdf7b84d6eec96141717ac80545eddab8f8abf6acde1d", "b675cde95ab615422f83691c786a30727240e8abfe8e0640bc479304195f517b"),
        MessageTestCase("Optimized WEB3 library for seamless work with blockchain!", "275eecc016211d65b3d39993791acd814b1316ca03774e9a2b80d0a0225e3c3b", "dda097804dc48248e51fbe83652b12f2cae0828f5e8e11e6e4f4b5ecb951484b"),
        MessageTestCase("", "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470", "5f35dce98ba4fba25530a026ed80b2cecdaa31091ba4958b99b52ea1d068adad"),
        MessageTestCase("abc", "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45", "e28f5ff58ff3f1b24d6ba6e3b3e95e49589e8dd59b91296e76189d6ad2857b22"),
        MessageTestCase("baz bazonk foo", "44d0abfd52dc3b5d7057799ffbedbbeefdbe346dd1859faa6ff3acc58e6bece5", "389d8d4c9e8b67afca9f0437b037d3dbfd1c923e162ec27c6d64a9fb5882c2c6"),
        MessageTestCase("mockFunction(address,uint)", "30066a7ee0ffd05192d9476a5c80b93d0100d5836920c98256a6d163e2c79944", "7c28684d76b8924336c74d0be33df53610bfe64267782b48614d84593d0850d8"),
    )

    test("keccak256") {
        testCases.forAll { (message, keccak256Hash) ->
            Hashing.keccak256(message.toByteArray()).toHexString() shouldBe keccak256Hash
        }
    }

    test("hashMessage") {
        testCases.forAll { (message, _, hash) ->
            Hashing.hashMessage(message.toByteArray()).toHexString() shouldBe hash
        }
    }
}) {
    data class MessageTestCase(
        val message: String,
        val keccak256Hash: String,
        val hash: String,
    )
}
