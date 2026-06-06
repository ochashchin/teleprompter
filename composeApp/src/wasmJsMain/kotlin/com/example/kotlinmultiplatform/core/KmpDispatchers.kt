package com.example.kotlinmultiplatform.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object KmpDispatchers {
    actual val Main:    CoroutineDispatcher = Dispatchers.Default
    actual val IO:      CoroutineDispatcher = Dispatchers.Default
    actual val Default: CoroutineDispatcher = Dispatchers.Default
}
