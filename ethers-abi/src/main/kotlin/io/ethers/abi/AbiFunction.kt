package io.ethers.abi

import io.ethers.core.types.Bytes

data class AbiFunction(
    val name: String,
    val inputs: List<AbiType<*>>,
    val outputs: List<AbiType<*>>,
) {
    val selector = Bytes(AbiType.computeSignatureHash(name, inputs).copyOfRange(0, 4))

    fun encodeCall(args: Array<out Any>): Bytes {
        return Bytes(AbiCodec.encodeWithPrefix(selector, inputs, args))
    }

    fun decodeCall(data: Bytes): Array<Any> {
        return AbiCodec.decodeWithPrefix(selector.size, inputs, data.asByteArray())
    }

    fun encodeArgs(args: Array<out Any>): Bytes {
        return Bytes(AbiCodec.encode(inputs, args))
    }

    fun decodeArgs(data: Bytes): Array<Any> {
        return AbiCodec.decode(inputs, data.asByteArray())
    }

    fun encodeResponse(data: Array<out Any>): Bytes {
        return Bytes(AbiCodec.encode(outputs, data))
    }

    fun decodeResponse(data: Bytes): Array<Any> {
        return AbiCodec.decode(outputs, data.asByteArray())
    }

    companion object {
        private val SIGNATURE_REGEX = "(\\w+)\\((.*?)\\)\\s*?(\\((.*)\\))?".toRegex()

        fun parseSignature(signature: String): AbiFunction {
            val match = SIGNATURE_REGEX.matchEntire(
                signature.replace("function", "").replace("returns", "").trim(),
            ) ?: throw IllegalArgumentException("Invalid signature: $signature")

            val name = match.groupValues.getOrNull(1)
            if (name.isNullOrBlank()) throw IllegalArgumentException("Invalid signature, function has no name: $signature")

            val inputsRaw = match.groupValues.getOrNull(2)
            val outputsRaw = match.groupValues.getOrNull(4)
            val inputs = if (inputsRaw.isNullOrBlank()) emptyList() else AbiType.parseSignature(inputsRaw)
            val outputs = if (outputsRaw.isNullOrBlank()) emptyList() else AbiType.parseSignature(outputsRaw)

            return AbiFunction(name, inputs, outputs)
        }
    }
}
