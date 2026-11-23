# ethers-account-abstraction

[ERC-4337](https://eips.ethereum.org/EIPS/eip-4337) account abstraction for EntryPoint v0.6, v0.7, v0.8 and v0.9.

The module is vendor-neutral: it contains no smart account implementations. Accounts implement the `SmartAccount`
interface, and everything else — building, hashing, estimating, sponsoring, signing, sending and decoding user
operations — works with any of them.

The snippets below use the blocking `sendAwait` terminal, which exists only on JVM and Android. From common code, or
from any coroutine, call the suspending `send()` instead.

## User operations

`UserOperation` covers every EntryPoint version in the unpacked form bundler APIs use. `EntryPoint` describes a
deployment, with the canonical ones predefined:

```kotlin
val hash = userOp.hash(EntryPoint.V0_7, chainId)        // what the account signs
val packed = userOp.toPacked(EntryPointVersion.V0_7)     // the on-chain PackedUserOperation
val json = UserOperationJson.encode(userOp, EntryPointVersion.V0_7)
```

- v0.6 operations use `initCode` and `paymasterAndData` on-chain, built from `factory`/`factoryData` and
  `paymaster`/`paymasterData`.
- v0.8 and later hash with EIP-712, and support EIP-7702 accounts: `factory` is `UserOperation.EIP7702_FACTORY_MARKER`
  and the hash commits to the delegate, taken from `eip7702Auth`.
- v0.9 adds `paymasterSignature`, which is excluded from the hash.

## Bundlers and paymasters

`BundlerClient` implements the ERC-7769 bundler API, and `PaymasterClient` the ERC-7677 paymaster API:

```kotlin
val bundler = BundlerClient(HttpClient("<BUNDLER_URL>"))
val userOpHash = bundler.sendUserOperation(signedUserOp, EntryPoint.V0_7).sendAwait().unwrap()
val receipt = bundler.waitForUserOperationReceipt(userOpHash).sendAwait().unwrap()
```

Rejections carry the EntryPoint's `AAxx` code and the ERC-7769 error code, when present:

```kotlin
val result = bundler.sendUserOperation(userOp, EntryPoint.V0_7)
    .mapError { it.toUserOperationError() }
    .sendAwait()

if (result.unwrapErrorOrNull()?.entryPointError == EntryPointErrorCode.INVALID_NONCE) {
    // refresh the nonce and retry
}
```

## Sending operations from a smart account

`SmartAccountClient` prepares, signs and sends operations of a `SmartAccount`. It fills in whatever the request leaves
unset: call data, nonce, fees, deployment of an undeployed account (or EIP-7702 delegation), a stub signature, stub
paymaster data, bundler gas estimates and final paymaster data.

```kotlin
val client = SmartAccountClient(
    account = myAccount,                 // your SmartAccount implementation
    bundler = BundlerClient(HttpClient("<BUNDLER_URL>")),
    provider = provider,                 // a node of the same chain
    paymaster = PaymasterClient(HttpClient("<PAYMASTER_URL>")),  // optional
)

val userOpHash = client.sendUserOperation(listOf(Call(to = recipient, value = amount))).sendAwait().unwrap()
```

A `UserOperationRequest` overrides any field, sets paymaster context, or signs through another validator, e.g. a
session key, which selects its validator through its nonce key:

```kotlin
val request = UserOperationRequest(calls)
    .signer(sessionKeySigner)
    .paymasterContext(mapOf("policyId" to "<POLICY>"))

client.sendUserOperation(request).sendAwait().unwrap()
```

Fees default to `NodeFeeEstimator`. Implement `FeeEstimator` to use a bundler's own gas price method instead.

## Decoding executed bundles

`BundleDecoder` splits a bundle transaction into the user operations it executed, collecting each one's outcome,
logs and calls into an `ExecutedUserOperation`. `BundleReader` fetches the data from a node:

```kotlin
val reader = BundleReader(provider, useTrace = true)

val bundles = reader.getBundles(txHash).sendAwait().unwrap()
val op = reader.getExecutedUserOperation(userOpHash, BlockId.Number(fromBlock), BlockId.LATEST).sendAwait().unwrap()
val history = reader.getUserOperationsBySender(account, BlockId.Number(fromBlock), BlockId.LATEST).sendAwait().unwrap()
```

What is available depends on the input:

| Input | Gives |
|---|---|
| Receipt | Outcome, execution logs, and validation events carrying the operation's hash |
| + transaction calling the EntryPoint directly | The submitted `UserOperation`, beneficiary and decoded calls |
| + `callTracer` trace with logs | All of the above for bundles sent through other contracts too, every validation log, `postOp` logs split out, and the EntryPoint's call frames per operation |

Calls are decoded by `StandardCallDataDecoder`, which understands the common `execute`/`executeBatch` functions and
ERC-7579 `execute`. Pass your own `CallDataDecoder` for other accounts.

## Signature verification

`SignatureVerifier` verifies signatures of EOAs, deployed ERC-1271 accounts, and counterfactual accounts with
ERC-6492 signatures, through a deployless `eth_call` that needs no contract deployment:

```kotlin
val valid = SignatureVerifier(provider).verifyMessage(account, message, signature).sendAwait().unwrap()
```

## Passkeys

The `webauthn` package has the building blocks of passkey-owned accounts: `P256PublicKey`, `P256Signature` with DER
parsing and low-s normalization, `WebAuthnAssertion` with the fields on-chain verifiers need, and off-chain
verification. Each platform has its own passkey API, so apps implement `WebAuthnAuthenticator` on top of it.
`SoftwareWebAuthnAuthenticator` implements it in memory, for tests.

## EntryPoint bindings

`EntryPointV06` to `EntryPointV09` are contract bindings generated from the official ABIs in [`abi`](abi). To
regenerate them, run `io.ethers.abigen.AbigenCliKt` from `ethers-abigen` with the arguments
`ethers-account-abstraction/abi <empty-temp-dir> io.ethers.aa.contracts AccountAbstraction`, copy the output into
`src/commonMain/kotlin`, and run `./gradlew :ethers-account-abstraction:ktlintFormat`. The CLI deletes its output
directory first, so never point it at the source directory.
