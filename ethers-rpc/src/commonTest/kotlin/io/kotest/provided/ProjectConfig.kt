package io.kotest.provided

import io.ethers.providers.RunningMockServers
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.spec.Spec

/**
 * Kotest configuration for this module's tests, found by its package and name. A class rather than an object: the
 * test runner Kotest generates for native targets constructs it.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(MockServerLeakCheck)
}

/** Fails a spec that leaves a mock server running, see [RunningMockServers]. */
private object MockServerLeakCheck : AfterSpecListener {
    override suspend fun afterSpec(spec: Spec) {
        val leaked = RunningMockServers.stopAll()
        if (leaked > 0) {
            throw AssertionError("${spec::class.simpleName} left $leaked mock server(s) running; stop each server a test starts")
        }
    }
}
