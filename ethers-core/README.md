# ethers-core

Contains core data types for interacting with EVM-based blockchains. It also includes a highly optimized `FastHex`
hexadecimal codec implementation and the `kotlinx.serialization` glue used to (de)serialize JSON-RPC payloads.

When implementing a new data structure make sure to annotate it with `@Serializable` and give it a custom
`KSerializer` where the wire format is not the default one. For example, see
the [Address](src/commonMain/kotlin/io/ethers/core/types/Address.kt) class. If the data type is expected to be
only received by the library, then only deserialization needs to be correct.

For working with JSON data, use the shared `Json` instance defined
in [Kotlinx](src/commonMain/kotlin/io/ethers/core/Kotlinx.kt). Ethereum encodes most scalars as 0x-prefixed hex
strings rather than JSON numbers, so field-level serializers for those live
in [HexSerializers](src/commonMain/kotlin/io/ethers/core/HexSerializers.kt) - annotate a field
with `@Serializable(with = HexLongSerializer::class)` and it round-trips as a quantity. There is also a set of
extension functions for reading values straight off a `JsonElement`,
in [JsonElementExtensions](src/commonMain/kotlin/io/ethers/core/JsonElementExtensions.kt).

## 🧱 Transaction types

```
Transaction
  ├── TransactionUnsigned (no Signature, RLP-encodable)
  │      ├── TxLegacy
  │      ├── TxAccessList
  │      ├── TxDynamicFee
  │      ├── TxBlob
  │      └── TxSetCode
  └── TransactionRecovered ( + "hash", "from" fields)
     ├── RPCTransaction
     └── TransactionSigned (with Signature, RLP-encodable)
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
    - `TxBlob` - A **Blob Transaction** type introduced in Ethereum's Cancun fork
      through [EIP-4844](https://eips.ethereum.org/EIPS/eip-4844).
    - `TxSetCode` - A **Set Code Transaction** type introduced in Ethereum's Prague fork
      through [EIP-7702](https://eips.ethereum.org/EIPS/eip-7702).

- `TransactionRecovered` contains the recovered `from` address and transaction `hash` fields, without the signature.

    - `RPCTransaction` also contains the `r`, `s`, `v` fields, and is a type used for block/rpc transactions.
    - `TransactionSigned` also has a valid signature, and is used to send transactions.

## 💻 Code Examples

- Use FastHex to encode and decode string with and without prefix.

    ```kotlin
    val bytes = "ethers-core".encodeToByteArray()
    
    val hexWithPrefix = FastHex.encodeWithPrefix(bytes)
    val hexWithoutPrefix = FastHex.encodeWithoutPrefix(bytes)
    
    val decodedHexWithPrefix = FastHex.decode(hexWithPrefix)
    val decodedHexWithoutPrefix = FastHex.decode(hexWithoutPrefix)
    ```
- Decode raw transaction:

    ```kotlin
    val tx = TransactionSigned.rlpDecode(Bytes("0x12412433525432deadbeef").asByteArray())
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
        accessList = emptyList(),
    )
    
    val signed = unsigned.sign(signer)
    ```

- Get address RLP encoding and calculate CREATE and CREATE2 addresses off-chain.

    ```kotlin
    val sender = Address("0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0")
    val addressRlp = sender.toRlp()
    
    val nonce = 2L
    val computedCreateAddress = Address.computeCreate(sender, nonce)
    
    val salt = "ethers-core".padStart(64, '0').encodeToByteArray()
    val initCodeHash = Hashing.keccak256("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadabcd".hexToByteArray())
    val computedCreate2Address = Address.computeCreate2(sender, salt, initCodeHash)
    ```

Additional examples can be found in [tests](src/commonTest/kotlin/io/ethers/core).

