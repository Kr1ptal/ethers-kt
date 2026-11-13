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

Fields of fixed-size structs and elements of fixed-size arrays have descriptors too, reached as paths through their
field. Their offsets are the ones the codec reads them at, after the discriminator and including the padding a
zero-copy layout places before them. A descriptor of a struct or an array reads the whole value, and a slice that
selects it can also read the fields and elements within it:

```kotlin
val rewards = Clmm.PoolState.select {
    combine(rewardInfos[0].rewardState, rewardInfos[1].tokenMint) { state, mint -> state to mint }
}

val slice = Clmm.PoolState.select { fields(rewardInfos) }.fetch(api, poolAddress).sendAwait().unwrap()
val mint = slice[Clmm.PoolState.Fields.rewardInfos[2].tokenMint]
```

A struct field named like a member of a descriptor (`name`, `offset`, `size`, `read`, `write`) takes a `Field` suffix,
such as `slots[0].nameField`. An index outside the array throws `IndexOutOfBoundsException`, and descriptors with the
same path are equal, so selecting one twice selects it once.

A filter can compare any descriptor, nested or not, with `field(descriptor, value)`, which compares the bytes the
value encodes to at the descriptor's offset. A value with padding in its zero-copy layout is compared with zeros there.

```kotlin
val pools = Clmm.PoolState.filter(api).field(Clmm.PoolState.Fields.rewardInfos[0].tokenMint, mint).query()
```

Descriptors exist only where the offset and size are the same in every account. A field whose size varies, such as a
`Vec`, a `String`, an `Option` or an enum with data, has none, and nor does anything after it: those offsets depend on
the data of each account, so they are intentionally not guessed. Fields that take no bytes, such as an empty struct,
have nothing to select either. Each account's `Fields` documents which of its fields have no descriptor and why.

Not yet supported: the fields of a struct whose size varies have no descriptors, even those before its first
variable-size field, whose offsets are fixed.

Legacy IDLs, from before Anchor 0.30, are converted as Anchor converts them: names become snake_case and
discriminators are derived from them. They often declare no program address, which `programAddress` then provides, and
do not record zero-copy layouts, so their types are read as Borsh, which matches zero-copy accounts without padding.
Struct fields that a legacy IDL names alike, such as Rust's `_padding` and `padding`, are numbered from the first, and
the last keeps the name.

Zero-copy accounts (`#[account(zero_copy)]`, IDL `serialization` `bytemuck` or `bytemuckunsafe`) are decoded in the
layout the program sees on-chain, on Solana's SBF target. Only layouts the IDL fully describes are generated; the rest
fail generation with an error that says why:

| IDL `repr`                                       | Generated | Layout                                                                                             |
|--------------------------------------------------|-----------|----------------------------------------------------------------------------------------------------|
| `{"kind": "c"}`, optionally with `align`         | yes       | Fields in order, each padded to its alignment; the size padded to the struct's alignment.          |
| `{"kind": "c", "packed": true}`                  | yes       | Fields back to back, no padding, aligned to 1.                                                     |
| `{"kind": "transparent"}`                        | yes       | Its one field of non-zero size or alignment above 1.                                               |
| absent or `{"kind": "rust", ...}`                | no        | Rust does not specify the field order of its default layout, even when packed.                     |
| `packed` and `align` together, invalid `align`   | no        | Rust rejects these layouts.                                                                        |

Primitive alignments follow the SBF data layout (`e-m:e-p:64:64-i64:64-n32:64-S128`): integers are aligned to their
size up to 8 bytes, and `u128`/`i128` to 8 bytes, unlike the 16 bytes of x86-64 since Rust 1.77. Public keys are
aligned to 1. 256-bit integers have no Rust primitive, so their alignment is unknown. They are generated only where no
padding can depend on it: in packed layouts, as the field of a transparent one, and in `bytemuck` C layouts. A
`bytemuck` IDL type stands for `derive(Pod)`, which only compiles without padding, so a `bytemuck` layout that would
need padding is rejected as not matching the program.

The Rust layout includes `#[zero_copy(unsafe)]` without a `#[repr]` of its own, which Anchor declares
`#[repr(packed)]`. Also rejected, by design, are:

- zero-copy enums and enum fields of zero-copy types. Anchor records `#[repr(u8)]`, `#[repr(u16)]` and others all as
  `{"kind": "rust"}`, and `#[repr(C, u8)]` as `{"kind": "c"}`, so the IDL never states the width of the discriminant. A
  plain `#[repr(C)]` discriminant takes at least 4 bytes on-chain.
- `custom` serialization, whose layout is defined by the program's own code.
- a zero-copy type with padding where Borsh encodes it, such as an instruction argument.

For a layout the IDL cannot describe, bind the type with a handwritten `SolanaCodec` instead.

Anchor records both `#[repr(packed)]` and `#[repr(packed(N))]` as `"packed": true`. The generator reads that as
`packed`, that is `packed(1)`, the form programs such as Raydium's CLMM use. A type declared
`#[repr(C, packed(N))]` with N above 1 and a field aligned above 1 is laid out differently from what the IDL implies,
and needs a handwritten codec.

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
