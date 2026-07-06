package com.oprojectview.navigation

sealed interface ExitReason {
    data object Back : ExitReason
    data object Close : ExitReason
}

interface ExitHandler {
    fun requestExit(reason: ExitReason)
    var shouldInterceptBack: Boolean
}
