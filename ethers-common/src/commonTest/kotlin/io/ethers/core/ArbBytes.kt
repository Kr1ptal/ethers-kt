package io.ethers.core

import io.kotest.property.Arb
import io.kotest.property.Sample
import io.kotest.property.arbitrary.arbitrary

/**
 * Random byte arrays whose size falls in [size], drawn with a single `nextBytes` call.
 *
 * kotest's `Arb.byteArray(Arb.int(size), Arb.byte())` draws every byte through its own generator, which is where
 * most of a test's time goes on Kotlin/Native: 1000 samples of up to 512 bytes take ~50x as long as this does.
 *
 * Edge cases are an empty array, and the largest size filled with 0x00 and with 0xFF. Each is a fresh array, so a
 * test may change the one it is given.
 */
fun Arb.Companion.bytes(size: IntRange): Arb<ByteArray> = arbitrary(
    edgecaseFn = { rs ->
        when (rs.random.nextInt(3)) {
            0 -> Sample(ByteArray(size.first))
            1 -> Sample(ByteArray(size.last))
            else -> Sample(ByteArray(size.last) { -1 })
        }
    },
) { rs ->
    rs.random.nextBytes(rs.random.nextInt(size.first, size.last + 1))
}
