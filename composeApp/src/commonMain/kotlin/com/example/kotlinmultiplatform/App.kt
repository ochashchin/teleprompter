package com.example.kotlinmultiplatform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import com.russhwolf.settings.Settings


@Composable
fun App() {
    val settings = remember { Settings() }

    AppTheme {
        CompositionLocalProvider(LocalSettings provides settings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            AppNavigation(modifier = Modifier.fillMaxSize())
        }
    }
}
