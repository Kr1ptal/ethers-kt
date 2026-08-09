# ethers-signers

Abstracts the transaction and messages signing process, allowing multiple signing key sources, such as:

- `hardware wallet`,
- `mnemonic` or
- `raw private key`.

`PrivateKeySigner` covers the raw-private-key case, and `MnemonicKeySource` derives keys from a BIP-39 seed
phrase. A Google Cloud KMS-backed signer lives in the separate, JVM-only `ethers-signers-gcp` module.
Functionality can be easily extended to other sources by implementing the `Signer` interface - the only member
you have to provide is `signHash`.

## 💻 Code Examples

- Sign a message and recover signer address.

    ```kotlin
    val privateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    val signer = PrivateKeySigner(privateKey)
    
    val messageToSign = "ethers-signers".encodeToByteArray()
    val signature = signer.signMessage(messageToSign)
    val recoveredAddress = signature.recoverFromMessage(messageToSign)
    ```

- Sign a transaction.

    ```kotlin
    val privateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    val signer = PrivateKeySigner(privateKey)
    
    val transactionToSign = TxDynamicFee(
        to = Address("0x1be31a94361a391bbafb2a4ccd704f57dc04d4bb"),
        value = "1000000000".toBigInteger(),
        nonce = 12425132,
        gas = 2000000,
        gasFeeCap = "210000000000".toBigInteger(),
        gasTipCap = "21000000000".toBigInteger(),
        data = Bytes("0x1214abcdef12445980"),
        chainId = 1L,
        accessList = emptyList(),
    )
    val signature = signer.signTransaction(transactionToSign)
    ```

Other signing examples can be found in [tests](src/commonTest/kotlin/io/ethers/signers).

