# <h1 align="center"> ethers-kt </h1>

<p style="text-align: center;"> <b>ethers-kt</b> is an async, high-performance Kotlin library for interacting with
EVM-based blockchains. It targets <b>JVM</b> and <b>Android</b> platforms. </p>

## Features:

- **High Performance**: Optimized types and code to minimize the number of allocations and copying.

- **Clean Abstractions**: Intuitive, extensible, and easy to use.

- **Async**: RPC requests are async by default, with helper functions for awaiting results.

- **Safe**: RPC calls return an error object in case of failure, instead of throwing an exception.

- **Smart contract bindings**:
    - Generate type-safe smart contract bindings from JSON-ABI files.
    - Complete support: events (anonymous), custom errors, structs, receive and fallback functions.
    - Custom errors can be resolved from any source (e.g. generated bindings, 4byte directory, etc...).
    - Payable and non-payable functions/constructors.
- **Batch RPC calls**: RPC calls can be batched together in a single call, reducing the number of round trips to the
  server.

## 🚀 Quickstart

> [!NOTE]
> `ethers-kt` API is not yet stable and might be subject to change. It will be stabilized in the 1.0.0 release.

The latest release of the library is available on [TODO](TODO).

It's recommended to define BOM platform dependency to ensure that ethers-kt artifacts are compatible with each other.

```kotlin
dependencies {
    // Define a BOM and its version
    implementation(platform("io.ethers:ethers-bom:0.1.0"))

    // Define any required artifacts without version
    implementation("io.ethers:ethers-abi")
    implementation("io.ethers:ethers-core")
    implementation("io.ethers:ethers-providers")
    implementation("io.ethers:ethers-signers")
}
```

To interact with the chain, you need to create a `Provider` instance, which is the main entry point for all RPC calls.

```kotlin
// create a provider, using a websocket as underlying transport
val provider = Provider(WsClient("<WS_URL>"))

// query the latest block number
val startBlockNum = provider.getBlockNumber().sendAwait().resultOrThrow()
println("Starting at block $startBlockNum")

// subscribe to new blocks, blocking the calling thread. Use "forEachAsync" to stream without blocking the caller.
provider.subscribeNewHeads().sendAwait().resultOrThrow().forEach {
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
- [ethers-js](https://github.com/ethers-io/ethers.js/)

[discord-channel]: https://discord.gg/rx35NzQGSb

[contributing-md]: https://github.com/Kr1ptal/ethers-kt/blob/master/CONTRIBUTING.md

[abi-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-abi/

[abigen-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-abigen/

[abigen-plugin-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-abigen-plugin/

[core-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-core/

[crypto-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-crypto/

[providers-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-providers/

[rlp-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-rlp/

[signers-module]: https://github.com/Kr1ptal/ethers-kt/blob/master/ethers-signers/
