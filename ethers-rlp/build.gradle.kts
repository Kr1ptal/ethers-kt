plugins {
    `project-conventions`
    id(libs.plugins.kotlin.kapt.get().pluginId) // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
    `jmh-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.ditchoom.buffer)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}

dependencies {
    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
    kaptJmh(libs.jmh.generator)
}
