rootProject.name = "ethers-kt"

include("ethers-bom")
include("ethers-core")
include("ethers-providers")
include("ethers-rlp")
include("ethers-signers")
include("ethers-abi")
include("ethers-abigen")
include("ethers-abigen-plugin")
include("ethers-crypto")
include("logger")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}
