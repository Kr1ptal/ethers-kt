fun isLibraryReleaseMode(): Boolean {
    return System.getenv("LIB_RELEASE").equals("true", ignoreCase = true)
}
