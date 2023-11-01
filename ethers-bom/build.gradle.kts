plugins {
    `java-platform`
}

dependencies {
    constraints {
        project.rootProject.subprojects.forEach { subproject ->
            if (subproject.name != "ethers-bom") {
                api(subproject)
            }
        }
    }
}
