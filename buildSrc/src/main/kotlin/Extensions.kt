import org.gradle.api.Project


fun Project.isLibraryReleaseMode(): Boolean {
    return System.getenv("LIB_RELEASE").equals("true", ignoreCase = true)
}
