# ethers-rlp

Handles the serialization and deserialization of the data
in [RLP (Recursive Length Prefix)](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/) format, which is extensively used in Ethereum's execution clients. The code is optimized for performance by minimizing the number
of allocations and copying.

The RLP logic is implemented in `RlpEncoder` and `RlpDecoder` classes. They are written in way so that the actual
structure of the data can be represented in code:

```kotlin
val rlp = RlpEncoder()

rlp.encodeList {
    encodeString("Hello")
    encodeString("World")

    encodeList {
        encodeString("Hello from")
        encodeString("nested list")
    }
}
```

The `RlpEncodable` and `RlpDecodable` interfaces are used to define the serialization and deserialization logic of
custom data types. `RlpEncodable` is intended for serializing an actual instance of a type to RLP, while `RlpDecodable` acts as a
factory to create an instance of a type from the RLP. Recommended pattern when working in Kotlin is to implement the
encodable interface directly on the type, and the decodable interface on a companion object:

```kotlin
data class MyData(val a: BigInteger, val b: Long) : RlpEncodable {
    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encodeList {
            encode(a)
            encode(b)
        }
    }

    companion object : RlpDecodable<MyData> {
        override fun rlpDecode(rlp: RlpDecoder): MyData {
            return rlp.decodeList {
                MyData(decodeBigInteger(), decodeLong())
            }
        }
    }
}
```
