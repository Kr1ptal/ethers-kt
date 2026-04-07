package io.ethers.core.types

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger

class StateOverrideTest : FunSpec({
    val addr1 = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
    val addr2 = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9")
    val addr3 = Address("0x0000000000000000000000000000000000000001")

    fun newAccountOverride(nonce: Long, balance: String) = AccountOverride {
        nonce(nonce)
        balance(BigInteger(balance))
    }

    context("factory methods") {
        test("no-arg constructor creates empty instance") {
            val state = StateOverride()
            state.size shouldBe 0
            state.isEmpty() shouldBe true
        }

        test("wrap shares reference with original map") {
            val map = mutableMapOf(addr1 to newAccountOverride(1, "100"))
            val state = StateOverride.wrap(map)

            state.size shouldBe 1
            state[addr1] shouldBe map[addr1]

            // modifying original map affects state
            map[addr2] = newAccountOverride(2, "200")
            state.size shouldBe 2
        }

        test("copy creates deep copy of overrides") {
            val original = mutableMapOf(addr1 to newAccountOverride(1, "100"))
            val state = StateOverride.copy(original)

            state.size shouldBe 1
            state[addr1] shouldBe original[addr1]

            // modifying original does not affect copy
            original[addr2] = newAccountOverride(2, "200")
            state.size shouldBe 1
        }

        test("invoke with vararg pairs") {
            val override1 = newAccountOverride(1, "100")
            val override2 = newAccountOverride(2, "200")
            val state = StateOverride(addr1 to override1, addr2 to override2)

            state.size shouldBe 2
            state[addr1] shouldBe override1
            state[addr2] shouldBe override2
        }

        test("invoke with builder") {
            val state = StateOverride {
                put(addr1, newAccountOverride(1, "100"))
            }

            state.size shouldBe 1
            state[addr1]!!.nonce shouldBe 1L
        }
    }

    context("mergeChanges") {
        test("merges two instances without mutating originals") {
            val state1 = StateOverride(addr1 to newAccountOverride(1, "100"))
            val state2 = StateOverride(addr2 to newAccountOverride(2, "200"))

            val merged = state1.mergeChanges(state2)

            merged.size shouldBe 2
            merged[addr1]!!.nonce shouldBe 1L
            merged[addr2]!!.nonce shouldBe 2L

            // originals unchanged
            state1.size shouldBe 1
            state2.size shouldBe 1
        }

        test("merges overlapping addresses by applying changes") {
            val state1 = StateOverride(addr1 to newAccountOverride(1, "100"))
            val state2 = StateOverride(addr1 to newAccountOverride(5, "500"))

            val merged = state1.mergeChanges(state2)
            merged[addr1]!!.nonce shouldBe 5L
            merged[addr1]!!.balance shouldBe BigInteger("500")
        }

        test("with null other returns copy of this") {
            val state1 = StateOverride(addr1 to newAccountOverride(1, "100"))

            val merged = state1.mergeChanges(null)
            merged.size shouldBe 1
            merged[addr1]!!.nonce shouldBe 1L

            // not the same reference
            (merged !== state1) shouldBe true
        }
    }

    context("applyChanges") {
        test("mutates this instance in place") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            val other = StateOverride(addr2 to newAccountOverride(2, "200"))

            state.applyChanges(other)

            state.size shouldBe 2
            state[addr1]!!.nonce shouldBe 1L
            state[addr2]!!.nonce shouldBe 2L
        }

        test("merges existing address in place") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            val other = StateOverride(addr1 to newAccountOverride(5, "500"))

            state.applyChanges(other)

            state.size shouldBe 1
            state[addr1]!!.nonce shouldBe 5L
        }

        test("with null does nothing") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            state.applyChanges(null)
            state.size shouldBe 1
        }
    }

    context("applyChange") {
        test("adds new address") {
            val state = StateOverride()
            state.applyChange(addr1, newAccountOverride(1, "100"))
            state.size shouldBe 1
            state[addr1]!!.nonce shouldBe 1L
        }

        test("merges with existing address") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            state.applyChange(addr1, newAccountOverride(5, "500"))
            state[addr1]!!.nonce shouldBe 5L
        }

        test("with null override does nothing") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            state.applyChange(addr1, null)
            state[addr1]!!.nonce shouldBe 1L
        }
    }

    context("takeChanges") {
        test("takes references from other") {
            val override2 = newAccountOverride(2, "200")
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            val other = StateOverride(addr2 to override2)

            state.takeChanges(other)

            state.size shouldBe 2
            // reference is kept for new address
            (state[addr2] === override2) shouldBe true
        }

        test("merges with existing address") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            val other = StateOverride(addr1 to newAccountOverride(5, "500"))

            state.takeChanges(other)
            state[addr1]!!.nonce shouldBe 5L
        }

        test("with null does nothing") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            state.takeChanges(null)
            state.size shouldBe 1
        }
    }

    context("takeChange") {
        test("takes reference for new address") {
            val override1 = newAccountOverride(1, "100")
            val state = StateOverride()
            state.takeChange(addr1, override1)
            (state[addr1] === override1) shouldBe true
        }

        test("merges with existing address") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            state.takeChange(addr1, newAccountOverride(5, "500"))
            state[addr1]!!.nonce shouldBe 5L
        }

        test("with null override does nothing") {
            val state = StateOverride(addr1 to newAccountOverride(1, "100"))
            state.takeChange(addr1, null)
            state[addr1]!!.nonce shouldBe 1L
        }
    }

    context("nullable extension mergeChanges") {
        test("both null returns null") {
            val result: StateOverride? = (null as StateOverride?).mergeChanges(null)
            result.shouldBeNull()
        }

        test("this null, other non-null returns copy of other") {
            val other = StateOverride(addr1 to newAccountOverride(1, "100"))
            val result = (null as StateOverride?).mergeChanges(other)
            result!!.size shouldBe 1
            result[addr1]!!.nonce shouldBe 1L
        }

        test("this non-null, other null returns copy of this") {
            val state: StateOverride? = StateOverride(addr1 to newAccountOverride(1, "100"))
            val result = state.mergeChanges(null)
            result!!.size shouldBe 1
            result[addr1]!!.nonce shouldBe 1L
            (result !== state) shouldBe true
        }

        test("both non-null merges") {
            val state: StateOverride? = StateOverride(addr1 to newAccountOverride(1, "100"))
            val other: StateOverride? = StateOverride(addr2 to newAccountOverride(2, "200"))
            val result = state.mergeChanges(other)
            result!!.size shouldBe 2
        }
    }

    context("equals and hashCode") {
        test("equal instances") {
            val a = StateOverride(addr1 to newAccountOverride(1, "100"))
            val b = StateOverride(addr1 to newAccountOverride(1, "100"))
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("different instances") {
            val a = StateOverride(addr1 to newAccountOverride(1, "100"))
            val b = StateOverride(addr1 to newAccountOverride(2, "200"))
            a shouldNotBe b
        }

        test("same instance") {
            val a = StateOverride(addr1 to newAccountOverride(1, "100"))
            a shouldBe a
        }

        test("not equal to null or different type") {
            val a = StateOverride(addr1 to newAccountOverride(1, "100"))
            a.equals(null) shouldBe false
            a.equals("string") shouldBe false
        }
    }
})
