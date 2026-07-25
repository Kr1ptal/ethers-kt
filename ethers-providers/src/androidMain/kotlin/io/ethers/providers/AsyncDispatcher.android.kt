package io.ethers.providers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val asyncDispatcher: CoroutineDispatcher
    get() = Dispatchers.IO
