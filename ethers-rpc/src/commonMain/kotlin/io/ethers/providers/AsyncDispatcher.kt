package io.ethers.providers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Shared platform-specific dispatcher for long-lived and potentially blocking provider tasks.
 *
 * Public so that provider implementations outside this module, such as the EVM filter poller, run
 * their long-lived work on the same dispatcher as the transport.
 */
expect val asyncDispatcher: CoroutineDispatcher
