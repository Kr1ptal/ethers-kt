package io.ethers.abi

import io.ethers.core.types.Bytes

data class AbiFunction(
    val name: String,
    val inputs: List<AbiType<*>>,
    val outputs: List<AbiType<*>>,
) {
    val selector = Bytes(AbiType.computeSignatureHash(name, inputs).copyOfRange(0, 4))

    fun encodeCall(args: List<Any>): Bytes {
        return Bytes(AbiCodec.encodeWithPrefix(selector, inputs, args))
    }

    fun decodeCall(data: Bytes): List<Any> {
        return AbiCodec.decodeWithPrefix(selector.size, inputs, data.asByteArray())
    }

    fun encodeArgs(args: List<Any>): Bytes {
        return Bytes(AbiCodec.encode(inputs, args))
    }

    fun decodeArgs(data: Bytes): List<Any> {
        return AbiCodec.decode(inputs, data.asByteArray())
    }

    fun encodeResponse(data: List<Any>): Bytes {
        return Bytes(AbiCodec.encode(outputs, data))
    }

    fun decodeResponse(data: Bytes): List<Any> {
        return AbiCodec.decode(outputs, data.asByteArray())
    }

    companion object {
        private enum class ParseState {
            NAME,
            INPUTS,
            OUTPUTS,
        }

        /**
         * Parse function signature, returning [AbiFunction] instance, or throwing an exception if signature is invalid.
         * Only raw tuple types are supported, which must be wrapped in parentheses, e.g. `(uint256,address)`.
         *
         * Example signature:
         * ```
         * function balanceOf(address owner) public view returns (uint256 balance)
         * ```
         * */
        fun parseSignature(signature: String): AbiFunction {
            val cleanSignature = signature.replace("function", "").trim()
            var state = ParseState.NAME
            var startIndex = 0
            var nestingLevel = 0
            var name: String? = null
            var inputsRaw: String? = null
            var outputsRaw: String? = null

            for (i in cleanSignature.indices) {
                when (state) {
                    ParseState.NAME -> {
                        if (cleanSignature[i] == '(') {
                            name = cleanSignature.substring(startIndex, i)
                            startIndex = i + 1
                            nestingLevel++

                            state = ParseState.INPUTS
                        }
                    }

                    ParseState.INPUTS -> {
                        if (cleanSignature[i] == '(') {
                            nestingLevel++
                            continue
                        }

                        if (cleanSignature[i] == ')') {
                            nestingLevel--

                            if (nestingLevel == 0) {
                                inputsRaw = cleanArgumentNames(cleanSignature.substring(startIndex, i))
                                startIndex = i + 1
                                state = ParseState.OUTPUTS
                            }
                        }
                    }

                    ParseState.OUTPUTS -> {
                        when {
                            // find outputs start
                            nestingLevel == 0 && cleanSignature[i] != '(' -> continue

                            // found outputs start, increment nesting level and remember start index
                            nestingLevel == 0 && cleanSignature[i] == '(' -> {
                                startIndex = i + 1
                                nestingLevel++
                                continue
                            }

                            // found a nested definition, increment nesting level
                            nestingLevel > 0 && cleanSignature[i] == '(' -> {
                                nestingLevel++
                                continue
                            }
                        }

                        if (cleanSignature[i] == ')') {
                            nestingLevel--

                            if (nestingLevel == 0) {
                                outputsRaw = cleanArgumentNames(cleanSignature.substring(startIndex, i))
                                break
                            }
                        }
                    }
                }
            }

            if (name.isNullOrBlank()) throw IllegalArgumentException("Invalid signature, function has no name: $signature")

            val inputs = if (inputsRaw.isNullOrBlank()) emptyList() else AbiType.parseSignature(inputsRaw)
            val outputs = if (outputsRaw.isNullOrBlank()) emptyList() else AbiType.parseSignature(outputsRaw)

            return AbiFunction(name, inputs, outputs)
        }

        /**
         * Remove all argument names from [signature], leaving only types, separated by commas.
         * E.g. `address owner, uint256 amount` -> `address,uint256`
         * */
        private fun cleanArgumentNames(signature: String): String {
            return signature.split(',').joinToString(",") {
                val cleaned = it.trim()

                // handle tuples
                if (cleaned.startsWith('(') && cleaned.endsWith(')')) {
                    "(${cleanArgumentNames(cleaned.substring(1, cleaned.length - 1))})"
                } else {
                    cleaned.split(' ').first()
                }
            }
        }
    }
}
