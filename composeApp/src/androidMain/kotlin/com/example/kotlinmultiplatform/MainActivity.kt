package com.example.kotlinmultiplatform

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.kotlinmultiplatform.core.WindowModeObserver

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowModeObserver.setMultiWindow(isInMultiWindowMode)
        setContent {
            App(onExitApp = { finishAffinity() })
        }
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        WindowModeObserver.setMultiWindow(isInMultiWindowMode)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
