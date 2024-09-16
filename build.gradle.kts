plugins {
    `project-conventions`
    `jacoco-report-aggregation`
    id("test-report-aggregation")
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
    version = "1.2.1"
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
