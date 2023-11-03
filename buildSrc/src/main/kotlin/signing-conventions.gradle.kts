plugins {
    signing
}

signing {
    isRequired = isLibraryReleaseMode()

    val signingKeyId = System.getenv("ORG_GRADLE_PROJECT_signingKeyId")
    val signingKey = System.getenv("ORG_GRADLE_PROJECT_signingKey")
    val signingPassword = System.getenv("ORG_GRADLE_PROJECT_signingKeyPassword")

    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
}
