package io.ethers.abigen.plugin.task

import io.ethers.abigen.plugin.source.SolanaIdlSource
import io.ethers.solana.idl.generator.SolanaProgramBuilder
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Work action which generates Kotlin program bindings for a single Solana IDL.
 *
 * Writes the program's IDL name and the canonical name of the generated object to [Parameters.resultFile], one per
 * line, so the task can check them across all IDLs.
 * */
abstract class SolanaIdlWork : WorkAction<SolanaIdlWork.Parameters> {
    private val logger = LoggerFactory.getLogger(SolanaIdlWork::class.java)

    override fun execute() {
        val source = parameters.idl.get()
        val read = SolanaIdlReaderRegistry.tryReadIdl(source.idlUrl).unwrap()
        val address = parameters.programAddresses.get()[read.name]
        val program = try {
            if (address != null) read.copy(address = address) else read
        } catch (e: IllegalArgumentException) {
            throw GradleException("Invalid address override for program '${read.name}' ($source): ${e.message}", e)
        }

        logger.info("Generating Kotlin bindings for ${program.name} from $source")
        val builder = try {
            SolanaProgramBuilder(program, source.destinationPackage)
        } catch (e: IllegalArgumentException) {
            throw GradleException("Failed to generate bindings for program '${program.name}' ($source): ${e.message}", e)
        }
        val canonicalName = builder.writeTo(parameters.destination.get())
        parameters.resultFile.get().asFile.writeText("${program.name}\n$canonicalName")
    }

    // all parameters need to be serializable
    interface Parameters : WorkParameters {
        val idl: Property<SolanaIdlSource>
        val destination: Property<File>
        val programAddresses: MapProperty<String, String>
        val resultFile: RegularFileProperty
    }
}
