package com.oprojectview.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object KmpDispatchers {
    actual val Main:    CoroutineDispatcher = Dispatchers.Main
    actual val IO:      CoroutineDispatcher = Dispatchers.Default
    actual val Default: CoroutineDispatcher = Dispatchers.Default
}
