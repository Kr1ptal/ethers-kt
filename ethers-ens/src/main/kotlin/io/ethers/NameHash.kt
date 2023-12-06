package io.ethers

import io.ethers.crypto.Hashing
import io.github.adraffy.ens.ENSNormalize
import io.github.adraffy.ens.InvalidLabelException
import java.nio.charset.StandardCharsets

object NameHash {
    private fun normalise(ensName: String): String {
        return try {
            ENSNormalize.ENSIP15.normalize(ensName)
        } catch (e: InvalidLabelException) {
            throw Exception("Invalid ENS name provided: $ensName")
        }
    }

    fun nameHash(ensName: String): ByteArray {
        val normalisedEnsName: String = normalise(ensName)
        return nameHash(normalisedEnsName.split("."))
    }

    private fun nameHash(labels: List<String>) : ByteArray {
        if (labels.isEmpty() || labels[0] == "") {
            return ByteArray(32) {0}
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
}
