package io.ethers.ens

import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import io.github.adraffy.ens.ENSNormalize
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val EMPTY_BYTE_ARRAY = ByteArray(32) { 0 }

object NameHash {
    fun nameHash(ensName: String): ByteArray {
        return nameHash(ENSNormalize.ENSIP15.normalize(ensName).split("."), 0)
    }

    private fun nameHash(labels: List<String>, index: Int): ByteArray {
        return if (index >= labels.size || labels[index] == "") {
            EMPTY_BYTE_ARRAY
        } else {
            // get nameHash result without first label and expand it to 64 bytes
            val remainderHash = nameHash(labels, index + 1)

            val result = ByteArray(64)
            System.arraycopy(remainderHash, 0, result, 0, remainderHash.size)

            // hash the current label and append it to result
            val labelHash = Hashing.keccak256(labels[index].toByteArray(StandardCharsets.UTF_8))
            System.arraycopy(labelHash, 0, result, 32, labelHash.size)

            Hashing.keccak256(result)
        }
    }

    /**
     * Encode Dns name. Reference implementation
     * https://github.com/ethers-io/ethers.js/blob/fc1e006575d59792fa97b4efb9ea2f8cca1944cf/packages/hash/src.ts/namehash.ts#L49
     */
    fun dnsEncode(name: String): Bytes {
        val parts = name.split(".")
        val stream = ByteArrayOutputStream()
        for (part in parts) {
            val normalized = ENSNormalize.ENSIP15.normalize(part)
            stream.write(normalized.length)
            stream.write(normalized.toByteArray(StandardCharsets.UTF_8))
        }
        stream.write(0)

        return Bytes(stream.toByteArray())
    }
}
