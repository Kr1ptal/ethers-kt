import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    val layout = project.layout
    val libs = the<LibrariesForLibs>()

    version = libs.versions.ktlint.tool.get()

    reporters {
        reporter(ReporterType.HTML)
        reporter(ReporterType.SARIF)
    }

    filter {
        exclude { it.file.path.contains(layout.buildDirectory.dir("generated").get().toString()) }
    }

    // format of rule keys defined at: https://pinterest.github.io/ktlint/latest/rules/configuration-ktlint/#disable-rules
    additionalEditorconfig.set(
        mapOf(
            "ktlint_code_style" to "intellij_idea",
            "ktlint_standard_comment-spacing" to "disabled",
            "ktlint_standard_discouraged-comment-location" to "disabled",
            "ktlint_standard_property-naming" to "disabled",
            "ktlint_standard_spacing-between-declarations-with-annotations" to "disabled",
            "ktlint_standard_multiline-if-else" to "disabled",
            "ktlint_standard_value-argument-comment" to "disabled",
            "ktlint_standard_value-parameter-comment" to "disabled",
            "ktlint_standard_backing-property-naming" to "disabled",
            "ktlint_standard_function-expression-body" to "disabled",
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_blank-line-between-when-conditions" to "disabled",
        ),
    )
}

// The plugin lints each Kotlin source set as a whole, and KSP, abigen and the static data generators add their task
// outputs to source sets. Every check therefore depended on code generation, and through it on compiling the upstream
// modules for every target, although the filter above then skips the generated files. Linting only the directories
// outside the build directory, which no task produces, keeps checks free of compilation. It also lets `ktlintCheck`
// run on a host that cannot compile the Apple targets.
plugins.withId("org.jetbrains.kotlin.multiplatform") { lintCheckedInSourcesOnly() }
plugins.withId("org.jetbrains.kotlin.jvm") { lintCheckedInSourcesOnly() }

fun lintCheckedInSourcesOnly() {
    val kotlin = extensions.getByType<KotlinProjectExtension>()
    val buildDir = layout.buildDirectory.get().asFile
    // After evaluation, since the plugin adds the sources of a source set created late, such as `jvmJmh`, after any
    // action registered earlier would run.
    afterEvaluate {
        tasks.withType<BaseKtLintCheckTask>().matching { it.name.endsWith("SourceSet") }.configureEach {
            val sourceSetName = name.removePrefix("runKtlintCheckOver").removePrefix("runKtlintFormatOver")
                .removeSuffix("SourceSet").replaceFirstChar { it.lowercase() }
            val sourceSet = kotlin.sourceSets.findByName(sourceSetName) ?: error("No Kotlin source set for $path")
            setSource(sourceSet.kotlin.srcDirs.filterNot { it.startsWith(buildDir) })
            include("**/*.kt")
        }
    }
}
