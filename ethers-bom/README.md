# ethers-bom

Bill of materials (BOM) that can be utilized to ensure ethers-kt artifacts are up-to date and compatible with
each other.

```kotlin
dependencies {
    // Define a BOM and its version
    implementation(platform("io.kriptal.ethers:ethers-bom:2.0.0"))

    // Define any required artifacts without version
    implementation("io.kriptal.ethers:ethers-abi")
    implementation("io.kriptal.ethers:ethers-core")
    implementation("io.kriptal.ethers:ethers-providers")
    implementation("io.kriptal.ethers:ethers-signers")
}
```

