package io.ethers.abigen.plugin.task

import io.ethers.abigen.AbiContractBuilder
import io.ethers.abigen.plugin.source.AbiSource
import io.ethers.abigen.reader.JsonAbiReaderRegistry
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Work action which generates kotlin wrapper code for a single ABI.
 * */
abstract class AbigenWork : WorkAction<AbigenWork.Parameters> {
    private val logger = LoggerFactory.getLogger(AbigenWork::class.java)

    override fun execute() {
        val source = parameters.abi.get()
        val abi = JsonAbiReaderRegistry.readAbi(source.abiUrl)
        if (abi == null) {
            logger.error("Failed to read ABI from ${source.abiUrl}")
            throw GradleException("Failed to read ABI from ${source.abiUrl}")
        }

        logger.info("Generating Kotlin wrapper for ${source.contractName}")
        val canonicalName = AbiContractBuilder(
            source.contractName,
            source.destinationPackage,
            parameters.destination.get(),
            abi,
            parameters.functionRenames.get(),
        ).build(parameters.errorLoaderName.get())

        parameters.canonicalNameFile.get().asFile.writeText(canonicalName)
    }

    // all parameters need to be serializable
    interface Parameters : WorkParameters {
        val abi: Property<AbiSource>
        val destination: Property<File>
        val functionRenames: MapProperty<String, String>
        val errorLoaderName: Property<String>
        val canonicalNameFile: RegularFileProperty
    }
}
