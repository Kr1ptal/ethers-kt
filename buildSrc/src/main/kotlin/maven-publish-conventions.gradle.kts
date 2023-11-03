plugins {
    `maven-publish`
    id("signing-conventions")
}

val configureRepositories: Action<PublishingExtension> = Action {
    repositories {
        if (isLibraryReleaseMode()) {
            maven {
                name = "GithubPackages"
                url = uri("https://maven.pkg.github.com/Kr1ptal/ethers-kt")

                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        } else {
            mavenLocal()
        }
    }
}

project.pluginManager.withPlugin("java") {
    val extJava = project.extensions.getByType(JavaPluginExtension::class.java)
    extJava.withJavadocJar()
    extJava.withSourcesJar()

    publishing {
        publications {
            create<MavenPublication>("library") {
                from(components["java"])
            }
        }

        configureRepositories.execute(this)
    }

    signing {
        sign(publishing.publications["library"])
    }
}

project.pluginManager.withPlugin("java-platform") {
    publishing {
        publications {
            create<MavenPublication>("libraryBom") {
                from(components["javaPlatform"])
            }
        }

        configureRepositories.execute(this)
    }

    signing {
        sign(publishing.publications["libraryBom"])
    }
}
