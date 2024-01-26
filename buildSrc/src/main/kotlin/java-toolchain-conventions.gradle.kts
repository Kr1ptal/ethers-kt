project.pluginManager.withPlugin("java") {
    val extension = the<JavaPluginExtension>()
    val javaToolchainService = the<JavaToolchainService>()

    extension.toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTIUM)
        implementation.set(JvmImplementation.VENDOR_SPECIFIC)
    }

    tasks.withType<JavaExec>().all {
        javaLauncher.set(javaToolchainService.launcherFor(extension.toolchain))
    }

    tasks.withType<Test>().all {
        javaLauncher.set(javaToolchainService.launcherFor(extension.toolchain))
    }

    // temp fix: https://youtrack.jetbrains.com/issue/IDEA-316081/Gradle-8-toolchain-error-Toolchain-from-executable-property-does-not-match-toolchain-from-javaLauncher-property-when-different#focus=Comments-27-7276479.0-0
    gradle.taskGraph.whenReady {
        tasks.withType<JavaExec>().all {
            @Suppress("UsePropertyAccessSyntax")
            setExecutable(javaLauncher.get().executablePath.asFile.absolutePath)
        }
    }
}
