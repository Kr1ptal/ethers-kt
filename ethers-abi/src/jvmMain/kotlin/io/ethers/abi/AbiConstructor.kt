package io.ethers.abi

import io.ethers.core.types.Bytes

class AbiConstructor(
    val bytecode: Bytes,
    val arguments: List<AbiType<*>>,
) {
    fun encode(args: List<Any>): Bytes {
        return Bytes(AbiCodec.encodeWithPrefix(bytecode, arguments, args))
    }
}
