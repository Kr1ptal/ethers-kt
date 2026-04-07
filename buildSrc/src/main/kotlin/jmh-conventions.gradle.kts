import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val kmpExt = the<KotlinMultiplatformExtension>()
    val jvmTarget = kmpExt.jvm()

    // Register JMH compilation that depends on jvmMain output
    val jmhCompilation = jvmTarget.compilations.create("jmh") {
        associateWith(jvmTarget.compilations.getByName("main"))
    }

    // Make JMH source set visible in IDEA as test sources
    configure<IdeaModel> {
        module {
            testSources.from(jmhCompilation.defaultSourceSet.kotlin.srcDirs)
        }
    }
}
