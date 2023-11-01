# ethers-bom

Bill of materials (BOM) that can be utilized to ensure ethers-kt artifacts are up-to date and compatible with
each other.

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

