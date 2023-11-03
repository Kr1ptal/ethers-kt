plugins {
    `maven-publish`
    id("signing-conventions")
}

val configureMavenCentralRepo: Action<RepositoryHandler> = Action {
    if (isLibraryReleaseMode()) {
        maven {
            name = "mavenCentral"

            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }

            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    } else {
        mavenLocal()
    }
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

    signing {
        sign(publishing.publications["library"])
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

    signing {
        sign(publishing.publications["libraryBom"])
    }
}
