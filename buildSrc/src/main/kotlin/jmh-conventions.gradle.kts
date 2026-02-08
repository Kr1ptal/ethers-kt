// Create JMH configurations eagerly so they're available as Kotlin DSL accessors.
// In KMP, the standard Java plugin is not applied, so we create configurations manually.
val jmhImplementation = configurations.create("jmhImplementation")
val jmhRuntimeOnly = configurations.create("jmhRuntimeOnly")
val jmhAnnotationProcessor = configurations.create("jmhAnnotationProcessor")

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    afterEvaluate {
        val kmpExt = the<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()
        val jvmMain = kmpExt.sourceSets.getByName("jvmMain")

        jmhImplementation.extendsFrom(
            configurations.getByName(jvmMain.implementationConfigurationName),
        )
    }
}

// Also handle kapt for JMH (kapt creates kaptJmh only when a "jmh" source set exists)
pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
    configurations.maybeCreate("kaptJmh")
}
