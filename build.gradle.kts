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
        ":ethers-signers",
        ":logger",
    )

    releasedSubmodules.forEach {
        jacocoAggregation(project(it))
        testReportAggregation(project(it))
    }
}

tasks.withType<Test> {
    finalizedBy(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport"))
}

allprojects {
    group = "io.kriptal.ethers"
    version = "0.2.0-SNAPSHOT"
}
