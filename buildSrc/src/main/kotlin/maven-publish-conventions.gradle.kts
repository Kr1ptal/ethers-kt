plugins {
    `maven-publish`
}

val configureRepositories: Action<PublishingExtension> = Action {
    repositories {
        if (System.getenv("LIB_RELEASE").equals("true", ignoreCase = true)) {
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
}

project.pluginManager.withPlugin("java-platform") {
    publishing {
        publications {
            create<MavenPublication>("library-bom") {
                from(components["javaPlatform"])
            }
        }

        configureRepositories.execute(this)
    }
}
