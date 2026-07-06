package com.oprojectview.core.camera

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File


import androidx.core.content.ContextCompat

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

class AndroidPermissionHelper(
    private val context: android.content.Context,
    private val cameraLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    private val audioLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    private val onCameraResult: (Boolean, Boolean) -> Unit,
    private val onAudioResult: (Boolean) -> Unit
) : PermissionHelper {
    override fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun requestCameraPermission() {
        cameraLauncher.launch(android.Manifest.permission.CAMERA)
    }

    override fun requestAudioPermission() {
        audioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    override fun openSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

@Composable
actual fun rememberPermissionHelper(
    onCameraResult: (granted: Boolean, permanentlyDenied: Boolean) -> Unit,
    onAudioResult: (granted: Boolean) -> Unit
): PermissionHelper {
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onCameraResult(true, false)
        } else {
            val activity = context.findActivity()
            val showRationale = if (activity != null) {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    android.Manifest.permission.CAMERA
                )
            } else {
                false
            }
            onCameraResult(false, !showRationale)
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onAudioResult(granted)
    }
    return remember(context, cameraLauncher, audioLauncher, onCameraResult, onAudioResult) {
        AndroidPermissionHelper(context, cameraLauncher, audioLauncher, onCameraResult, onAudioResult)
    }
}

class AndroidCameraCalibrator(
    private val onLaunch: () -> Unit
) : CameraCalibrator {
    override fun launch() {
        onLaunch()
    }
}

@Composable
actual fun rememberCameraCalibrator(
    onCancel: () -> Unit,
    onLaunch: () -> Unit,
    onCalibrationCompleted: (CalibrationData) -> Unit
): CameraCalibrator {
    return remember {
        AndroidCameraCalibrator {
            onLaunch()
        }
    }
}

actual suspend fun exportVideoToGallery(filePath: String, context: Any, fileName: String) {
    val ctx = context as? android.content.Context ?: return
    val file = File(filePath)
    if (!file.exists()) return

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val resolver = ctx.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Teleprompter")
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        
        try {
            val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun getUniqueExportFileName(context: Any, fileName: String): String {
    val ctx = context as? android.content.Context ?: return fileName
    val resolver = ctx.contentResolver
    val uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
    
    var name = fileName
    val dotIndex = fileName.lastIndexOf('.')
    val nameWithoutExtension = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
    val extension = if (dotIndex != -1) fileName.substring(dotIndex) else ""
    
    var counter = 1
    
    while (true) {
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} = ?"
        }
        val selectionArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            arrayOf(name, "DCIM/Teleprompter%")
        } else {
            arrayOf(name)
        }
        
        var exists = false
        try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    exists = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (!exists) {
            break
        }
        name = "$nameWithoutExtension ($counter)$extension"
        counter++
    }
    return name
}

@Composable
actual fun CalibrationOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCalibrationCompleted: (CalibrationData) -> Unit,
    cameraControlState: CameraControlState,
    modifier: androidx.compose.ui.Modifier
) {
    if (visible) {
        NativeCalibrationView(
            onDismiss = onDismiss,
            onCalibrationCompleted = onCalibrationCompleted,
            zoomOptions = cameraControlState.zoomOptions,
            currentZoomIndex = cameraControlState.currentZoomIndex,
            onZoomIndexChanged = { index ->
                cameraControlState.currentZoomIndex = index
                cameraControlState.zoomRatio = cameraControlState.zoomOptions.getOrNull(index) ?: 1f
            },
            isTorchOn = cameraControlState.flashEnabled,
            onTorchChanged = { enabled ->
                cameraControlState.flashEnabled = enabled
            },
            isFrontCamera = cameraControlState.isFrontCamera,
            onFlipCamera = {
                cameraControlState.isFrontCamera = !cameraControlState.isFrontCamera
            },
            modifier = modifier
        )
    }
}
