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
