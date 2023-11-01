project.pluginManager.withPlugin("java") {
    val extension = the<JavaPluginExtension>()
    val javaToolchainService = the<JavaToolchainService>()

    extension.toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTIUM)
        implementation.set(JvmImplementation.VENDOR_SPECIFIC)
    }

    tasks.withType<JavaExec>().configureEach {
        javaLauncher.set(javaToolchainService.launcherFor(extension.toolchain))
    }
    tasks.withType<Test>().configureEach {
        javaLauncher.set(javaToolchainService.launcherFor(extension.toolchain))
    }
}
