package io.ethers.abigen.plugin.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Logs the declarations [EthersSolanaIdlTask] left out of the bindings as warnings. It finalizes that task and runs on
 * every build, so the warnings are logged even when the bindings are up to date or come from the build cache.
 * */
@DisableCachingByDefault(because = "Only logs a report")
abstract class EthersSolanaIdlReportTask : DefaultTask() {
    init {
        group = "ethers"
        description = "Log the Solana IDL declarations left out of the generated program bindings"
        outputs.upToDateWhen { false }
    }

    /**
     * The report [EthersSolanaIdlTask] writes. Internal, as a failed generation leaves none, which an input would not
     * allow.
     * */
    @get:Internal
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun run() {
        val report = reportFile.get().asFile
        if (!report.isFile) return
        val text = report.readText()
        if (text.isNotBlank()) logger.warn("Some Solana IDL declarations were left out of the generated bindings:\n$text")
    }
}
