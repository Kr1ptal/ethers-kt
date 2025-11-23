import org.jreleaser.model.Active

plugins {
    `project-conventions`
    `jacoco-report-aggregation`
    id("test-report-aggregation")
    alias(libs.plugins.jreleaser)
}

dependencies {
    // contains only submodules that are released
    val releasedSubmodules = listOf(
        ":ethers-abi",
        ":ethers-abigen",
        ":ethers-abigen-plugin",
        ":ethers-core",
        ":ethers-crypto",
        ":ethers-ens",
        ":ethers-providers",
        ":ethers-rlp",
        ":ethers-signers-gcp",
        ":ethers-signers",
        ":logger",
    )

    releasedSubmodules.forEach {
        jacocoAggregation(project(it))
        testReportAggregation(project(it))
    }
}

// TODO, see: https://github.com/Kr1ptal/ethers-kt/issues/66
/*tasks.withType<Test> {
    finalizedBy(tasks.named<JacocoReport>("testCodeCoverageReport"))
}*/

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport"))
}

allprojects {
    group = "io.kriptal.ethers"
    version = "1.5.1-SNAPSHOT"
}

subprojects {
    /**
     * Analyze sizes of added dependencies.
     */
    tasks.register("depsize", Task::class.java) {
        doLast {
            // skip depsize task if no runtime classpath is defined
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

    // Set project info for deployment
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
                }
            }
        }
    }
}
