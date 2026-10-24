plugins {
    `java-platform`
    `maven-publish-conventions`
}

dependencies {
    constraints {
        // swift-interop-test is a test harness, never published
        project.rootProject.subprojects.forEach { subproject ->
            if (subproject.name != "ethers-bom" && subproject.name != "swift-interop-test") {
                api(subproject)
            }
        }
    }
}
