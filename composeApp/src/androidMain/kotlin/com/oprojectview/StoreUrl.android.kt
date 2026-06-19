package com.oprojectview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberStoreUrl(): String {
    val context = LocalContext.current
    return "https://play.google.com/store/apps/details?id=${context.packageName}"
}
