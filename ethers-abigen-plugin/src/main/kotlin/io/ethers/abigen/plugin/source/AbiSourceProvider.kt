package io.ethers.abigen.plugin.source

import org.gradle.api.tasks.Internal
import java.io.Serializable
import java.net.URL

/**
 * Provides a list of [AbiSource]s.
 * */
interface AbiSourceProvider {
    /**
     * Returns a list of [AbiSource]s.
     * */
    @Internal
    fun getSources(): List<AbiSource>
}

/**
 * A single ABI source.
 * */
data class AbiSource(
    val contractName: String,
    val destinationPackage: String,
    val abiUrl: URL,
) : Serializable {
    companion object {
        @Transient
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 214123932514L
    }
}
