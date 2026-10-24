import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

// Not published. Links ethers-common and ethers-solana into a macOS framework the way a Swift app consumes them, then
// compiles and runs src/swift against it. Kotlin-side tests never see the generated Objective-C header, so this is
// what checks the Swift API itself: names, `throws`, closures, `async`, `Data` bridging.

plugins {
    `project-conventions`
}

kotlin {
    macosArm64 {
        binaries.framework("EthersKt", listOf(NativeBuildType.DEBUG)) {
            export(project(":ethers-common"))
            export(project(":ethers-solana"))
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ethers-common"))
                api(project(":ethers-solana"))
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

val framework = kotlin.macosArm64().binaries.getFramework("EthersKt", NativeBuildType.DEBUG)
// the directory holding EthersKt.framework, for `swiftc -F`
val frameworkDir = framework.outputFile.parentFile

val swiftTest = tasks.register<Exec>("swiftTest") {
    group = "verification"
    description = "Compiles src/swift against the exported framework and runs it."
    dependsOn(framework.linkTaskProvider)
    onlyIf("Swift and the macOS framework need a macOS host") { System.getProperty("os.name").startsWith("Mac") }

    val sources = layout.projectDirectory.dir("src/swift")
    val output = layout.buildDirectory.dir("swift")
    inputs.dir(sources)
    inputs.dir(framework.outputDirectory)
    outputs.upToDateWhen { false }

    workingDir = sources.asFile
    commandLine("bash", "run.sh", frameworkDir.absolutePath, output.get().asFile.absolutePath)
}

tasks.named("check") { dependsOn(swiftTest) }
