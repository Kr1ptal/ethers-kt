package io.ethers.aa.signature

import io.ethers.aa.account.FactoryArgs
import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.abi.eip712.EIP712TypedData
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An ERC-6492 signature: a signature of a not yet deployed account, wrapped with the factory call that deploys it,
 * so a verifier can deploy the account (in a simulation) before checking the signature with ERC-1271.
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-6492">ERC-6492</a>
 */
data class Erc6492Signature(val factory: Address, val factoryData: Bytes, val signature: Bytes) {
    /**
     * Serialize as `abi.encode(factory, factoryData, signature) || magicBytes`.
     */
    fun serialize(): Bytes {
        val encoded = AbiCodec.encode(WRAPPER_TYPES, listOf(factory, factoryData, signature))
        return Bytes(encoded + MAGIC_BYTES)
    }

    companion object {
        /** The 32-byte suffix marking an ERC-6492 signature. */
        private val MAGIC_BYTES = ByteArray(32) { if (it % 2 == 0) 0x64 else 0x92.toByte() }
        private val WRAPPER_TYPES = listOf(AbiType.Address, AbiType.Bytes, AbiType.Bytes)

        /**
         * Return whether [signature] is ERC-6492 wrapped, i.e. ends with the magic bytes.
         */
        @JvmStatic
        fun isWrapped(signature: Bytes): Boolean {
            val raw = signature.asByteArray()
            if (raw.size < MAGIC_BYTES.size) return false
            val offset = raw.size - MAGIC_BYTES.size
            for (i in MAGIC_BYTES.indices) if (raw[offset + i] != MAGIC_BYTES[i]) return false
            return true
        }

        /**
         * Unwrap an ERC-6492 [signature], or return null if it is not wrapped or malformed.
         */
        @JvmStatic
        fun parse(signature: Bytes): Erc6492Signature? {
            if (!isWrapped(signature)) return null
            val raw = signature.asByteArray()
            return try {
                val values = AbiCodec.decode(WRAPPER_TYPES, raw.copyOfRange(0, raw.size - MAGIC_BYTES.size))
                Erc6492Signature(values[0] as Address, values[1] as Bytes, values[2] as Bytes)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Wrap [signature] of an account deployed by [factoryArgs], unless it is already wrapped.
         */
        @JvmStatic
        fun wrap(signature: Bytes, factoryArgs: FactoryArgs): Bytes {
            if (isWrapped(signature)) return signature
            return Erc6492Signature(factoryArgs.factory, factoryArgs.factoryData, signature).serialize()
        }
    }
}

/**
 * Verifies signatures of any kind of account: EOAs, deployed ERC-1271 smart accounts, and not yet deployed accounts
 * whose signatures are ERC-6492 wrapped.
 *
 * A 65-byte signature that recovers to a signer without code is accepted locally. Everything else, including
 * signatures of EIP-7702 delegated EOAs whose delegate decides what is valid, is checked by simulating the ERC-6492
 * universal validator with a deployless `eth_call`, which deploys a counterfactual account if needed and calls its
 * `isValidSignature`.
 */
class SignatureVerifier(private val provider: Middleware) {
    /**
     * Verify [signature] of [hash] by [signer] at block [blockId].
     *
     * @param factoryArgs deployment of [signer] if it is a smart account that may not be deployed yet. The signature
     * is wrapped with it unless it already is an ERC-6492 signature.
     */
    @JvmOverloads
    fun verifyHash(
        signer: Address,
        hash: Hash,
        signature: Bytes,
        factoryArgs: FactoryArgs? = null,
        blockId: BlockId = BlockId.LATEST,
    ): RpcRequest<Boolean, RpcError> {
        // no signature is valid for the zero address
        if (signer == Address.ZERO) return SuppliedRpcRequest { success(false) }

        val wrapped = if (factoryArgs != null) Erc6492Signature.wrap(signature, factoryArgs) else signature
        val args = AbiCodec.encode(listOf(AbiType.Address, AbiType.FixedBytes(32), AbiType.Bytes), listOf(signer, Bytes(hash.asByteArray()), wrapped))
        val call = CallRequest().data(Bytes(ERC6492_VALIDATOR_BYTECODE.asByteArray() + args))
        val onChain = provider.call(call, blockId)
            .orElse { error -> if (error.isRevert()) success(Bytes.EMPTY) else failure(error) }
            .map { result -> result.size > 0 && result.asByteArray().last().toInt() == 1 }

        if (!recoversTo(signer, hash, signature)) return onChain

        // an EOA is valid locally, unless it has code: an EIP-7702 delegate decides for itself
        return provider.getCode(signer, blockId).andThen { code ->
            if (code.isEmpty) success(true) else onChain.send()
        }
    }

    /**
     * Verify [signature] of the EIP-191 [message] by [signer], see [verifyHash].
     */
    @JvmOverloads
    fun verifyMessage(
        signer: Address,
        message: ByteArray,
        signature: Bytes,
        factoryArgs: FactoryArgs? = null,
        blockId: BlockId = BlockId.LATEST,
    ): RpcRequest<Boolean, RpcError> {
        return verifyHash(signer, Hash(Hashing.hashMessage(message)), signature, factoryArgs, blockId)
    }

    /**
     * Verify [signature] of the EIP-712 [typedData] by [signer], see [verifyHash].
     */
    @JvmOverloads
    fun verifyTypedData(
        signer: Address,
        typedData: EIP712TypedData,
        signature: Bytes,
        factoryArgs: FactoryArgs? = null,
        blockId: BlockId = BlockId.LATEST,
    ): RpcRequest<Boolean, RpcError> {
        return verifyHash(signer, Hash(typedData.signatureHash()), signature, factoryArgs, blockId)
    }

    private fun recoversTo(signer: Address, hash: Hash, signature: Bytes): Boolean {
        if (signature.size != 65) return false
        val sig = Signature.fromByteArray(signature.asByteArray())
        if (sig !is Result.Success) return false
        return try {
            sig.value.recoverFromHash(hash.asByteArray()) == signer
        } catch (e: Exception) {
            false
        }
    }

    private fun RpcError.isRevert(): Boolean {
        return code == 3 || message.contains("revert", ignoreCase = true)
    }
}
