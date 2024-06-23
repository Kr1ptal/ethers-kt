import org.gradle.accessors.dm.LibrariesForLibs
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
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
        ),
    )
}
