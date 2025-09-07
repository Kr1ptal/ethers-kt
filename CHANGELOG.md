# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

<!--
Release version and date:
## [MAJOR.MINOR.PATCH](link_to_tag)- <yyyy-mm-dd>

Sections:
### Added = New features.
### Changed = Changes in existing functionality.
### Deprecated = Soon-to-be removed features.
### Removed = Removed features.
### Fixed = Bug fixes.
### Security = Security patches.
-->

## [1.4.0] - 2025-09-07

### Bug Fixes

- Gradle plugin-publish version
- Exclude ArbOS changes from state diff
- Handle more `RpcError#isMethodNotFound` cases (#240)
- [**breaking**] Lowercase ABI class names and flat package structure generation (#245)
- Use env variables for publishing via jreleaser

### Features

- Use `jreleaser` for publishing to maven central (#251)
- Support out-of-order batch responses (#250)
- Add `CLAUDE.md` rules
- Implement `TxSetCode` (#252)
- [**breaking**] Replace `SubscriptionStream` with `channels-kt` library (#211)
- [**breaking**] Abigen uses `List` instead of `Array` (#263)
- Use kmp-friendly logging facade (#270)
- Make "FastHex" decoding functions safe by default (#271)
- Add fromHex/fromHexUnsafe helper functions to low-level types (#272)
- Optimize struct ABI references and add typed StructFactory.abi (#276)
- [**breaking**] Typed data signing (#283)
- Specify annotation targets explicitly (#288)
- Gradle config cache (#289)

### Miscellaneous Tasks

- Fix tagged version concurrency group

### Refactor

- [**breaking**] `GcpSigner` constructor no longer does an external call (#264)
- Simplify `HttpClient` response body reading

## [1.3.2] - 2025-04-20

### Bug Fixes

- Support more Kotlin SourceSets in EthersAbigenPlugin.kt

## [1.3.1] - 2025-04-08

### Bug Fixes

- Replace `readAllBytes` with `readBytes` for android compatibility
- `JsonAbi` decoding of functions without explicit output entry in the JSON
- `JsonAbiReader` to work in gradle plugin

### Features

- Add `tryReadAbi` method that returns all read failures

## [1.3.0] - 2025-02-26

### Bug Fixes

- Handle additional error cases in Http/WsClients (#179)
- Add chainId when manually filling unsigned tx
- Close our websocket connection if other side closes it
- Properly timeout pending ws requests on close
- Explicitly define arrayOf type to avoid kotlin compiler warnings
- Handle hardhat error in `fillTransaction`
- Normalize Windows file paths (#217)

### Features

- Include HTTP response code in error message in `HttpClient`
- Add additional `Bytes` overloads where `ByteArray` can be used
- Change dependency visibility to `api` for submodules (#183)
- Add `CallFrame#getAllCallLogs` function
- Add support for creating random `PrivateKeySigner` (#198)
- Properly unsubscribe `WsClient` streams and expire all remaining requests on client close (#206)
- [**breaking**] Try to use virtual threads if present and enabled (#209)

### Miscellaneous Tasks

- Upgrade gradle to v8.10.2
- Bump gradle setup GH action

### Performance

- Re-check the event queue while holding the lock in `BlockingSubscriptionStream`

### Misc

- Move to v1.2.1-SNAPSHOT
- Remove new line suffix in `HttpClient` response when logging it
- Update license year
- Update gradle to v8.12.1
- Fix IDE inspections

## [1.2.1] - 2024-09-16

### Bug Fixes

- Lazy-evaluate all input properties of `FoundrySourceProvider` (#167)
- Return error instead of throwing exception in `AnvilBuilder#spawn` method

### Features

- Make `Multicall3.Aggregatable` implement `IntoCallRequest`

## [1.2.0] - 2024-09-06

### Bug Fixes

- [**breaking**] Change `estimateGas` to return gas as `Long` instead of `BigInteger`
- Handle contract reverts even if JSON-RPC returns wrong error code
- Fix abigen of overloaded functions with same java types (#154)
- Convert `addresses` array content to string in `LogFilter#toString`

### Features

- Manually fill missing transaction details if `eth_fillTransaction` is not supported (#152)
- Add docs to generated contract bindings (#155)
- Add support for auto-generating bindings for foundry projects (#156)
- Add a default value for `foundryRoot` option in `FoundrySourceProvider`
- Add function selector suffix to generated duplicate function names
- Allow passing `BlockId` parameter to `LogFilter#atBlock` function

### Miscellaneous Tasks

- Upgrade gradle to v8.9

### Refactor

- Simplify `CallRequest#toUnsignedTransactionOrNull`

### Misc

- Replace deprecated kotlin compiler flags
- Set correct current version in README.md
- Update ethers-kt plugin version to 1.1.0 in `examples`

## [1.1.0] - 2024-07-18

### Bug Fixes

- Add word and checksum validation to `MnemonicCode` initialization
- Support deserializing `RpcError#data` as any type
- [**breaking**] Change `RpcError#data` to `JsonNode` type
- [**breaking**] Change `getBlockHeader`/`getBlockWithTransactions`/`getBlockWithHashes`/`getUncleBlockHeader` to return `Optional`-lly wrapped value
- Serialize `CallRequest#data` field as `data` instead of `input` (#147)

### Features

- Add support for GCP KMS Signer (#141)
- Implement `anvil` bindings and `AnvilProvider` (#143)
- Add support for parsing raw abi function signatures with argument names (#149)

### Miscellaneous Tasks

- Change version to snapshot
- Add additional documentation to `AccountOverride` and `BlockOverride`

### Misc

- Add depsize gradle task
- Fix `watch*` methods docstrings
- [**breaking**] Rename `watchNewBlocks` to `watchNewBlockHashes`

## [1.0.0] - 2024-06-30

### Bug Fixes

- [**breaking**] Standardize transactionIndex field type to Int
- Handle hyphens in module names for loaderPrefix in EthersAbigenTask
- Set correct formula for Uniswap v2 fee derivation in docstring
- Fix potential deadlocks during reconnection in `WsClient`
- Use correct name and type for `yParity` field when decoding `RPCTransaction`
- Remove gas tip/fee cap validation from `TxBlob` and `TxDynamicFee`
- Make signature values in `RPCTransaction` optional
- Add missing closed connection condition signaler to `WsClient#onFailure`
- Make `Block#mixHash` nullable
- Encode `blockCount` param to hex in `getFeeHistory` RPC call
- Update no wildcard ENS test case
- `AnonymousEventFilter#topic0` filter, add convenience methods for `BigInteger`/`Address` topics

### Features

- Document `FeeHistory` and add additional helper methods
- Add support for using `BlockId.Name` when fetching `FeeHistory`
- [**breaking**] Implement `StateOverride` as a safer alternative to working with `Map<Address, AccountOverride>` directly (#106)
- Add option to set initial map size for `StateOverride`
- Support nullable types in `StateOverride` when merging/applying changes
- `StateOverride#takeChanges` function
- Add cloning constructor to `CallRequest`
- Add `TransactionReceipt#getEffectiveGasTip` method
- Add `ExecutionRevertedError` contract error
- Implement equals/hashCode/toString for StateOverride along with apply/take methods for single element
- Add UniswapV2FeeFinder to examples
- Add txIndex to `TracerConfig`
- Add support for defining RPC request headers
- Add `Multicall` example (#111)
- Add documentation for rewardPercentiles value range. (#113)
- Add block transaction simulation with provider.traceCall and IntoCallRequest to examples (#112)
- Suppress IDE inspections in generated contract wrappers (#114)
- `EventFactory#isLogValid` and additional utils for converting logs to events
- Add `blockTimestamp` field to `Log` type
- Ignore unknown JSON fields by default on all types
- Add 10sec ping interval to default OkHttpClient in JsonClientConfig
- Implement equals/hashCode for `Result` variants
- Enable `BatchRpcRequest` `batchSent` Status Modification (#122)
- [**breaking**] Make `BlockingSubscriptionStream` more configurable and reduce gc pressure when waiting for new events
- Make SubscriptionStream#forEachAsync return itself
- Support deserialization of other fields in `CallTracer`. (#125)
- Add constructor for hex strings to Signature (#121)
- Implement `PrivateKeySigner#toString`
- Add "type" field to CallTracer
- Add Result#isNullOrFailure extension function
- Implement equals/hashCode for crypto and signer types
- Add `onSuccess`/`onFailure` operators to `RpcRequest`/`RpcSubscribe`
- Implement equals/hashCode for `BlockOverride` and `CallRequest`
- Add additional conversion methods to `EthUnit` for `Int`/`Long`/`Double` types
- Add support for EIP-55 and EIP-1191 checksummed addresses
- Add BigInteger constructor to Hash type

### Miscellaneous Tasks

- Inspection fixes
- Replace usage of deprecated Jackson methods
- Update gradle GH action to gradle/actions/setup-gradle@v3 (#115)
- Upgrade gradle to v8.7

### Performance

- Use concurrent queues from jctools for better performance in `WsClient`
- Add `toString` cache for `Address`/`Hash`/`Bytes` types (#124)
- [**breaking**] Implement `RlpEncoder` pre-sizing for all `RlpEncodable` types and list encodings (#137)

### Refactor

- Merge `JsonPubSubClient` into `JsonRpcClient` (#116)
- [**breaking**] Make `access list` property immutable on unsigned txs, replace `rlpEncodeFields` with `rlpEncodeEnveloped` (#136)

### Misc

- Release abigen-plugin as a fat jar
- Fix docstring typo for ExecutionRevertedError
- Fix RpcClientConfig docstrings
- Add `out` generic bound to `AbiType.Tuple#factory` input type array
- Fix TxDynamicFeeTest
- Document which fields from `Aggregatable` are used for aggregate call
- Minor readme update (#138)

## [0.5.0] - 2024-03-23

### Bug Fixes

- Add missing list append in `List<CompletableFuture<T>>.await()` extension function (#89)
- Don't include logs from failed CallTracer frames (#88)
- Handle empty ByteArray in RlpDecodable
- Make Provider#fromUrl actually return failure instead of throwing
- Remove deprecated "packages" parameter from log4j2.xml
- Fix decoding arrays of type Bytes/FixedBytes in AbiCodec (#102)
- [**breaking**] Change `ConstructorCall#call` result to include all state changes made by contract deployment (#90)

### Features

- Add function to create signature from 65 bytes long array
- Validate BigInteger BlockOverride fields and allow assigning back to null
- Add "flatten" function to CallTracer and clarify "getAllLogs" docstring
- Throw an exception if multiple tracers of the same type are added to the same `MuxTracer`
- Option to provide custom OkHttpClient instance to Provider#fromUrl
- Replace Stopwatch with kotlin TimeSource
- [**breaking**] Move `chainId` fetching from constructor to `Provider#fromUrl` (#97)
- Return a failure response instead of throwing if pub-sub not available in `Provider#subscribe*` methods (#98)
- Make BatchRpcRequest#sendAsync return "false" future if no requests added
- Simplify Result generics
- Add Bytes#copyOfRange function and some additional docs
- [**breaking**] Simplify and improve ergonomics of `LogFilter` (#92)
- [**breaking**] Add RpcClientConfig for configuring JsonRpcClient implementations (#91)
- [**breaking**] Generify `AbiType` (#104)

### Miscellaneous Tasks

- Fix Middleware code example in docs
- Simplify applying of abigen plugin in consuming projects by releasing it as a fat jar

### Performance

- 40% reduced allocation rate when decoding BigInteger/String types

### Refactor

- Split Tracer into two interfaces for improved API without the need for throwing exceptions in Mux/Struct tracers
- [**breaking**] Treat failed transaction as successful inclusion result in `PendingInclusion` (#99)

## [0.4.0] - 2024-02-23

### Bug Fixes

- Rlp encoding of BigInteger where its bytearray has exactly one non-zero element but is larger than RLP_STRING_SHORT
- RLP-encoding of ByteArray with a single, 0 element
- [**breaking**] Make encoding/decoding RLP lists non-ambiguous by returning empty List instead of null (#65)
- Gracefully handle unsuccessful http responses (close #68)
- [**breaking**] Correctly abi-generate complex constant names. Might break some incorrectly generated AbiFunction
  constants
- Set missing generic result type for `EthApi#createAccessList` function

### Features

- Add `createAccessList` function to `ReadWriteContractCall`
- Add helper method to initialize Provider with correct client directly from url
- Add `Signature#toByteArray` function
- Add additional `ContractCall#call/traceCall` overloads (#67)
- Add `baseFeePerBlobGas` and `blobGasUsedRatio` to `FeeHistory` type
- Add `eth_blobBaseFee` RPC call
- Add support for aggregating contract calls into `Multicall3` (#70)
- Add `GasUtils` for working with eip1559 gas price mechanics
- Add `eth_callMany`/`debug_traceCallMany` RPC calls (#75)
- Add `IntoCallRequest` interface and use it in provider function arguments (#76)
- Add additional `call/traceCall` function overloads with block number/hash
- Add function to `PrestateTracer.Result` to create state override from state diff (#78)
- Add support for converting `CallTracer#CallLog` into `Log`, and returning all logs from a `CallFrame` (#79)
- Add `Web3Api`

### Miscellaneous Tasks

- Temporarily disable jacoco coverage reports
- Move `TxBlob` test RLP to a file, so it does not clutter the editor
- Update README.md to use the new Result type functions

### Refactor

- [**breaking**] Make `value` property private and replace it with asByteArray/toByteArray functions for
  Address/Bloom/Bytes/Hash types
- [**breaking**] Change `selector` property type of `AbiFunction` from `ByteArray` to `Bytes`
- [**breaking**] Rename `Bytes#ZERO` to `Bytes#EMPTY`

## [0.3.0] - 2024-01-29

### Bug Fixes

- TracerConfig state/block overrides field name
- Don't override kotlin compiler arg "-opt-in" in tests

### Features

- Full support for **ENS** names and avatars, with wildcard resolution and offchain resolution
  via CCIP-Read (#59)
- Add additional conversion methods to `EthUnit`
- Improve functional operators on `RpcRequest`/`RpcResponse` (#60)
- New Result type (#62)
- [**breaking**] Migrate the project to use new `Result` type (#63)

### Miscellaneous Tasks

- Update ktlint-gradle plugin to v12.1.0
- Add ens section to README.md
- Fix gradle unit-test report aggregation and GH actions upload
- Join lint and test runs via gradle check

## [0.2.0] - 2024-01-02

### Bug Fixes

- Project artifact publishing and move to maven central (#1)
- Ethers-abi README file (#13)
- Ethers-providers README file (#14)
- Correctly return mapped rpc requests (#25)
- Create `eth_call` params array with minimal amount of values (#28)
- Add resolver error factories in generated wrappers via companion object init (#41)
- `eth_call` execution revert decoding (#42)
- Race condition when awaiting new WS events
- Clear previous task outputs if its inputs have changed (#49)
- Normalize reserved field and parameter names

### Features

- Implement support for bip32, bip39 and mnemonic key source (#2)
- Add usage examples (#24)
- Function to unwrap all batch responses (#30)
- Wei units conversion (#29)
- Force-load all generated contract wrapper classes on first access (#44)
- Add support for generating random mnemonics using SecureRandom
- Add support for eip-4844 blob transactions (#3)
- Add support for receiving unsupported tx types (#34)
- Add support for eip-4788 `parentBeaconBlockRoot` header field (#43)
- Add traceBlock RPC call

### Miscellaneous Tasks

- Update readme files
- Add abigen plugin to quickstart gradle example
- Upgrade gradle to v8.5 (#38)

### Refactor

- Convert "invoke" functions back to constructors in PrivateKeySigner (#35)

## [0.1.0] - 2023-11-01

Initial release
