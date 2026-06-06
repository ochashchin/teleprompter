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
        // Sync initial state — app may launch directly into split-screen
        WindowModeObserver.setMultiWindow(isInMultiWindowMode)
        setContent { App() }
    }

    /**reen / mul
     * Called when the app enters or exits split-scti-window mode.
     *
     * IMPORTANT: also add these to AndroidManifest.xml on the <activity> tag
     * to prevent Activity recreation on window resize:
     *
     *   android:configChanges="orientation|screenSize|screenLayout|
     *       smallestScreenSize|keyboard|keyboardHidden|navigation"
     *
     * Without that, dragging the split-screen divider triggers a config change
     * that destroys the Activity and wipes all remember{} ViewModels.
     */
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
