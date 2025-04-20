# ethers-abigen-plugin

Gradle plugin for generating contract wrappers from JSON-ABI files using the `ethers-abigen` module, during the build
process. By default, it reads the ABI files from `src/main/abi` and generates the wrappers
in `generated/source/ethers/main/kotlin`.

File directory structure for `directorySource` is used as package structure,
e.g. `src/main/abi/io/ethers/erc20/ERC20.json` will be generated as `io.ethers.erc20.ERC20`.

It supports the following configuration options:

```kotlin
plugins {
    id("io.kriptal.ethers.abigen-plugin") version "1.3.2"
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
        classpath(files("/absolute/path/ethers-abigen-plugin/build/libs/ethers-abigen-plugin-1.3.2-SNAPSHOT.jar"))
    }
}

// apply the plugin by its id
apply(plugin = "io.kriptal.ethers.abigen-plugin")

configure<EthersAbigenExtension> {
    // configure the plugin here
}
```
