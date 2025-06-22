# <h1 align="center"> ethers-kt </h1>

<p style="text-align: center;"> <b>ethers-kt</b> is an async, high-performance Kotlin library for interacting with
EVM-based blockchains. It targets <b>JVM</b> and <b>Android</b> platforms. </p>

## Features:

- **High Performance**: Optimized types and code to minimize the number of allocations and copying.

- **Clean Abstractions**: Intuitive, extensible, and easy to use.

- **Async**: RPC requests are async by default, with helper functions for awaiting results.

- **Safe**: RPC calls return an error object in case of failure, instead of throwing an exception.

- **Smart contract bindings**:
    - Generate type-safe smart contract bindings from JSON-ABI files or from Foundry projects.
    - Complete support: events (anonymous), custom errors, structs, receive and fallback functions.
    - Custom errors can be resolved from any source (e.g. generated bindings, 4byte directory, etc...).
    - Payable and non-payable functions/constructors.
- **Batch RPC calls**:
    - **Batch JSON-RPC**: RPC calls can be batched together in a single request, reducing the number of round trips to
      the server.
    - **Multicall**: Aggregate multiple smart contract calls into a single call via `Multicall3` contract.

## 🚀 Quickstart

All releases are published to Maven Central. Changelog of each release can be found
under [Releases](https://github.com/Kr1ptal/ethers-kt/releases).

It's recommended to define BOM platform dependency to ensure that ethers-kt artifacts are compatible with each other.

```kotlin
plugins {
    id("io.kriptal.ethers.abigen-plugin") version "1.3.2"
}

// default values
ethersAbigen {
    directorySource("src/main/abi")
    outputDir = "generated/source/ethers/main/kotlin"
}

// Define a maven repository where the library is published
repositories {
    mavenCentral()

    // for snapshot versions, use the following repository
    //maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    // Define a BOM and its version
    implementation(platform("io.kriptal.ethers:ethers-bom:1.3.2"))

    // Define any required artifacts without version
    implementation("io.kriptal.ethers:ethers-abi")
    implementation("io.kriptal.ethers:ethers-core")
    implementation("io.kriptal.ethers:ethers-providers")
    implementation("io.kriptal.ethers:ethers-signers")
}
```

To interact with the chain, you need to create a `Provider` instance, which is the main entry point for all RPC calls.

```kotlin
// create a provider, using a websocket as underlying transport
val provider = Provider.fromUrl("<WS_URL>").unwrap()

// query the latest block number
val startBlockNum = provider.getBlockNumber().sendAwait().unwrap()
println("Starting at block $startBlockNum")

// subscribe to new blocks, blocking the calling thread. Use "forEachAsync" to stream without blocking the caller.
provider.subscribeNewHeads().sendAwait().unwrap().forEach {
    println("New Block: ${it.number}, ${it.number - startBlockNum} blocks since start")
}
```

## 📦 Structure

Code is structured into multiple modules, each categorized by its purpose. Below is a brief overview of each module. For
a more in-depth explanation, please refer to the individual module's *README.md*.

- **[abi][abi-module]**: Provides ABI primitives with encoding/decoding logic for all types supported by the EVM.

- **[abigen][abigen-module]**: Code for generating type-safe smart contract bindings from JSON-ABI files.

- **[abigen-plugin][abigen-plugin-module]**: Gradle plugin for generating smart contract bindings during the build
  process.

- **[core][core-module]**: Contains optimized base types.

- **[crypto][crypto-module]**: Includes cryptographic utilities for signing and verifying **ECDSA** signatures on the
  **secp256k1** curve.

- **[ens][ens-module]**: Full support for **ENS** names and avatars, with wildcard resolution and offchain resolution
  via CCIP-Read.

- **[providers][providers-module]**: Logic for interacting with **JSON-RPC API** using various transports (**HTTP**,
  **WebSocket**).

- **[rlp][rlp-module]**: Handles the encoding and decoding of RLP.

- **[signers][signers-module]**: Code for transaction/message signing, allowing multiple signing key
  sources: `hardware wallet`, `mnemonic` or `raw private key`.

## 🙋‍♂️ Contributing

We are happy to have you here! Opportunities to get involved with **ethers-kt** are open to everyone, no matter your
level of expertise. Please check the [CONTRIBUTING.md][contributing-md] to get started. To chat with fellow
contributors, join our [Discord channel][discord-channel].

Before submitting a PR make sure to format the code and run all checks using the following command:

```shell
./gradlew ktlintFormat check
```

## Need help❓

First, check if any of the README files under each module answers your question. If the answer is not there please don't
open an issue.

Instead, you can:

- ask on [Discord][discord-channel].
- open a thread under [Discussions](https://github.com/Kr1ptal/ethers-kt/discussions).

## ❤️ Acknowledgements

This library has been made possible thanks to the inspiration provided by the following projects:

- [web3j](https://github.com/web3j/web3j)
- [ethers-rs](https://github.com/gakonst/ethers-rs)
- [reth](https://github.com/paradigmxyz/reth)
- [ethers-js](https://github.com/ethers-io/ethers.js/)

----------------------

<p style="text-align: center;"> <b>Proof of Work:</b> <a href="https://etherscan.io/tx/0x6b0f9ff6f53ec22d8d2d92b1beb193cdc523628951b5c81779fabce9f51db351">0x6b0f9ff6f53ec22d8d2d92b1beb193cdc523628951b5c81779fabce9f51db351</a> </p>

[discord-channel]: https://discord.gg/rx35NzQGSb

[contributing-md]: https://github.com/Kr1ptal/ethers-kt/blob/master/CONTRIBUTING.md

[abi-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-abi/

[abigen-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-abigen/

[abigen-plugin-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-abigen-plugin/

[core-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-core/

[crypto-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-crypto/

[ens-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-ens/

[providers-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-providers/

[rlp-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-rlp/

[signers-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-signers/
