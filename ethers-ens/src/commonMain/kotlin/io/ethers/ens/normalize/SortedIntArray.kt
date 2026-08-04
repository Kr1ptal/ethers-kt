package io.ethers.ens.normalize

/**
 * Membership test for an ascending-sorted [IntArray].
 *
 * The kotlin stdlib's `IntArray.binarySearch` is JVM-only - it delegates to `java.util.Arrays` - and has no common
 * equivalent, so this reimplements it. Both call sites only ever compared the result against `>= 0`, so this
 * returns a [Boolean] rather than an insertion point.
 *
 * The receiver **must** be sorted ascending; the result is undefined otherwise, exactly as with the stdlib version.
 */
internal fun IntArray.containsSorted(value: Int): Boolean {
    var low = 0
    var high = size - 1

    while (low <= high) {
        // `ushr` rather than `/ 2` so a large low + high cannot overflow into a negative index
        val mid = (low + high) ushr 1
        val midValue = this[mid]

        when {
            midValue < value -> low = mid + 1
            midValue > value -> high = mid - 1
            else -> return true
        }
    }

    return false
}
