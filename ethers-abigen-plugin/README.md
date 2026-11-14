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

Zero-copy accounts (`#[account(zero_copy)]`) are decoded in the layout the program sees on-chain: packed and unpacked
`#[repr(C)]` layouts, including the padding an unpacked layout places between and after fields, and
`#[repr(transparent)]`. Zero-copy types with the default Rust layout, with both `packed` and `align`, or with 256-bit
integers in an unpacked layout fail generation, as does using a zero-copy type with padding where Borsh encodes it, such
as an instruction argument.

#### Resolving instruction accounts

The instruction builders are pure: they take every account. To fill in the accounts the IDL says how to find, each
accounts class has a `Partial` class, whose accounts may all be left out, and `resolve`, which returns an
`RpcRequest` of the full accounts class. Resolving only happens when you call it, and needs a provider only for the
accounts whose data it reads:

```kotlin
val accounts = Escrow.DepositAccounts.Partial(user = me)
    .resolve(api, Escrow.DepositArgs(poolId, amount)) // args only when a PDA seed reads them
    .sendAwait().unwrap()
val instruction = Escrow.deposit(accounts, args)
```

Each account the IDL does not let you leave out is documented on its `Partial` property, along with the reason. An
account you give is used as given. It is never derived again or checked against a derivation, so you can
override any of them. Every other required account is resolved from the first of these that applies:

1. its fixed `address`;
2. its PDA `seeds`, and its `program` when that is set, through the same `find` function. Seeds can be constants,
   arguments, the addresses of other accounts, or fields of other accounts' data. A field seed is resolved only when
   its `account` names an account type this program declares;
3. its `relations`, which are Anchor's `has_one`. Each related account's data is fetched and decoded as whichever
   of this program's account types its discriminator identifies. Only types with a pubkey field named after this
   account count. When there are several related accounts, they must all name the same address.

Optional accounts are never derived. They are absent unless you give them. A way of deriving an account that
depends on an absent optional account is skipped.

Accounts whose data a derivation reads are fetched with `getMultipleAccounts`. Each round fetches every account it
needs in one request, and no round reads older state than the round before it. Only accounts the program owns are
read. Resolution returns the first failure in instruction order as a typed `SolanaAccountResolutionError` and does
not throw:

| Error | Cause |
| --- | --- |
| `MissingAccount` | a required account was not given, and the IDL gives no usable way to derive it |
| `AccountNotFound` | an account whose data a derivation reads does not exist on chain |
| `OwnerMismatch` | that account is owned by another program |
| `AccountTypeMismatch` | its discriminator is not one of the account types the derivation reads |
| `Undecodable` | it has the right discriminator, but its data does not decode |
| `AmbiguousRelation` | related accounts name different addresses for the account |
| `InvalidSeeds` | the seeds cannot derive an address, such as a seed longer than 32 bytes |
| `DependencyCycle` | accounts can only be derived from each other; give one of them to break the cycle |
| `Rpc` | a fetch failed |

Nothing is inferred from names alone, and nothing is guessed. The resolver does not use:

- a seed whose encoding the IDL does not determine. This is the same case where `find` takes the seed's bytes;
- a seed read from an account type this program does not declare;
- a `program` that is not a pubkey;
- a relation that no account type can follow.

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
