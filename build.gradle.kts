import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenDeployer
import org.jreleaser.model.Active

plugins {
    `project-conventions`
    alias(libs.plugins.jreleaser)
}

tasks.register("test") {
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("kotest") })
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("test") })
}

allprojects {
    group = "io.kriptal.ethers"
    version = "1.6.0-SNAPSHOT"
}

subprojects {
    tasks.register("depsize", Task::class.java) {
        doLast {
            val configuration = runCatching { project.configurations["runtimeClasspath"] }
                .getOrNull() ?: return@doLast

            var maxNameLength = 0
            var totalSizeMb = 0.0
            val formatStr = "%,10.2f"

            configuration.forEach {
                maxNameLength = maxOf(maxNameLength, it.name.trim().length)
                totalSizeMb += it.length().toDouble() / (1024 * 1024)
            }

            val sizePadding = maxNameLength + 5
            val newLine = System.lineSeparator()
            val separatorLine = "".padEnd(sizePadding, '-') + newLine

            val out = StringBuffer()
            out.append(newLine)
            out.append(separatorLine)
            out.append("| Dependency sizes for ${project.name}")
            out.append(newLine)
            out.append(separatorLine)

            configuration
                .sortedBy { -it.length() }
                .forEach {
                    out.append("| " + it.name.padEnd(sizePadding - 2))
                    out.append("${String.format(formatStr, (it.length() / 1024.0))} Kb$newLine")
                }

            out.append(separatorLine)
            out.append("| Total dependencies size:".padEnd(sizePadding))
            out.append("${String.format(formatStr, totalSizeMb)} Mb$newLine")
            out.append(separatorLine)

            println(out)
        }
    }
}

jreleaser {
    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        verify.set(true)
    }

    // Configure release to skip GitHub operations since we only want Maven Central deployment
    release {
        github {
            enabled.set(true)
            skipRelease.set(true)
            skipTag.set(true)
            token.set("dummy") // Dummy token since no GitHub operations are performed
        }
    }

    project {
        description.set("Async, high-performance Kotlin library for interacting with EVM-based blockchains. Targeting JVM and Android platforms.")
        links {
            homepage.set("https://github.com/Kr1ptal/ethers-kt")
        }
        license.set("Apache-2.0")
        inceptionYear.set("2023")
        authors.set(listOf("Kriptal"))
    }

    val stagingDir = layout.buildDirectory.dir("staging-deploy")

    // Dynamically configure artifactOverride for all non-JVM KMP targets
    fun MavenDeployer.configureKmpOverrides() {
        rootProject.subprojects.forEach { subproject ->
            kotlin.targets.forEach { target ->
                // Skip JVM target (produces JAR, not klib)
                if (target.platformType.name != "jvm") {
                    val id = "${subproject.name}-${target.name.lowercase()}"
                    println("Configuring jreleaser artifactOverride for '$id'")

                    artifactOverride {
                        groupId = "io.kriptal.channels"
                        artifactId = id
                        jar.set(false)
                        sourceJar.set(false)
                        javadocJar.set(false)
                        verifyPom.set(false)
                    }
                }
            }
        }
    }

    deploy {
        maven {
            mavenCentral {
                create("release-deploy") {
                    active.set(Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")

                    applyMavenCentralRules.set(true)
                    stagingRepository(stagingDir.get().asFile.absolutePath)

                    username.set(System.getenv("MAVEN_CENTRAL_USERNAME"))
                    password.set(System.getenv("MAVEN_CENTRAL_PASSWORD"))

                    configureKmpOverrides()
                }
            }

            nexus2 {
                create("snapshot-deploy") {
                    active.set(Active.SNAPSHOT)

                    url.set("https://central.sonatype.com/repository/maven-snapshots/")
                    snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")

                    applyMavenCentralRules.set(true)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)
                    stagingRepository(stagingDir.get().asFile.absolutePath)

                    username.set(System.getenv("MAVEN_CENTRAL_USERNAME"))
                    password.set(System.getenv("MAVEN_CENTRAL_PASSWORD"))

                    configureKmpOverrides()
                }
            }
        }
    }
}
