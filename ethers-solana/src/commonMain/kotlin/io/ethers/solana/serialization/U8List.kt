package io.ethers.solana.serialization

/**
 * Read-only list of unsigned bytes, as compiled instructions and lookup tables index accounts. Keeps
 * one byte per element instead of a boxed integer, and hands out shared boxes on access.
 *
 * Takes ownership of [bytes] rather than copying, so do not mutate the array afterwards.
 */
internal class U8List(val bytes: ByteArray) : AbstractList<Int>(), RandomAccess {
    override val size: Int get() = bytes.size

    override fun get(index: Int): Int = BOXES[bytes[index].toInt() and 0xff]

    private companion object {
        // boxed once, since Integer.valueOf only caches up to 127 and indices run to 255
        val BOXES: Array<Int> = Array(256) { it }
    }
}
