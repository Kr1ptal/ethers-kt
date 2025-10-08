# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ethers-kt is an async, high-performance Kotlin library for interacting with EVM-based blockchains. It targets JVM and Android platforms.

## Module Structure

The codebase is divided into several modules, each with a specific purpose:

- **ethers-abi**: ABI primitives with encoding/decoding logic for EVM types
- **ethers-abigen**: Code for generating type-safe smart contract bindings from JSON-ABI files
- **ethers-abigen-plugin**: Gradle plugin for generating smart contract bindings during build
- **ethers-core**: Optimized base types
- **ethers-crypto**: Cryptographic utilities for signing and verifying ECDSA signatures
- **ethers-ens**: Support for ENS names and avatars
- **ethers-providers**: Logic for interacting with JSON-RPC API using HTTP and WebSocket
- **ethers-rlp**: Encoding and decoding of RLP
- **ethers-signers**: Transaction/message signing with multiple key sources
- **examples**: Sample code demonstrating the library's usage
- **logger**: Logging utilities

## Common Commands

### Build and Check

```bash
# Build the entire project
./gradlew build

# Format code and run all checks
./gradlew ktlintFormat check

# Run only tests
./gradlew test

# Run tests for a specific module
./gradlew :ethers-core:test

# Run a single test class
./gradlew :ethers-core:test --tests "io.ethers.core.FastHexTest"

# Generate test coverage report
./gradlew testCodeCoverageReport
# Report will be available at ./build/reports/jacoco/testCodeCoverageReport/html/index.html

# Analyze dependency sizes for a specific module
./gradlew :ethers-core:depsize
```

### Generate Smart Contract Bindings

```bash
# Add the abigen-plugin to your project's build.gradle.kts
plugins {
    id("io.kriptal.ethers.abigen-plugin") version "1.4.4"
}

# Configure the plugin
ethersAbigen {
    directorySource("src/main/abi")  # Directory with JSON-ABI files
    outputDir = "generated/source/ethers/main/kotlin"  # Output directory
}
```

## Code Architecture

### Provider System

The `Provider` class is the main entry point for all RPC calls to the blockchain. It:
- Makes asynchronous RPC requests with helper functions for awaiting results
- Returns error objects instead of throwing exceptions
- Supports both HTTP and WebSocket connections
- Allows batching of RPC calls

Example:
```kotlin
// Create a provider
val provider = Provider.fromUrl("<URL>").unwrap()

// Query blockchain data
val blockNumber = provider.getBlockNumber().sendAwait().unwrap()
```

### Transaction and Type System

- The library provides optimized implementations of Ethereum types
- Transactions are represented with strong typing for different transaction types (Legacy, EIP-1559, etc.)
- Extensive support for ABI encoding/decoding with all EVM types
- RLP encoding/decoding for serialization

### Smart Contract Interaction

The library generates type-safe Kotlin bindings from contract ABIs:
- Full support for functions, events, custom errors, and structs
- Supports payable/non-payable functions
- Handles anonymous events
- Generated code includes builder patterns for method calls

### Signing System

Multiple options for transaction signing:
- Raw private keys
- Mnemonic seed phrases (BIP-39)
- Hardware wallets

## Testing Approach

The project uses Kotest and JUnit for testing. Tests are organized by module:
- Unit tests are located in each module's `src/test` directory
- Integration tests using real contract interactions are in the examples

## Contribution Guidelines

Before submitting code:
1. Format code with ktlint: `./gradlew ktlintFormat`
2. Run all checks: `./gradlew check`
3. Add tests for new functionality
4. Use conventional commits format for commit messages