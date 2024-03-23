package io.ethers.abi

/**
 * A contract-defined struct. Supports converting its fields to a tuple, and creating a struct from a tuple
 * via [StructFactory].
 **/
interface ContractStruct {
    val tuple: Array<Any>
}

/**
 * Struct factory, used to create a [ContractStruct] from a tuple.
 * */
interface StructFactory<T : ContractStruct> {
    fun fromTuple(data: Array<out Any>): T
}
