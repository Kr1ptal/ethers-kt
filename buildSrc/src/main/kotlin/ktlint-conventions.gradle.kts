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
}
