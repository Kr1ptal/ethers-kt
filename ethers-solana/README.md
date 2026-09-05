# ethers-solana

Solana support for ethers-kt on JVM (Java 11+), Android, iOS, and macOS. Add
`io.kriptal.ethers:ethers-solana` using the matching ethers BOM version. This new module is part of the next
library publication; it is not included in previously released BOMs. Within this repository use
`implementation(project(":ethers-solana"))`.

The module depends on `ethers-providers` and reuses its HTTP/WebSocket clients, request batching, errors,
and coroutine/blocking/future execution APIs. Ethereum dependencies are therefore transitive.

Android currently inherits a known `channels-core:1.0.4` incompatibility: its MethodHandle bytecode prevents
APK dexing below API 26 despite the repository's declared `minSdk 24`. Fixing that existing dependency is
deferred. The module's Android library compiles, but API 24 device tests cannot run until that is resolved.

## Kotlin

```kotlin
import io.ethers.solana.instruction.TransferInstruction
import io.ethers.solana.providers.SolanaCluster
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.PublicKey
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.TransactionMessage

// Run inside a coroutine. Supply your funded account's 32-byte seed and recipient.
suspend fun transfer(seed: ByteArray, recipient: PublicKey) {
    val provider = SolanaProvider.builder(SolanaCluster.DEVNET).build().unwrap()
    try {
        val signer = KeypairSigner.fromSeed(seed)
        val latest = provider.getLatestBlockhash().send().unwrap().value
        val message = TransactionMessage.compile(
            signer.publicKey, latest.blockhash,
            TransferInstruction(signer.publicKey, recipient, 1_000L),
        )
        val transaction = SolanaTransaction(message).sign(signer)
        val signature = provider.sendTransaction(transaction).send().unwrap()
        println(signature)
    } finally {
        provider.close()
    }
}
```

`send()` suspends and returns `io.ethers.core.Result`; `unwrap()` explicitly opts into throwing on failure.
JVM/Android also expose inherited `sendAwait()` and `sendAsync()` members. Constructors and invalid local
transaction inputs fail immediately with `IllegalArgumentException`.

## Java

```java
var provider = SolanaProvider.builder(SolanaCluster.DEVNET).build().unwrap();
try {
    var signer = KeypairSigner.fromSeed(seed);
    var latest = provider.getLatestBlockhash().sendAwait().unwrap().getValue();
    var instruction = new TransferInstruction(signer.getPublicKey(), recipient, 1_000L);
    var message = TransactionMessage.compile(signer.getPublicKey(), latest.getBlockhash(), instruction);
    var transaction = new SolanaTransaction(message).sign(signer);
    var signature = provider.sendTransaction(transaction).sendAwait().unwrap();
} finally {
    provider.close();
}
```

## Subscriptions and configuration

For private endpoints, supply the WebSocket URL explicitly:

```kotlin
val provider = SolanaProvider.builder("https://your-rpc.example")
    .webSocketUrl("wss://your-rpc.example")
    .commitment(Commitment.CONFIRMED)
    .config(RpcClientConfig().requestHeaders(mapOf("Authorization" to "Bearer ...")))
    .build().unwrap()
val stream = provider.subscribeLogs(LogsFilter.Mentions(publicKey)).send().unwrap()
// Consume using the ChannelReceiver API. Closing the stream unsubscribes.
stream.close()
provider.close()
```

The builder configures Solana subscription routing without modifying the supplied `RpcClientConfig`.
When constructing a `WsClient` directly, set
`RpcClientConfig().subscriptionDescriptor(SolanaSubscriptionDescriptor)`. Low-level `subscribe` calls
take the stream name first (for example, `arrayOf("slot")`); the descriptor converts it into Solana's
RPC method and removes the name from the wire parameters. Ethereum remains the default for other clients.

Streams cover accounts, program accounts (data-size/memcmp filters), logs, signatures, slots, and roots.
Signature streams optionally deliver a `Received` event, then a `Status` event and close. Read queued events
even when a stream reports closed. Reconnects restore active subscriptions by default but do not replay missed
events; change this through `RpcClientConfig.resubscribeOnReconnect(false)`. HTTP-only providers report an
unsupported-method error for subscriptions. A provider owns its RPC clients, but the shared Ktor client's
lifecycle remains governed by the existing transport configuration.

Default commitment is `FINALIZED`; RPC methods accepting commitment also support per-call overrides. No
Ethereum chain-ID lookup is performed. Construction does not guarantee that an endpoint is reachable.

## Unit conversion

`SolUnit` follows `EthUnit`'s decimals-based API, with `MICRO_LAMPORT`, `LAMPORT`, and `SOL` constants.
Each supports `to...`, `from...`, and `convert(amount, toUnit)` methods accepting `Int`, `Long`, `Double`,
`String`, `BigInteger`, and `BigDecimal`, returning `BigDecimal`.

```kotlin
val lamports = SolUnit.SOL.toLamports("1.000000001").toBigIntegerExact()
val sol = SolUnit.LAMPORT.toSol(lamports)
val priorityPrice = SolUnit.MICRO_LAMPORT.fromLamports("0.5") // 500000 micro-lamports
```

Decimals are relative to a micro-lamport: `MICRO_LAMPORT = SolUnit(0)`, `LAMPORT = SolUnit(6)`, and
`SOL = SolUnit(15)`. This preserves fractional-lamport priority-fee precision; decimal conversions truncate
towards zero below one micro-lamport. Use strings or `BigDecimal` for precise decimal inputs, and
`toBigIntegerExact()` when an API requires whole lamports. Unit conversion permits negative and arbitrarily
large values; transaction and RPC APIs apply their own unsigned-amount validation.

## Ported capabilities

Reference: [sol4k a166edd854a7198553fdafe9a5051a400d70b121](https://github.com/sol4k/sol4k/tree/a166edd854a7198553fdafe9a5051a400d70b121).

| Upstream capability | ethers-solana API |
| --- | --- |
| Keys, detached signing, verification | `PublicKey`, `Signature`, `SolanaSigner`, `KeypairSigner` |
| PDA / associated token address derivation | `PublicKey.createProgramAddress`, `findProgramAddress`, `findAssociatedTokenAddress` |
| Legacy/v0 messages and lookup tables | `TransactionMessage.compile`, `MessageVersion`, `AddressLookupTableAccount` |
| Build, sign, import/export transactions | Immutable `SolanaTransaction`; `serializePartial` for offline signing |
| SOL, SPL, Token-2022, associated accounts, compute budget | Classes in `io.ethers.solana.instruction` |
| Arbitrary program instructions | `BaseInstruction` |
| Unit conversion / fee estimation | `SolUnit`, `SolanaTransaction.estimateFee` |
| All upstream public RPC methods | `SolanaApi` / `SolanaProvider` |
| Additional WebSocket support | `subscribeAccount`, `subscribeProgram`, `subscribeLogs`, `subscribeSignature`, `subscribeSlot`, `subscribeRoot` |

RPC coverage: `getAccountInfo`, `getBalance`, `getEpochInfo`, `getFeeForMessage`, `getHealth`, `getIdentity`,
`getLatestBlockhash`, `getMinimumBalanceForRentExemption`, `getMultipleAccounts`, `getRecentPrioritizationFees`,
`getSignaturesForAddress`, `getTokenAccountBalance`, `getTokenSupply`, `getTransactionCount`, `getVersion`,
`isBlockhashValid`, `requestAirdrop`, `sendTransaction`, and `simulateTransaction`.

## Migration notes

- Imports move from `org.sol4k` to `io.ethers.solana` packages. This is capability parity, not source compatibility.
- RPC functions construct lazy requests. Call `send`, `sendAwait`, or `sendAsync` to execute them. They also batch
  using the existing `BatchRpcRequest` API.
- Contextual responses retain `context.slot`; latest blockhash responses retain `lastValidBlockHeight`.
  Transaction submission does not automatically wait for confirmation or retry expired blockhashes.
- Use `fromSeed` for 32-byte seeds and `fromSecretKey` for validated 64-byte seed/public-key pairs. Secret exports
  and public byte arrays are copied. JVM/Android use TweetNaCl; Apple uses cryptography-kotlin/OpenSSL Ed25519.
- Monetary and other unsigned 64-bit quantities use the repository's multiplatform `BigInteger`, not signed
  `Long` or floating point. Request quantities are decimal JSON numbers; token amounts retain their RPC string encoding.
- Account data is explicitly requested as base64. Simulation `err` is retained as structured JSON in a successful
  RPC result, with nullable logs supported.
- Messages default to v0. Choose `MessageVersion.LEGACY` explicitly when needed. Lookup-table addresses are supplied
  by the caller; automatic table fetching is outside this module's initial RPC coverage.
- Transactions are immutable: retain the value returned by `sign`/`addSignature`. Message changes discard signatures.
  `serialize` and submission require all signatures; partial import/export is explicit. Unknown versions, malformed
  lengths/indices, and invalid signatures are rejected.
- PDA seeds may be arbitrary bytes. Seed bounds and bump zero are handled. Compute-unit-limit instructions encode
  their payload as u32 (correcting the upstream u64 encoding).
- Offline fee estimation requires an explicit compute-unit limit when a nonzero unit price is set. Use
  `getFeeForMessage` for a node-calculated fee that accounts for runtime defaults.
- Token-2022 helpers cover the same checked-transfer capabilities as sol4k. Extensions requiring additional
  accounts/instructions must be constructed explicitly. Block, vote, and slot-update subscriptions are excluded.

## Verification

```sh
./gradlew :ethers-solana:jvmKotest :ethers-solana:macosArm64Test :ethers-providers:jvmKotest
./gradlew :ethers-solana:iosSimulatorArm64Test :ethers-solana:compileTestKotlinIosArm64 :ethers-solana:compileTestKotlinIosX64
# Device smoke test; currently blocked below API 26 by channels-core (see above).
./gradlew :ethers-solana:connectedAndroidDeviceTest
```

Common tests include RFC8032 signing, upstream PDA/transaction fixtures, an independent Solana CLI message/signature
fixture, RPC wire shapes, and all subscription decoders. Provider tests cover numeric subscription IDs, reconnects,
cancellation, and terminal events. Java API examples are compiled with `--release 11`.

An optional local-validator test creates ephemeral test accounts and uses the local faucet. Start your own
`solana-test-validator` and opt in (loopback endpoints only):

```sh
SOLANA_VALIDATOR_HTTP=http://127.0.0.1:8899 SOLANA_VALIDATOR_WS=ws://127.0.0.1:8900 \
  ./gradlew :ethers-solana:jvmKotest --rerun-tasks
```

See [NOTICE](NOTICE) for upstream provenance and licensing.
