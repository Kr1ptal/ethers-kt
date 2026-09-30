import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val libs = the<LibrariesForLibs>()
    val jmhGenerator = configurations.create("jmhGenerator")
    dependencies {
        jmhGenerator(libs.jmh.generator.bytecode)
    }

    val kmpExt = the<KotlinMultiplatformExtension>()
    val jvmTarget = kmpExt.jvm()

    // Register JMH compilation that depends on jvmMain output
    val jmhCompilation = jvmTarget.compilations.create("jmh") {
        associateWith(jvmTarget.compilations.getByName("main"))
        // benchmarks live in src/jmh rather than the source set's default src/jvmJmh
        defaultSourceSet.kotlin.srcDirs("src/jmh/kotlin", "src/jmh/java")
    }

    // Make JMH source set visible in IDEA as test sources
    configure<IdeaModel> {
        module {
            testSources.from(jmhCompilation.defaultSourceSet.kotlin.srcDirs)
        }
    }

    // compile and run the harness on the project toolchain rather than the Gradle daemon's JDK
    val toolchains = the<JavaToolchainService>()
    val toolchain = the<JavaPluginExtension>().toolchain

    // The annotation processor never sees Kotlin sources, so generate the harness from compiled bytecode
    val generatedSources = layout.buildDirectory.dir("generated/jmh/sources")
    val generatedResources = layout.buildDirectory.dir("generated/jmh/resources")
    val benchmarkClasses = jmhCompilation.compileTaskProvider.flatMap { (it as KotlinJvmCompile).destinationDirectory }
    val jmhRuntimeClasspath = files(jmhCompilation.output.allOutputs, jmhCompilation.runtimeDependencyFiles)

    val jmhGenerate = tasks.register<JavaExec>("jmhGenerate") {
        description = "Generates the JMH harness from compiled benchmark classes."
        inputs.dir(benchmarkClasses)
        outputs.dirs(generatedSources, generatedResources)
        mainClass = "org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator"
        classpath(jmhGenerator, jmhRuntimeClasspath)
        doFirst {
            generatedSources.get().asFile.deleteRecursively()
            generatedResources.get().asFile.deleteRecursively()
            args(benchmarkClasses.get().asFile, generatedSources.get().asFile, generatedResources.get().asFile, "default")
        }
    }

    val jmhCompileGenerated = tasks.register<JavaCompile>("jmhCompileGenerated") {
        description = "Compiles the generated JMH harness."
        dependsOn(jmhGenerate)
        source(generatedSources)
        classpath = jmhRuntimeClasspath
        javaCompiler = toolchains.compilerFor(toolchain)
        destinationDirectory = layout.buildDirectory.dir("classes/java/jmhGenerated")
    }

    // Run with e.g. `./gradlew :ethers-rlp:jmh -Pjmh.includes=RlpEncoderBenchmark -Pjmh.args="-prof gc"`
    tasks.register<JavaExec>("jmh") {
        group = "benchmark"
        description = "Runs JMH benchmarks."
        dependsOn(jmhCompileGenerated)
        mainClass = "org.openjdk.jmh.Main"
        javaLauncher = toolchains.launcherFor(toolchain)
        classpath(jmhCompileGenerated.map { it.destinationDirectory }, generatedResources, jmhRuntimeClasspath)
        providers.gradleProperty("jmh.includes").orNull?.let { args(it) }
        providers.gradleProperty("jmh.args").orNull?.let { args(it.split(" ").filter(String::isNotBlank)) }
    }
}
