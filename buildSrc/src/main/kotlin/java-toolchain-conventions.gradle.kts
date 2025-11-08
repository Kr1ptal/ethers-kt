project.pluginManager.withPlugin("java") {
    val extension = the<JavaPluginExtension>()
    val javaToolchainService = the<JavaToolchainService>()

    extension.toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
        implementation = JvmImplementation.VENDOR_SPECIFIC
    }

    extension.targetCompatibility = Constants.compileJavaVersion
    extension.sourceCompatibility = Constants.compileJavaVersion

    tasks.withType<JavaExec>().all {
        javaLauncher = javaToolchainService.launcherFor(extension.toolchain)
    }

    tasks.withType<Test>().all {
        javaLauncher = javaToolchainService.launcherFor(extension.toolchain)
    }

    tasks.withType<JavaCompile>().matching { it.name.contains("Test") }.all {
        targetCompatibility = Constants.testJavaVersion.majorVersion
        sourceCompatibility = Constants.testJavaVersion.majorVersion
    }


    // temp fix: https://youtrack.jetbrains.com/issue/IDEA-316081/Gradle-8-toolchain-error-Toolchain-from-executable-property-does-not-match-toolchain-from-javaLauncher-property-when-different#focus=Comments-27-7276479.0-0
    gradle.taskGraph.whenReady {
        tasks.withType<JavaExec>().all {
            @Suppress("UsePropertyAccessSyntax")
            setExecutable(javaLauncher.get().executablePath.asFile.absolutePath)
        }
    }
}
