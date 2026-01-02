# ethers-crypto

Includes cryptographic utilities for both signing and verifying **ECDSA** signatures on the **secp256k1** curve, and
computing the **keccak256** hash. All signatures are canonicalized so the `s` value is always in the lower half of the
curve.

## 💻 Code Examples

- Use `keccak256` to hash raw data or a message based on the [EIP-191](https://eips.ethereum.org/EIPS/eip-191) standard.

    ```kotlin
    val messageToHash = "ethers-crypto"
    
    val dataHash = Hashing.keccak256(messageToHash.toByteArray())
    val msgHash = Hashing.hashMessage(messageToHash.toByteArray())
    ```

- Use the `Secp256k1.SigningKey` to derive the address from a public key, sign a hash, recover the public key from the
  signature.

  ```kotlin
  val privateKey = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
  val signingKey = Secp256k1.SigningKey(BigInteger(privateKey, 16))

  val address = Secp256k1.publicKeyToAddress(signingKey.publicKey)

  val messageToSign = "ethers-crypto"
  val messageHash = Hashing.hashMessage(messageToSign.toByteArray())
  val signature = signingKey.signHash(messageHash)

  val recoveredPublicKey = Secp256k1.recoverPublicKey(
      messageHash,
      signature[0], // r
      signature[1], // s
      signature[2].toLong() // v 
  )
  ```

Alternative usages can be found in [tests](src/test/kotlin/io/ethers/crypto).

[ecdsa-recoverable-source]: src/main/kotlin/io/ethers/crypto/ECDSASignerRecoverable.kt
