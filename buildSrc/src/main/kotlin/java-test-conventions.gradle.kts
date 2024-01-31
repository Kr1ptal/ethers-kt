import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    jacoco
}

project.pluginManager.withPlugin("java") {
    val libs = the<LibrariesForLibs>()

    jacoco {
        toolVersion = libs.versions.jacoco.tool.get()
    }

    tasks.withType<JacocoReport>().configureEach {
        // add support for multiple source sets, based on: https://stackoverflow.com/a/59377041
        executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/*.exec"))

        reports {
            html.required.set(true)
            csv.required.set(true)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // TODO, see: https://github.com/Kr1ptal/ethers-kt/issues/66
        //finalizedBy("jacocoTestReport")
    }
}
