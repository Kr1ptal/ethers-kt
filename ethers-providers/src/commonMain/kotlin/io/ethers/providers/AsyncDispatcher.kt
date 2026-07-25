package io.ethers.providers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Shared platform-specific dispatcher for long-lived and potentially blocking provider tasks.
 */
internal expect val asyncDispatcher: CoroutineDispatcher
