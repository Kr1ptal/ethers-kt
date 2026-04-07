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

            val labelHash = Hashing.keccak256(labels[i].toByteArray(Charsets.UTF_8))

            labelHash.copyInto(buf, 32)
            Hashing.keccak256(buf).copyInto(buf, 0)
        }

        return buf.copyOf(32)
    }

    /**
     * Encode Dns name. Reference implementation
     * https://github.com/ethers-io/ethers.js/blob/fc1e006575d59792fa97b4efb9ea2f8cca1944cf/packages/hash/src.ts/namehash.ts#L49
     */
    fun dnsEncode(name: String): Bytes {
        val parts = name.split(".")
        val encoded = Array(parts.size) { i ->
            val normalized = EnsNormalize.normalize(parts[i])
            normalized.length to normalized.toByteArray(Charsets.UTF_8)
        }

        // +1 at the end for the trailing zero byte requirement, which is handled implicitly during
        // initialization of the result array
        val result = ByteArray(encoded.sumOf { 1 + it.second.size } + 1)

        var offset = 0
        for ((length, bytes) in encoded) {
            result[offset++] = length.toByte()
            bytes.copyInto(result, offset)
            offset += bytes.size
        }
        return Bytes(result)
    }
}
