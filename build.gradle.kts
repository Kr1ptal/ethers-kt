plugins {
    `project-conventions`
    `jacoco-report-aggregation`
}

dependencies {
    jacocoAggregation(project(":ethers-abi"))
    jacocoAggregation(project(":ethers-abigen"))
    jacocoAggregation(project(":ethers-abigen-plugin"))
    jacocoAggregation(project(":ethers-core"))
    jacocoAggregation(project(":ethers-crypto"))
    jacocoAggregation(project(":ethers-providers"))
    jacocoAggregation(project(":ethers-rlp"))
    jacocoAggregation(project(":ethers-signers"))
    jacocoAggregation(project(":logger"))
}

tasks.withType<Test> {
    finalizedBy(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

allprojects {
    group = "io.kriptal.ethers"
    version = "0.2.0"
}
