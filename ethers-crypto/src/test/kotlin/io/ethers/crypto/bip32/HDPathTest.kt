package io.ethers.crypto.bip32

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class HDPathTest : FunSpec({
    context("parse") {
        listOf(
            "m/44'/60'/0'/0/0",
            "m/44'/60'/0'/0/1",
            "m/44'/60'/0'/0/2'",
            "m/44'/9231'/0'/0/2'",
            "m/44'/9231'/0'/0/2'/21421",
        ).forAll { path ->
            test("parse $path") {
                HDPath.parse(path).toString() shouldBe path
            }
        }
    }

    context("extend") {
        data class Idx(val index: Int, val hardened: Boolean)
        val base = HDPath.parse("m/44'/60'/0'/0")

        listOf(
            listOf(Idx(12, false)) to "$base/12",
            listOf(Idx(12, false), Idx(1442, true)) to "$base/12/1442'",
            listOf(Idx(12, true), Idx(1442, true)) to "$base/12'/1442'",
        ).forAll { (indices, expected) ->
            test("extend with ${indices.joinToString()}") {
                var path = base
                indices.forEach { (index, hardened) -> path = path.extend(index, hardened) }

                path.toString() shouldBe expected
            }
        }
    }
})
