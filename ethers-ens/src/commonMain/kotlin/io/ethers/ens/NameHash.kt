package io.ethers.ens

import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import io.ethers.ens.normalize.EnsNormalize

object NameHash {
    fun nameHash(ensName: String): ByteArray {
        val labels = EnsNormalize.normalize(ensName).split(".")

        val buf = ByteArray(64)
        for (i in labels.lastIndex downTo 0) {
            if (labels[i].isEmpty()) continue

            val labelHash = Hashing.keccak256(labels[i].encodeToByteArray())

            labelHash.copyInto(buf, 32)
            Hashing.keccak256(buf).copyInto(buf, 0)
        }

        return buf.copyOf(32)
    }

    /**
     * Maximum length of a single DNS label, in bytes. The DNS wire format reserves the two high bits of the length
     * byte to mark compression pointers, so a label length can never exceed 0b0011_1111.
     * */
    private const val MAX_LABEL_LENGTH_BYTES = 63

    /**
     * Encode Dns name. Reference implementation
     * https://github.com/ethers-io/ethers.js/blob/fc1e006575d59792fa97b4efb9ea2f8cca1944cf/packages/hash/src.ts/namehash.ts#L49
     *
     * @throws IllegalArgumentException if any label exceeds [MAX_LABEL_LENGTH_BYTES] once UTF-8 encoded.
     */
    fun dnsEncode(name: String): Bytes {
        val parts = name.split(".")
        val encoded = Array(parts.size) { i ->
            val bytes = EnsNormalize.normalize(parts[i]).encodeToByteArray()

            // checked in bytes, not characters - a label can be short enough as a string while still being too long
            // once encoded (e.g. 32x "é" is 32 chars but 64 bytes)
            require(bytes.size <= MAX_LABEL_LENGTH_BYTES) {
                "DNS label exceeds $MAX_LABEL_LENGTH_BYTES bytes: ${bytes.size}"
            }

            bytes
        }

        // +1 at the end for the trailing zero byte requirement, which is handled implicitly during
        // initialization of the result array
        val result = ByteArray(encoded.sumOf { 1 + it.size } + 1)

        var offset = 0
        for (bytes in encoded) {
            // NOTE: the length prefix is the label's UTF-8 byte length, not its UTF-16 length. These differ for any
            // non-ASCII label (e.g. "💎" is 2 UTF-16 code units but 4 UTF-8 bytes).
            result[offset++] = bytes.size.toByte()
            bytes.copyInto(result, offset)
            offset += bytes.size
        }
        return Bytes(result)
    }
}
