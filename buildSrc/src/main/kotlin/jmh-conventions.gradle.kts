import org.gradle.plugins.ide.idea.model.IdeaModel

project.pluginManager.withPlugin("java") {
    val sourceSets = the<SourceSetContainer>()

    sourceSets.register("jmh") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += compileClasspath
        resources.srcDir(file("src/jmh/resources"))

        configurations["jmhImplementation"].extendsFrom(configurations["implementation"])
        configurations["jmhRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])
        configurations["jmhAnnotationProcessor"].extendsFrom(configurations["annotationProcessor"])
    }

    configure<IdeaModel> {
        module {
            testSources.from(sourceSets["jmh"].java.srcDirs)
            testResources.from(sourceSets["jmh"].resources.srcDirs)
            scopes["TEST"]!!["plus"]!!.add(configurations["jmhCompileClasspath"])
        }
    }
}