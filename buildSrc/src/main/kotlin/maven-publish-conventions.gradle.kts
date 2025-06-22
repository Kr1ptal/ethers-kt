plugins {
    `maven-publish`
}

val configureMavenCentralRepo: Action<RepositoryHandler> = Action {
    // For JReleaser, we'll use a local staging directory
    // JReleaser will handle the actual upload to Maven Central
    maven {
        name = "localStaging"
        url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
    }

    // Always publish to local
    mavenLocal()
}

val configurePom = Action<MavenPom> {
    name = project.name
    description =
        "Async, high-performance Kotlin library for interacting with EVM-based blockchains. Targeting JVM and Android platforms."
    url = "https://github.com/Kr1ptal/ethers-kt"

    licenses {
        license {
            name = "Apache-2.0 License"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution = "repo"
        }
    }
    developers {
        developer {
            id = "kriptal"
            name = "Kriptal"
            organization = "Kriptal"
            organizationUrl = "https://kriptal.io"
        }
    }
    scm {
        url = "https://github.com/Kr1ptal/ethers-kt"
        connection = "scm:git:git://github.com/Kr1ptal/ethers-kt.git"
        developerConnection = "scm:git:ssh://git@github.com/Kr1ptal/ethers-kt.git"
    }
}

project.pluginManager.withPlugin("java") {
    val extJava = project.extensions.getByType(JavaPluginExtension::class.java)
    extJava.withJavadocJar()
    extJava.withSourcesJar()

    publishing {
        publications {
            create<MavenPublication>("library") {
                pom(configurePom)
                from(components["java"])
            }
        }

        repositories(configureMavenCentralRepo)
    }
}

project.pluginManager.withPlugin("java-platform") {
    publishing {
        publications {
            create<MavenPublication>("libraryBom") {
                pom(configurePom)
                from(components["javaPlatform"])
            }
        }

        repositories(configureMavenCentralRepo)
    }
}
