# ethers-abigen-plugin

Gradle plugin for generating contract wrappers from JSON-ABI files using the `ethers-abigen` module, during the build
process. By default, it reads the ABI files from `src/main/abi` and generates the wrappers
in `generated/source/ethers/main/kotlin`.

File directory structure for `directorySource` is used as package structure,
e.g. `src/main/abi/io/ethers/erc20/ERC20.json` will be generated as `io.ethers.erc20.ERC20`.

The generated code is registered as a source directory on the project's own Kotlin source set. In a Kotlin
Multiplatform project that is `commonMain`, so the wrappers are visible to every target; in a plain JVM or Android
project it is `main`. `directorySource` is a plain path and is unaffected either way - point it wherever you keep
the ABI files.

It supports the following configuration options:

```kotlin
plugins {
    id("io.kriptal.ethers.abigen-plugin") version "2.0.1"
}

ethersAbigen {
    // set by default
    directorySource("src/main/abi")

    // set by default
    outputDir = "generated/source/ethers/main/kotlin"

    // default is empty map
    functionRenames.putAll(
        mapOf(
            "approve" to "approveTokens",
            "transferFrom" to "transferTokensFrom",
        )
    )

    // by default, it supports reading Foundry, Hardhat, and Etherscan artifacts
    abiReader { uri ->
        // read JsonAbi from uri
    }
}
```

### Solana program bindings

The plugin also generates Kotlin bindings for Solana programs from IDL files, using the `ethers-solana-idl` module.
This is configured separately in the `ethersSolanaIdl` extension, and runs as the `ethersSolanaIdl` task. By default,
it reads the IDL files (`*.json`) from `src/main/idl` and generates the bindings in
`generated/source/ethers-solana/main/kotlin`. The generated code is added to the same source set as the contract
wrappers, and requires `ethers-solana` at runtime.

The directory structure is used as package structure, and each program is named after the program name its IDL
declares, e.g. `src/main/idl/com/example/whirlpool.json` will be generated as `com.example.Whirlpool`.

```kotlin
ethersSolanaIdl {
    // set by default; directorySource adds to it, and `sources.set(...)` replaces it
    directorySource("src/main/idl")

    // an additional source, generated into a fixed package regardless of the directory structure
    directorySource("src/main/idl-vendor") {
        packageOverride = "com.example.vendor"
    }

    // set by default
    outputDir = "generated/source/ethers-solana/main/kotlin"

    // use another deployment address than the one the IDL declares, e.g. on devnet or a local validator.
    // Keyed by the program name the IDL declares; naming a program no IDL declares fails the build.
    programAddress("whirlpool", "<base58 address>")

    // by default, it supports reading Anchor IDLs (spec 0.1.x)
    idlReader { input ->
        // read IdlProgram from input
    }
}
```

### Local testing of the plugin

To test the plugin locally, you must first build the shadow jar of the plugin. This can be done by running the following
Gradle command:

```shell
./gradlew :ethers-abigen-plugin:shadow
```

Then, in your project, you must apply the plugin using legacy `buildscript` syntax, and add the shadow jar as a
dependency:

```kts
buildscript {
    repositories {
        mavenLocal()
    }

    dependencies {
        classpath(files("/absolute/path/ethers-abigen-plugin/build/libs/ethers-abigen-plugin-2.0.1-SNAPSHOT.jar"))
    }
}

// apply the plugin by its id
apply(plugin = "io.kriptal.ethers.abigen-plugin")

configure<EthersAbigenExtension> {
    // configure the plugin here
}
```
