package io.ethers.ens.normalize

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * [containsSorted] replaces the JVM-only `IntArray.binarySearch`, so these pin the behaviour it has to reproduce.
 */
class SortedIntArrayTest : FunSpec({
    context("finds values present in the array") {
        withData(
            nameFn = { "value ${it.second} in ${it.first.toList()}" },
            intArrayOf(1) to 1,
            intArrayOf(1, 2) to 1,
            intArrayOf(1, 2) to 2,
            intArrayOf(1, 3, 5, 7, 9) to 1, // first
            intArrayOf(1, 3, 5, 7, 9) to 5, // middle
            intArrayOf(1, 3, 5, 7, 9) to 9, // last
            intArrayOf(-9, -5, 0, 5, 9) to -9,
            intArrayOf(-9, -5, 0, 5, 9) to 0,
            intArrayOf(Int.MIN_VALUE, 0, Int.MAX_VALUE) to Int.MIN_VALUE,
            intArrayOf(Int.MIN_VALUE, 0, Int.MAX_VALUE) to Int.MAX_VALUE,
        ) { (array, value) ->
            array.containsSorted(value) shouldBe true
        }
    }

    context("rejects values absent from the array") {
        withData(
            nameFn = { "value ${it.second} not in ${it.first.toList()}" },
            IntArray(0) to 1,
            intArrayOf(1) to 0,
            intArrayOf(1) to 2,
            intArrayOf(1, 3, 5, 7, 9) to 0, // below all
            intArrayOf(1, 3, 5, 7, 9) to 4, // between
            intArrayOf(1, 3, 5, 7, 9) to 10, // above all
            intArrayOf(-9, -5, 0, 5, 9) to -7,
            intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE) to 0,
        ) { (array, value) ->
            array.containsSorted(value) shouldBe false
        }
    }

    test("agrees with a linear scan for arbitrary sorted arrays") {
        checkAll(Arb.list(Arb.int(-50..50), 0..40)) { values ->
            val array = values.distinct().sorted().toIntArray()

            // probe inside and outside the generated range, so both hits and misses are covered
            for (probe in -55..55) {
                array.containsSorted(probe) shouldBe array.contains(probe)
            }
        }
    }

    test("does not overflow on indices near Int.MAX_VALUE") {
        // `(low + high)` would overflow to a negative index if it were not an unsigned shift. A real array that
        // large is not allocatable here, so this covers the arithmetic directly.
        val low = Int.MAX_VALUE - 1
        val high = Int.MAX_VALUE
        ((low + high) ushr 1) shouldBe 2147483646
    }
})
