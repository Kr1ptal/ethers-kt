package io.ethers.ens

import io.ethers.core.FastHex
import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import io.github.adraffy.ens.ENSNormalize
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object NameHash {
    fun nameHash(ensName: String): ByteArray {
        return nameHash(ENSNormalize.ENSIP15.normalize(ensName).split("."))
    }

    private fun nameHash(labels: List<String>): ByteArray {
        if (labels.isEmpty() || labels[0] == "") {
            return ByteArray(32) { 0 }
        } else {
            val tail: List<String> = if (labels.size == 1) {
                listOf()
            } else {
                labels.slice(1 until labels.size)
            }

            val remainderHash = nameHash(tail)
            val result = remainderHash.copyOf(64)

            val labelHash = Hashing.keccak256(labels[0].toByteArray(StandardCharsets.UTF_8))
            System.arraycopy(labelHash, 0, result, 32, labelHash.size)

            return Hashing.keccak256(result)
        }
    }

    /**
     * Encode Dns name. Reference implementation
     * https://github.com/ethers-io/ethers.js/blob/fc1e006575d59792fa97b4efb9ea2f8cca1944cf/packages/hash/src.ts/namehash.ts#L49
     */
    fun dnsEncode(name: String): Bytes {
        val parts = name.split(".")
        val outputStream = ByteArrayOutputStream()
        for (part in parts) {
            val bytes: ByteArray = NameHash.toUtf8Bytes("_" + ENSNormalize.ENSIP15.normalize(part)) ?: break
            bytes[0] = (bytes.size - 1).toByte()
            outputStream.write(bytes)
        }

        return Bytes(FastHex.encodeWithPrefix(outputStream.toByteArray()) + "00")
    }

    private fun toUtf8Bytes(string: String?): ByteArray? {
        return if (string.isNullOrEmpty()) {
            null
        } else string.toByteArray(StandardCharsets.UTF_8)
    }
}
