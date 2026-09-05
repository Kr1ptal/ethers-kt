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
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.transaction.SolanaTxV0

// Run inside a coroutine. Supply your funded account's 32-byte seed and recipient.
suspend fun transfer(seed: ByteArray, recipient: SolanaAddress) {
    val provider = SolanaProvider.builder(SolanaCluster.DEVNET).build().unwrap()
    try {
        val signer = KeypairSigner.fromSeed(seed)
        val latest = provider.getLatestBlockhash().send().unwrap().value
        val message = SolanaTxV0.compile(
            signer.publicKey, latest.blockhash,
            TransferInstruction(signer.publicKey, recipient, 1_000L),
        )
        val transaction = message.sign(signer)
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
    var message = SolanaTxV0.compile(signer.getPublicKey(), latest.getBlockhash(), instruction);
    var transaction = message.sign(signer);
    var signature = provider.sendTransaction(transaction).sendAwait().unwrap();
} finally {
    provider.close();
}
```

## Transaction types and signing

`SolanaTransaction` is the common interface. `SolanaTxLegacy` and `SolanaTxV0` implement
`SolanaTransactionUnsigned`; only v0 accepts address lookup tables. `SolanaTransactionSigned` holds the
unsigned payload in `tx` and delegates its common properties. There is no separate message hierarchy.

```kotlin
val unsigned = SolanaTxV0.compile(feePayer, blockhash, instructions)
val builder = unsigned.signingBuilder().sign(alice) // SolanaTransactionSigned.Builder
val signed = builder.sign(bob).build()              // requires every signature
// Or collect external signatures with addSignature(address, signature), then call build().
```

`unsigned.sign(vararg signers)` requires all signers. The nested builder collects signatures for a fixed
unsigned payload; it is mutable, not thread-safe, and is not a transaction itself. `build()` returns an immutable
fully signed snapshot. Constructors and imports verify every supplied signature against its ordered signer slot.
A signed transaction's `id` is its first signature. Replacing the message or blockhash returns an unsigned
transaction and discards signatures; start a new builder for that payload.

- `serializeMessage()` produces only the Ed25519 signing payload, on any transaction state.
- `SolanaTransactionSigned.serialize()` produces a fully signed envelope accepted by typed `sendTransaction`.
- `serializeForSimulation()` produces an unsigned or fully signed envelope, with zeros for unsigned slots.
- `Builder.serializePartial()` explicitly exports an offline signature collection, also accepted by raw simulation.
- `SolanaTransactionUnsigned.deserializeMessage()` parses message bytes; `SolanaTransaction.deserialize()`
  parses an unsigned or fully signed envelope. `SolanaTransactionSigned.deserialize()` rejects incomplete
  envelopes, while `SolanaTransactionSigned.Builder.deserializePartial()` imports a collection for further
  signing. The envelope decoders also have Base64 counterparts.

## Reading transactions from RPC

Chain-level values and transaction/API families use explicit Solana names: `SolanaSignature`,
`SolanaBlockhash`, and `SolanaRPCTransaction` (with its `SolanaRPC*` components). Node queries return
`SolanaNodeVersion` and `SolanaNodeHealth`. These names coexist with EVM types without import aliases.
Protocol-specific components such as `AccountMeta`, `CompiledInstruction`, `InnerInstructions`, and
`LoadedAddresses` keep their shorter names.

```kotlin
val transaction = provider.getTransaction(signature).send().unwrap() // SolanaRPCTransaction?
if (transaction != null) {
    val type = transaction.type // Legacy, V0, or Unsupported(version); never null
    val signatures = transaction.transaction.signatures // List<SolanaSignature>
    val message = transaction.transaction.message       // SolanaRPCMessage
    val accounts = message.accountKeys                  // List<SolanaAddress>
    val instructionData = message.instructions.firstOrNull()?.data // SolanaBytes?
    val fee = transaction.meta?.fee                     // BigInteger? (lamports)
    val extra = transaction.otherFields // fields introduced by newer validators
}
```

`SolanaRPCTransaction` is deliberately separate from the signable transaction hierarchy. Its `slot`, `blockTime`,
and `type` are exposed directly. `transaction` is a `SolanaRPCTransactionData` with typed signatures and message
fields: account addresses, blockhash, header, instructions, and lookup tables. `meta` is a
`SolanaRPCTransactionMeta` with typed fees, balances, token balances, logs, inner instructions, loaded addresses,
return data, rewards, compute/cost units, and extensible errors (including instruction indices and custom codes).
Instruction and return data are decoded to `SolanaBytes`. Compiled instruction `accounts` are integer indices.
The provider requests compiled `json`, so these models do not contain parsed/binary/accounts payload variants.
Metadata amounts use `BigInteger`, signed reward changes use `Long`, and JSON token UI amounts use `BigDecimal`
without introducing additional floating-point rounding. Prefer the integer token amount and decimals for arithmetic.

Each concrete model has its own serializer and typed constructor properties. Shared `TokenAmount`,
`TokenBalance`, `ReturnData`, `AccountInfo`, `InnerInstructions`, and `LoadedAddresses` are reused wherever
their wire structures match. `TransactionError` and `InstructionError` model the runtime's named and
parameterized variants; unknown future errors retain their JSON. `RewardType` retains unknown string values.

Known fields are decoded even for unsupported numeric versions. Required fields are non-null and missing
required values fail decoding. Nullable fields represent genuinely unavailable data, such as `blockTime`,
`meta`, recording-dependent metadata, and token UI amounts. Missing legacy address-table lookups become
an empty list. An omitted version means legacy, matching validator output; explicit null or malformed
versions are rejected.

Extensible response objects preserve unknown keys in `otherFields`. A shared serializer adapter delegates
known properties to generated serializers and flattens unknown keys back into the wire object, retaining
numeric precision. Serialization uses the model's current property values; defaults may normalize omitted
versus explicit-null fields rather than reproduce the original JSON text. Unknown keys cannot override
known properties. Malformed base58, signature lengths, and out-of-range quantities are rejected; reading
does not perform binary message sanitization or cryptographic signature verification.

`getTransaction` requests `json` encoding and defaults `maxSupportedTransactionVersion` to `255`, the full
unsigned-byte range accepted by the [RPC configuration](https://github.com/anza-xyz/agave/blob/v3.1.8/rpc-client-types/src/config.rs).
This is a read ceiling, not a claim that every version is signable. Pass a lower ceiling explicitly if needed.
Only confirmed/finalized commitment is supported. A missing transaction returns null; RPC errors remain
errors. Forward-compatible decoding cannot guarantee node support, history availability, or compatibility
with future changes to the RPC envelope itself. See [Solana transaction versioning](https://solana.com/developers/cookbook/transactions/versions).

## Binary values

`SolanaBytes` is an immutable binary value with content-based equality and hashing. Its factories and
`toByteArray()` do not share mutable arrays with callers. Text encodings are always explicit:

```kotlin
val bytes = SolanaBytes.fromBytes(byteArrayOf(1, 2, 3))
bytes.toBase58() // "Ldp"
bytes.toBase64() // "AQID"
bytes.toHex()    // "010203" (lowercase, no prefix)
bytes.toString() // "SolanaBytes(size=3)" (diagnostic only)
val decoded = SolanaBytes.fromBase58("Ldp")
val slice = bytes.slice(0, 2) // end index is exclusive
```

`fromHex()` accepts an optional `0x`/`0X` prefix and rejects odd-length input. There is no default JSON
serializer: `Base58BytesSerializer` encodes instruction data, while `Base64TupleBytesSerializer` encodes
return/account data as `["AQID", "base64"]`. Existing byte-array transaction and cryptographic boundaries
remain unchanged; use `toByteArray()` or `copyInto()` to pass bytes across those boundaries.

## Subscriptions and configuration

For private endpoints, supply the WebSocket URL explicitly:

```kotlin
val provider = SolanaProvider.builder("https://your-rpc.example")
    .webSocketUrl("wss://your-rpc.example")
    .defaultCommitment(Commitment.CONFIRMED)
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

The immutable `defaultCommitment` defaults to `FINALIZED` and can be set in the constructor or builder.
Commitment-aware RPC methods and subscriptions accept an optional, non-null `commitment` argument:

```kotlin
val provider = SolanaProvider(client, defaultCommitment = Commitment.CONFIRMED)
provider.getBalance(address) // Uses the provider default
provider.getBalance(address, commitment = Commitment.FINALIZED) // Only this request
provider.subscribeAccount(address, commitment = Commitment.PROCESSED)
```

Overrides do not change the provider default or other requests. Method-specific restrictions still apply:
for example, a `PROCESSED` default cannot be used for `getTransaction` without an explicit supported override.
For submission, `preflightCommitment` controls preflight simulation, not confirmation waiting; it also
defaults to `defaultCommitment`. No Ethereum chain-ID lookup is performed. Construction does not guarantee
that an endpoint is reachable.

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
| Keys, detached signing, verification | `SolanaAddress`, `SolanaSignature`, `SolanaSigner`, `KeypairSigner` |
| PDA / associated token address derivation | `SolanaAddress.createProgramAddress`, `findProgramAddress`, `findAssociatedTokenAddress` |
| Legacy/v0 messages and lookup tables | `SolanaTxLegacy`, `SolanaTxV0`, `AddressLookupTableAccount` |
| Build, sign, import/export transactions | `SolanaTransactionUnsigned`, `SolanaTransactionSigned.Builder`, `SolanaTransactionSigned` |
| Read transactions, including unsupported versions | `getTransaction`, `SolanaRPCTransaction`, `SolanaTxType.Unsupported` |
| SOL, SPL, Token-2022, associated accounts, compute budget | Classes in `io.ethers.solana.instruction` |
| Arbitrary program instructions | `BaseInstruction` |
| Unit conversion / fee estimation | `SolUnit`, `SolanaTransaction.estimateFee` |
| All upstream public RPC methods | `SolanaApi` / `SolanaProvider` |
| Additional WebSocket support | `subscribeAccount`, `subscribeProgram`, `subscribeLogs`, `subscribeSignature`, `subscribeSlot`, `subscribeRoot` |

RPC coverage: `getAccountInfo`, `getBalance`, `getEpochInfo`, `getFeeForMessage`, `getHealth`, `getIdentity`,
`getLatestBlockhash`, `getMinimumBalanceForRentExemption`, `getMultipleAccounts`, `getRecentPrioritizationFees`,
`getSignaturesForAddress`, `getTokenAccountBalance`, `getTokenSupply`, `getTransaction`, `getTransactionCount`, `getVersion`,
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
- Choose `SolanaTxLegacy.compile` or `SolanaTxV0.compile` explicitly. Only v0 exposes lookup-table arguments;
  table contents are supplied by the caller. Automatic table fetching is outside this module's initial RPC coverage.
- Transactions are immutable; signing builders mutate their signature collection. Message changes discard signatures.
  `serialize` and submission require all signatures; partial import/export is explicit. Unknown versions, malformed
  lengths/indices, and invalid signatures are rejected by binary transaction decoders. RPC transaction reads
  are separate and preserve unsupported versions without binary decoding or signature verification.
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
