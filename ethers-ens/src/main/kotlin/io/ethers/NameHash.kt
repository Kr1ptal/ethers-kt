package io.ethers

import io.ethers.EnsResolver.Error.Normalisation
import io.ethers.crypto.Hashing
import io.ethers.providers.types.RpcResponse
import io.github.adraffy.ens.ENSNormalize
import io.github.adraffy.ens.InvalidLabelException
import java.nio.charset.StandardCharsets

object NameHash {
    /**
     * Normalise ENS name based on [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-15-normalization-standard)
     */
    private fun normalise(ensName: String): RpcResponse<String> {
        return try {
            RpcResponse.result(ENSNormalize.ENSIP15.normalize(ensName))
        } catch (e: InvalidLabelException) {
            RpcResponse.error(Normalisation(e))
        }
    }

    fun nameHash(ensName: String): RpcResponse<ByteArray> {
        val normalisedEnsName = normalise(ensName)
        if (normalisedEnsName.isError) {
            return normalisedEnsName.propagateError()
        }
        return RpcResponse.result(nameHash(normalisedEnsName.resultOrThrow().split(".")))
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
}
