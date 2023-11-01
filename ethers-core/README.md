# ethers-core

Contains core data types for interacting with EVM-based blockchains. It also includes a highly optimized `FastHex`
hexadecimal codec implementation and serialization/deserialization utility
functions (`Jackson`, `JsonParserExtensions`).

When implementing a new data structure make sure to implement custom serializer/deserializer for it. For example, see
the [Address](src/main/kotlin/io/ethers/core/types/Address.kt) class. If the data type is expect to be
only received by the library, then only a deserializer is required.

For working with JSON data, use the JsonMapper instance defined in [Jackson](src/main/kotlin/io/ethers/core/Jackson.kt).
Additionally, there exists a set of pre-developed extension functions for `JsonParser`, simplifying the process of
working with JSON data. These can be found
in [JsonParserExtensions](src/main/kotlin/io/ethers/core/JsonParserExtensions.kt).

## 🧱 Transaction types

```
Transaction
  ├── TransactionUnsigned
  │      ├── TxAccessList
  │      ├── TxDynamicFee
  │      └── TxLegacy
  └── TransactionRecovered
     └── TransactionSigned
```

tl;dr: for sending transactions use one of the `TransactionUnsigned` subclasses, which - when signed - produces
a `TransactionSigned` that can be sent.

The structure of transactions is defined by the following hierarchy:

- `Transaction` is the base type of all other implementations. It contains all the properties common to all
  transaction types, excluding the fields related to signature: `from`, `hash`, `r`, `s`, `v`. In other words, it
  contains enough information to see what the tx does, but not who initiated it.

- `TransactionUnsigned` defines an unsigned transaction interface with functions for signing. Its
  implementations encompass the currently supported EVM transaction types:

    - `TxLegacy` - A **Legacy Transaction** type that follows the
      pre-[EIP-2718](https://eips.ethereum.org/EIPS/eip-2718)
      transaction format.
    - `TxAccessList` - An **Access List Transaction** type introduced
      in [EIP-2930](https://eips.ethereum.org/EIPS/eip-2930).
    - `TxDynamicFee` - A **Dynamic Fee Transaction** type introduced in Ethereum's London fork
      through [EIP-1559](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-1559.md).

- `TransactionRecovered` contains the recovered `from` address and transaction `hash` fields, without the signature.

    - `TransactionSigned` also has a valid signature, and is used to send transactions.

## 💻 Code Examples

- Use FastHex to encode and decode string with and without prefix.

    ```kotlin
    val bytes = "ethers-core".toByteArray()
    
    val hexWithPrefix = FastHex.encodeWithPrefix(bytes)
    val hexWithoutPrefix = FastHex.encodeWithoutPrefix(bytes)
    
    val decodedHexWithPrefix = FastHex.decode(hexWithPrefix)
    val decodedHexWithoutPrefix = FastHex.decode(hexWithoutPrefix)
    ```
- Decode raw transaction:

    ```kotlin
    val tx = TransactionSigned.rlpDecode(Bytes("0x12412433525432deadbeef").value)
    ```

- Create and sign transaction (need to import `ethers-crypto` module):

    ```kotlin
    val signer = PrivateKeySigner("0x0123456789012345678901234567890123456789012345678901234567890123")
    
    val unsigned = TxDynamicFee(
        to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
        value = "1000000000".toBigInteger(),
        nonce = 12425132,
        gas = 2000000,
        gasFeeCap = "210000000000".toBigInteger(),
        gasTipCap = "21000000000".toBigInteger(),
        data = Bytes("0x1214abcdef12445980"),
        chainId = 1L,
        accessList = null,
    )
    
    val signed = unsigned.sign(signer)
    ```

- Get address RLP encoding and calculate CREATE and CREATE2 addresses off-chain.

    ```kotlin
    val sender = Address("0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0")
    val addressRlp = sender.toRlp()
    
    val nonce = 2L
    val computedCreateAddress = Address.computeCreate(sender, nonce)
    
    val salt = "ethers-core".padStart(64, '0').toByteArray()
    val initCodeHash = Hashing.keccak256("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadabcd".hexToByteArray())
    val computedCreate2Address = Address.computeCreate2(sender, salt, initCodeHash)
    ```

Additional examples can be found in [tests](src/test/kotlin/io/ethers/core).

