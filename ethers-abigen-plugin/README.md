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

    // encode integer PDA seeds big-endian, for the whole program or one seed of one PDA account (default: little-endian)
    seedByteOrder("escrow", SeedByteOrder.BIG_ENDIAN)
    seedByteOrder("escrow", "vault", "nonce", SeedByteOrder.BIG_ENDIAN)

    // by default, it supports reading Anchor IDLs: spec 0.1.x, and legacy IDLs from before Anchor 0.30
    idlReader { input ->
        // read IdlProgram from input
    }
}
```

Each program derived account of an instruction gets a `find` function on its accounts class, deriving the address
and bump from the account's seeds, e.g. `Escrow.OpenVaultAccounts.findVault(owner, nonce)`. IDLs do not record the
byte order a program encodes integer seeds in. Most programs use little-endian, the default, but some do not, and the
derived address is then wrong unless `seedByteOrder` is set for them.

To read only some fields, each account has a `Fields` object of the fields at a fixed offset with a fixed size, and
`select` reads those fields as one slice of the account data, which the node cuts before sending it:

```kotlin
val price = Clmm.PoolState.select { fields(sqrtPriceX64, tickCurrent) }
val pool = price.fetch(api, poolAddress).sendAwait().unwrap()
val tick = pool.tickCurrent
```

To return a typed value instead of a slice, use `combine` with one to five field descriptors and a constructor or
transformation. The transformation runs after all its fields decode successfully:

```kotlin
data class PoolPrice(val sqrtPriceX64: BigInteger, val tickCurrent: Int)

val price = Clmm.PoolState.select {
    combine(sqrtPriceX64, tickCurrent, ::PoolPrice)
}
val value: PoolPrice = price.fetch(api, poolAddress).sendAwait().unwrap()
```

`combine(...).map { ... }` can further transform the result. Fetch, batch fetch, watch, filter query and filter
subscription all preserve the selected result type. Field decode errors are returned as account decode errors;
exceptions from your transformation propagate. Use `fields(...)` when you need the raw slice and its metadata.

Each selected field reads as a property of the slice, which fails for a field the selection does not include. Java
calls `select` with the descriptors, `PoolState.Fields.tickCurrent`, and reads them with `slice.get(field)`.

A selection can also `watch` an account, and an account `filter` can `query` or `subscribe` with one. A slice has no
discriminator to check the account type by, unless it starts with the first field, so fetching by address checks the
owner only; filters select accounts by discriminator on the node.

Legacy IDLs, from before Anchor 0.30, are converted as Anchor converts them: names become snake_case and
discriminators are derived from them. They often declare no program address, which `programAddress` then provides, and
do not record zero-copy layouts, so their types are read as Borsh, which matches zero-copy accounts without padding.
Struct fields that a legacy IDL names alike, such as Rust's `_padding` and `padding`, are numbered from the first, and
the last keeps the name.

Zero-copy accounts (`#[account(zero_copy)]`) are decoded in the layout the program sees on-chain: packed and unpacked
`#[repr(C)]` layouts, including the padding an unpacked layout places between and after fields, and
`#[repr(transparent)]`. Zero-copy types with the default Rust layout, with both `packed` and `align`, or with 256-bit
integers in an unpacked layout fail generation, as does using a zero-copy type with padding where Borsh encodes it, such
as an instruction argument.

Generic type definitions, with type and const parameters such as `struct RingBuffer<T, const N: usize>`, are
monomorphized: each distinct instantiation the program uses becomes a class of its own with a concrete codec, since a
const argument decides the layout, such as the length of `[T; N]`, and with it the offsets of the fields that follow.
An instantiation is named after its definition and arguments, a const argument after its parameter: `RingBuffer<u64, 3>`
becomes `RingBufferU64N3`, `Pair<u64, Option<Vec<String>>>` becomes `PairU64OptionVecString`, and `[u8; 4]` within an
argument becomes `Array4U8`. Arguments are compared as Rust compares types, so `Pair<Amount, u8>` with `type Amount = u64`
is `PairU64U8`, and generic aliases are inlined where they are used. A generic definition the program never uses is
not generated. Generation fails when an instantiation's name is already declared, when instantiation would never end
(as in `struct List<T> { next: Option<Box<List<Vec<T>>>> }`), when a const argument does not fit its parameter's type,
and when an account or event is itself a generic definition, as the IDL does not say which instantiation it holds.

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
