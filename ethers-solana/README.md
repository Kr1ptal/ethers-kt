# ethers-solana

Solana support for ethers-kt on JVM (Java 11+), Android, iOS, and macOS. Add
`io.kriptal.ethers:ethers-solana` using the matching ethers BOM version. This new module is part of the next
library publication; it is not included in previously released BOMs. Within this repository use
`implementation(project(":ethers-solana"))`.

The module depends on `ethers-rpc` and `ethers-common`, the chain-agnostic layers, and reuses their
HTTP/WebSocket clients, request batching, errors, and coroutine/blocking/future execution APIs. It pulls
in no EVM module, so nothing Ethereum-specific is transitive.

## Kotlin

```kotlin
import io.ethers.solana.instruction.SystemProgram
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
            SystemProgram.transfer(signer.publicKey, recipient, 1_000L),
        ).unwrap()
        val transaction = message.sign(signer)
        val pending = provider.sendTransaction(transaction).send().unwrap()
        println(pending.signature)
        println(pending.confirmation().unwrap().confirmationStatus)
    } finally {
        provider.close()
    }
}
```

`send()` suspends and returns `io.ethers.core.Result`; `unwrap()` explicitly opts into throwing on failure. The
same holds for anything that can fail to build or decode: `compile`, `create`, `deserialize`, `decode`,
`toRequest` and `resolveAccounts` all return a `Result` rather than throwing, so the failure is a value
you can inspect without paying for a stack trace. Signing mirrors the EVM `Signer`: `signMessage` and
`signTransaction` throw, while `trySignMessage` and `trySignTransaction` return a `Result`.
JVM/Android also expose inherited `sendAwait()` and `sendAsync()` members. Constructors and invalid local
transaction inputs fail immediately with `IllegalArgumentException`.

## Java

```java
var provider = SolanaProvider.builder(SolanaCluster.DEVNET).build().unwrap();
try {
    var signer = KeypairSigner.fromSeed(seed);
    var latest = provider.getLatestBlockhash().sendAwait().unwrap().getValue();
    var instruction = SystemProgram.transfer(signer.getPublicKey(), recipient, 1_000L);
    var message = SolanaTxV0.compile(signer.getPublicKey(), latest.getBlockhash(), instruction).unwrap();
    var transaction = message.sign(signer);
    var pending = provider.sendTransaction(transaction).sendAwait().unwrap();
    System.out.println(pending.getSignature());
    System.out.println(pending.awaitConfirmation().unwrap().getConfirmationStatus());
} finally {
    provider.close();
}
```

## Transaction types and signing

`SolanaTransaction` is the common interface. `SolanaTxLegacy`, `SolanaTxV0`, and `SolanaTxV1` implement
`SolanaTransactionUnsigned`; only v0 accepts address lookup tables. `SolanaTransactionSigned` holds the
unsigned payload in `tx` and delegates its common properties. There is no separate message hierarchy.

```kotlin
val unsigned = SolanaTxV0.compile(feePayer, blockhash, instructions).unwrap()
val builder = unsigned.signingBuilder().sign(alice) // SolanaTransactionSigned.Builder
val signed = builder.sign(bob).build().unwrap()     // Result; every slot must hold a valid signature
// Or collect external signatures with addSignature(address, signature), then call build().
// build() reports every empty and every failing slot at once, as SolanaTransactionError.UnsignedSlots.
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
- `SolanaTransactionUnsigned.deserializeMessage()` parses message bytes; `SolanaTransactionCompiled.deserialize()`
  parses an unsigned or fully signed envelope. `SolanaTransactionSigned.deserialize()` rejects incomplete
  envelopes, while `SolanaTransactionSigned.Builder.deserializePartial()` imports a collection for further
  signing. The envelope decoders also have Base64 counterparts.

### V1 transactions

`SolanaTxV1` implements [SIMD-0385](https://github.com/solana-foundation/solana-improvement-documents/blob/main/proposals/0385-transaction-v1.md).
It supports 4096-byte envelopes, at most 12 signatures, 64 inline accounts and 64 instructions, with
no address lookup tables. The first envelope byte is `0x81`; signatures follow the message without a
count prefix. Existing signing, partial-signature exchange, simulation, sending, and fee-query APIs work
with v1 without separate overloads.

```kotlin
val config = SolanaTransactionConfig(
    priorityFee = bigIntegerOf(5_000), // TOTAL lamports, not micro-lamports per compute unit
    computeUnitLimit = 20_000,
    loadedAccountsDataSizeLimit = 65_536,
    heapSize = 65_536,
)
val unsigned = SolanaTxV1.compile(feePayer, blockhash, instructions, config).unwrap()
val signed = unsigned.sign(signer)
val simulation = provider.simulateTransaction(signed).send().unwrap()
```

The config parameter is required to make resource requests explicit. A null field omits that request;
zero is separately representable on the wire. Unset compute/data limits and priority fee mean zero, while
unset heap size means 32 KiB. Heap requests must be multiples of 1 KiB within 32..256 KiB. ComputeBudget
instructions are preserved but do not configure v1 transactions; set the inline config instead. Local
fee estimates use its total priority fee; `getFeeForMessage` remains authoritative. `withConfig` creates
a new unsigned payload requiring new signatures, just like changing the blockhash.

Sending requires the target cluster's `enable_tx_v1` feature gate
(`txv1aq4pp281K9um3tnPgkfX8UqtFT6wcVW3hNezGLL`) to be active. Check with
`solana -u <cluster> feature status <feature-address>` before use. Support in this library does not imply
cluster activation. Send/simulation RPC requests use base64, including envelopes larger than 1232 bytes.
See the [Solana v1 integration guide](https://github.com/solana-foundation/solana-dev-skill/blob/main/skills/solana-dev/references/transactions-v1.md).

## Reading transactions from RPC

Chain-level values and transaction/API families use explicit Solana names: `SolanaSignature`,
`SolanaBlockhash`, and `SolanaRPCTransaction` (with its `SolanaRPC*` components). Node queries return
`SolanaNodeVersion` and `SolanaNodeHealth`. These names coexist with EVM types without import aliases.
Protocol-specific components such as `AccountMeta`, `MessageInstruction`, `InnerInstructions`, and
`LoadedAddresses` keep their shorter names.

```kotlin
val transaction = provider.getTransaction(signature).send().unwrap() // SolanaRPCTransaction?
if (transaction != null) {
    val type = transaction.type // Legacy, V0, V1, or Unsupported(version); never null
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
V1's `message.transactionConfig` is decoded as `SolanaTransactionConfig`, shared with the signable payload;
it is null for legacy/v0. Its nullable fields represent omitted inline requests, not missing decoding support.
Unknown config fields are retained in `otherFields` when reading; constructing a signable v1 payload rejects
these fields rather than silently dropping requests whose binary encoding is not supported.
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

## Composing the provider

`SolanaApi` is the full RPC surface and `SolanaProvider` implements it, the same split as the EVM
`Middleware` and `Provider`. Customize behaviour by delegating to the layer beneath:

```kotlin
class RetryingApi(override val inner: SolanaApi) : SolanaApi by inner {
    override fun getBalance(address: SolanaAddress): RpcRequest<ContextValue<BigInteger>, RpcError> =
        getBalance(address, defaultCommitment)

    override fun getBalance(address: SolanaAddress, commitment: Commitment) =
        inner.getBalance(address, commitment).map { ... }
}
```

Override **every** overload of a call you intend to intercept. Kotlin generates a forwarder for each
member the class does not override, so an un-overridden `getBalance(address)` resolves against `inner`
and reaches `inner`'s two-argument version rather than yours - the interception silently does not
happen. The same applies to the EVM `Middleware`.

Subscriptions are declared on `SolanaApi` and implemented by `SolanaProvider`, so a delegating layer
keeps them. `inner` walks one layer down and `provider` reaches the bottom of the chain, which owns
the WebSocket client; an implementation with no provider beneath it throws when subscribing rather
than silently doing nothing.

## Submitting and confirming

Waiting for the cluster to accept a transaction is its own step, because Solana gives a definite
negative answer that EVM has no equivalent of: a transaction is valid only while its blockhash is,
roughly a minute, after which it can never land.

```kotlin
val pending = provider.sendTransaction(signed).send().unwrap() // PendingSolanaTransaction
pending.signature                                              // what the RPC answered

val status = pending.confirmation().unwrap()                   // suspend; also confirmation(FINALIZED)
if (!status.isSuccess) println("landed but failed: ${status.err}")
```

`sendTransaction` answers with the handle rather than the bare signature, as the EVM
`sendRawTransaction` answers with a `PendingTransaction`. It carries the transaction's own blockhash,
so the wait ends as soon as the answer is known either way rather than running out a timeout. Raw
bytes work too, since the blockhash is recovered from them where they decode. To track a transaction
submitted elsewhere, construct one from its signature:
`PendingSolanaTransaction(signature, provider, blockhash)`.

JVM and Android also get blocking and future variants, like the EVM `PendingInclusion`:

```kotlin
pending.awaitConfirmation()      // blocks the calling thread
pending.confirmationAsync()      // CompletableFuture
```

A transaction that lands and then fails on chain is returned, not raised - it was included, and
`SignatureStatus.err` says why it failed. Only never landing is an error: `Expired` when the blockhash
is gone, `TimedOut` when there was no blockhash to prove it. A submission the node rejects fails at
`send()` as any other RPC call does, so there is no transaction to track.

Submission options mirror the RPC's own:

```kotlin
provider.sendTransaction(signed, SolanaSendConfig(skipPreflight = true, maxRetries = 3))
```

## Request batching

Batching is provided by the shared JSON-RPC layer and is generic over `RpcRequest`, so Solana calls
batch exactly as EVM ones do - there is no Solana-specific API to learn. Unrelated calls leave the
client as a single JSON-RPC array:

```kotlin
val batch = batchRequest(
    provider.getBalance(address),
    provider.getHealth(),
    provider.getVersion(),
).awaitSuspend().unwrap()

println(batch.response1.value) // lamports
```

Each call carries its own result and its own error: a node rejecting one of them leaves the rest
intact, so unwrap them individually when partial success is acceptable.

```kotlin
val batch = batchRequest(provider.getBalance(address), provider.getHealth()).awaitSuspend()
batch.response1.unwrap().value   // succeeded
batch.response2.unwrapError()    // this one did not
```

`BatchRpcRequest` is also available directly when the call count is dynamic, as on the EVM side.

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
Signature streams return `ContextValue<SignatureNotification>`: `context` contains the slot and optional
API version, while `value` is an optional `Received` event followed by a terminal `Status(err)` event.
The stream closes after the status event. Read queued events
even when a stream reports closed. Reconnects restore active subscriptions by default but do not replay missed
events; change this through `RpcClientConfig.resubscribeOnReconnect(false)`. HTTP-only providers report an
unsupported-method error for subscriptions. A provider owns its RPC clients, but the shared Ktor client's
lifecycle remains governed by the existing transport configuration.

RPC convenience overloads live in `SolanaApi` and are inherited by `SolanaProvider`. Java callers can
use the default-commitment overloads through either type, including custom `SolanaApi` implementations.

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

RPC methods retain the node's result shape: contextual responses expose both `context` and `value`,
object responses remain typed objects (for example, `getIdentity` returns `SolanaNodeIdentity`), and
scalar responses stay scalar. Extracting only the value is an explicit caller choice:

```kotlin
val balance = provider.getBalance(address).send().unwrap()
val lamports = balance.value
val slot = balance.context.slot
val apiVersion = balance.context.apiVersion // Optional on older nodes
```

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
large values; transaction instruction types validate unsigned amounts, while RPC calls defer range validation to the node.

## Ported capabilities

Reference: [sol4k a166edd854a7198553fdafe9a5051a400d70b121](https://github.com/sol4k/sol4k/tree/a166edd854a7198553fdafe9a5051a400d70b121).

| Upstream capability | ethers-solana API |
| --- | --- |
| Keys, detached signing, verification | `SolanaAddress`, `SolanaSignature`, `SolanaSigner`, `KeypairSigner` |
| PDA / associated token address derivation | `SolanaAddress.createProgramAddress`, `findProgramAddress`, `findAssociatedTokenAddress` |
| Legacy/v0 messages and lookup tables | `SolanaTxLegacy`, `SolanaTxV0`, `AddressLookupTableAccount` |
| Build, sign, import/export transactions | `SolanaTransactionUnsigned`, `SolanaTransactionSigned.Builder`, `SolanaTransactionSigned` |
| Read transactions, including unsupported versions | `getTransaction`, `SolanaRPCTransaction`, `SolanaTxType.Unsupported` |
| SOL, SPL, Token-2022, associated accounts, compute budget | `SystemProgram`, `TokenProgram`, `Token2022Program`, `AssociatedTokenProgram`, `ComputeBudgetProgram` |
| Arbitrary program instructions | `Instruction(programId, keys, data)` |
| Build a transaction across versions | `SolanaTransactionRequest`, `compileLegacy`/`compileV0`/`compileV1` |
| Shrink a v0 transaction with lookup tables | `getAddressLookupTable`, `AddressLookupTableAccount.decode`, automatic table selection in `compileV0` |
| Read token balances without a call per account | `getTokenAccountsByOwner`, `TokenAccount.decode` |
| Read a mint's supply, decimals and authorities | `TokenMint.decode` |
| Simulate with options, or fill and compile a request | `simulateTransaction(request, ...)`, `SolanaSimulationConfig`, `fillTransaction` |
| Rebuild a transaction from an RPC response | `SolanaRPCMessage.toTransaction`, `SolanaRPCTransaction.toSignedTransaction` |
| Compile or decode without throwing | `compile`, `deserialize`, `deserializeMessage` — every one returns `Result<_, SolanaTransactionError>` |
| Unit conversion / fee estimation | `SolUnit`, `SolanaTransaction.estimateFee` |
| Read a transaction's compute budget, any version | `computeUnitLimit`, `computeUnitPrice`, `priorityFee`, `heapSize`, `loadedAccountsDataSizeLimit` |
| Read any transaction the same way, built or fetched | `SolanaTransaction`, implemented by `SolanaTransactionCompiled` and `SolanaRPCTransaction` |
| Turn any transaction back into an editable request | `SolanaTransaction.toRequest`, `SolanaApi.decompileTransaction` |
| Resolve a transaction's account indices to addresses and flags | `SolanaTransaction.resolveAccounts` |
| Every documented JSON-RPC method and subscription | `SolanaApi` / `SolanaProvider` |
| Submit and wait for confirmation | `sendTransaction`, `PendingSolanaTransaction`, `SignatureStatus`, `SolanaSendConfig` |
| Read program and token accounts | `getProgramAccounts`, `getTokenAccountsByOwner`, `ProgramAccount` |
| Cluster, validators and leader schedule | `getClusterNodes`, `getVoteAccounts`, `getLeaderSchedule`, `getSlotLeaders`, `getEpochSchedule` |
| Supply, inflation and staking | `getSupply`, `getInflationRate`, `getInflationReward`, `getStakeMinimumDelegation` |
| Read slots and blocks | `getSlot`, `getBlockHeight`, `getBlocks`, `getBlock`, `SolanaBlock` |
| Account, nonce and token instructions | `SystemProgram`, `TokenProgram`, `Token2022Program` |
| Batch unrelated calls into one round trip | `batchRequest`, `BatchRpcRequest` (shared with EVM) |
| Additional WebSocket support | `subscribeAccount`, `subscribeProgram`, `subscribeLogs`, `subscribeSignature`, `subscribeSlot`, `subscribeRoot`, `subscribeBlock`, `subscribeSlotsUpdates`, `subscribeVote` |

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
- Choose `SolanaTxLegacy.compile`, `SolanaTxV0.compile`, or `SolanaTxV1.compile` explicitly. Only v0 exposes lookup-table arguments;
  table contents are supplied by the caller. Automatic table fetching is outside this module's initial RPC coverage.
- Transactions are immutable; signing builders mutate their signature collection. Message changes discard signatures.
  Signed transaction construction requires all signatures; partial import/export is explicit. Unknown versions, malformed
  lengths/indices, and invalid signatures are rejected by binary transaction decoders. RPC transaction reads
  are separate and preserve unsupported versions without binary decoding or signature verification.
- RPC calls forward parameters without local validation. Raw transaction bytes, numeric ranges, history options,
  and subscription filters are validated by the node; rejection is returned as an `RpcError`, not a local
  argument exception. Typed transaction construction still enforces version-specific envelope sizes and signatures.
- PDA seeds may be arbitrary bytes. Seed bounds and bump zero are handled. Compute-unit-limit instructions encode
  their payload as u32 (correcting the upstream u64 encoding).
- Legacy/v0 offline fee estimation requires an explicit compute-unit limit when a nonzero unit price is set. V1
  instead uses the total lamport priority fee in its config. Use
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

The committed [live transaction corpus](src/commonTest/resources/transactions/README.md) adds 200 legacy and
200 v0 mainnet transactions with paired RPC JSON and binary bytes. Common tests verify signatures, exact
binary roundtrips, reconstruction from RPC fields, lossless JSON values and metadata indices. The requested
200 live v1 samples remain pending: bounded public testnet/devnet discovery found none. Constructed v1
fixtures are tested separately and are not counted toward that live corpus.

An optional local-validator test creates ephemeral test accounts and uses the local faucet. Start your own
`solana-test-validator` and opt in (loopback endpoints only):

```sh
SOLANA_VALIDATOR_HTTP=http://127.0.0.1:8899 SOLANA_VALIDATOR_WS=ws://127.0.0.1:8900 \
  ./gradlew :ethers-solana:jvmKotest --rerun-tasks
```

With a v1-enabled validator (Agave 4.2+), also set `SOLANA_VALIDATOR_V1=true` to run the v1 integration
test. It simulates and submits a transaction larger than 1232 bytes, checks its fee, and reads back its
typed inline config and recipient balance. This test requires only the loopback HTTP endpoint.
Common v1 tests independently check the exact SIMD wire layout and an Ed25519 signature generated by
Node crypto, all config masks, partial signatures, malformed inputs, and the 4096-byte envelope boundary.

See [NOTICE](NOTICE) for upstream provenance and licensing.
