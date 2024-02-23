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
