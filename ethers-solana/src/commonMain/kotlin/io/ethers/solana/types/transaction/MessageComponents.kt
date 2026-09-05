package io.ethers.solana.types.transaction

import io.ethers.solana.types.SolanaAddress

data class MessageHeader(val requiredSignatures: Int, val readonlySignedAccounts: Int, val readonlyUnsignedAccounts: Int)

class AddressLookupTableAccount(val key: SolanaAddress, addresses: List<SolanaAddress>) {
    private val entries = addresses.toList().also { require(it.size <= 256) { "Lookup table exceeds 256 addresses" } }
    val addresses: List<SolanaAddress> get() = entries.toList()
}

class CompiledInstruction(val programIdIndex: Int, accounts: List<Int>, data: ByteArray) {
    private val indices = accounts.toList()
    private val payload = data.copyOf()
    val accounts: List<Int> get() = indices.toList()
    val data: ByteArray get() = payload.copyOf()
}

class CompiledAddressLookupTable(val key: SolanaAddress, writableIndexes: List<Int>, readonlyIndexes: List<Int>) {
    private val writable = writableIndexes.toList()
    private val readonly = readonlyIndexes.toList()
    val writableIndexes: List<Int> get() = writable.toList()
    val readonlyIndexes: List<Int> get() = readonly.toList()
}
