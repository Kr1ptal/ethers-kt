# ethers-signers

Abstracts the transaction and messages signing process, allowing multiple signing key sources, such as:

- `hardware wallet`,
- `mnemonic` or
- `raw private key`.

Currently `raw private key` signer is supported. Functionality can be easily extended to other sources by implementing
the `Signer` interface.

## 💻 Code Examples

- Sign a message and recover signer address.

    ```kotlin
    val privateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    val signer = PrivateKeySigner(privateKey)
    
    val messageToSign = "ethers-signers".toByteArray()
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

Other signing examples can be found in [tests](src/test/kotlin/io/ethers/signers).

