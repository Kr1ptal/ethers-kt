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

## Kotlin Multiplatform Structure

The project uses Kotlin Multiplatform (KMP) with JVM and Android targets. An intermediate source set `jvmSharedMain` holds all shared code that both targets use.

### Source Set Hierarchy

```
commonMain
└── jvmSharedMain    (all shared JVM/Android code lives here)
    ├── jvmMain      (JVM-only overrides, e.g. secp256k1 JVM JNI)
    └── androidMain  (Android-only overrides, e.g. secp256k1 Android JNI)
```

### Source Directory Layout

- `src/jvmSharedMain/kotlin` — shared code (this is where most code goes)
- `src/jvmSharedTest/kotlin` — shared tests
- `src/jvmMain/kotlin` — JVM-specific code (rarely used)
- `src/androidMain/kotlin` — Android-specific code (rarely used)
- `src/jmh/` — JMH benchmarks (JVM-only, in ethers-core, ethers-abi, ethers-rlp)

### Convention Plugins (buildSrc)

- `project-conventions` — applies KMP, KSP, Kotest, AGP, ktlint to all modules
- `kotlin-project-conventions` — configures JVM/Android targets, compiler options, intermediate source sets
- `maven-publish-conventions` — Maven Central publishing with Dokka javadoc
- `jmh-conventions` — JMH benchmark support via custom JVM compilation
- `static-data-generator` — generates Kotlin source from JSON/TXT data files
- `ktlint-conventions` — code formatting
- `signing-conventions` — GPG signing for publishing

### Special Modules

- **ethers-abigen-plugin** — standalone `kotlin("jvm")` module (not KMP) because `java-gradle-plugin` is incompatible with KMP. Has its own secp256k1 JVM version override and uses `useJUnitPlatform()` with `kotest-runner-junit5`.
- **ethers-bom** — `java-platform` module, not KMP.
- **ethers-crypto** — the only module with platform-specific dependencies (secp256k1 JNI for JVM vs Android).

## Common Commands

### Build and Check

```bash
# Build the entire project
./gradlew build

# Format code and run all checks
./gradlew ktlintFormat check

# Run all tests (kotest for KMP modules + JUnit for abigen-plugin)
./gradlew test

# Run only kotest tests (KMP modules)
./gradlew kotest

# Run tests for a specific module
./gradlew :ethers-core:kotest

# Analyze dependency sizes for a specific module
./gradlew :ethers-core:depsize
```

### Generate Smart Contract Bindings

```bash
# Add the abigen-plugin to your project's build.gradle.kts
plugins {
    id("io.kriptal.ethers.abigen-plugin") version "1.6.0"
}

# Configure the plugin
ethersAbigen {
    directorySource("src/jvmSharedMain/abi")  # Directory with JSON-ABI files
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

The project uses Kotest 6 with the Kotest Gradle plugin for test discovery (no JUnit runner needed for KMP modules). Tests are organized by module:
- Unit tests are located in each module's `src/jvmSharedTest` directory
- The Kotest Gradle plugin runs tests via the `kotest` task (the default `jvmTest` task is disabled)
- The `ethers-abigen-plugin` module is the exception: it uses `useJUnitPlatform()` with `kotest-runner-junit5` since it's a standalone JVM module
- Bug fixes must include a regression test that explicitly exercises the fixed behavior to prevent regressions
- New features must have tests covering the happy path, obvious failures, and edge cases

## Code Style Guidelines

1. **Use regular imports, not fully qualified names**: Always use import statements at the top of the file rather than fully qualified class names inline. The only exception is when there's a naming conflict with another class that's already imported in the same file.

## Web Search Guidelines
1. **ALWAYS FETCH WEBSITES AS MARKDOWN**: Prepend all web requests with `https://markdown.new/<any-url-here>`, where `<any-url-here>` is the URL you want to fetch. This ensures that the content is returned in Markdown format, which is easier to parse and work with programmatically.
   - **Website exclusion** (these cannot be converted): `githubusercontent.com`

## Performance Guidelines

1. **Avoid unnecessary allocations and copies**: Return or reuse existing data structures directly instead of creating copies. If you already have the data in the right format, don't copy it.

2. **Prefer in-place operations**: Modify data structures in-place instead of creating temporary objects. For example, use `copyInto()` for shifting elements within a collection rather than creating intermediate collections.

3. **Use stdlib functions over manual implementations**: Standard library functions like `copyInto()`, `copyOf()`, `sortedBy()`, etc. are optimized and battle-tested. Avoid reimplementing them with manual loops.

## Contribution Guidelines

Before submitting code:
1. Format code with ktlint: `./gradlew ktlintFormat`
2. Run all checks: `./gradlew check`
3. Add tests for new functionality
4. Use conventional commits format for commit messages