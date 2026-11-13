package com.celestiallemon.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celestiallemon.tuner.ui.Palette
import com.celestiallemon.tuner.ui.PermissionScreen
import com.celestiallemon.tuner.ui.TunerScreen
import com.celestiallemon.tuner.ui.TunerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TunerViewModel by viewModels()

    @SuppressLint("MissingPermission") // start() is only called once the permission is granted.
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Tuning takes both hands; don't let the screen dim halfway through.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent.getBooleanExtra("capture", false)) viewModel.capture(java.io.File(getExternalFilesDir(null), "capture.f32"))

        setContent {
            TunerTheme {
                var granted by remember { mutableStateOf(hasMicPermission()) }
                var askedOnce by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    granted = it
                    askedOnce = true
                }
                Box(Modifier.fillMaxSize().background(Palette.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    if (granted) {
                        // Listen only while the app is in front.
                        LifecycleResumeEffect(Unit) {
                            viewModel.start()
                            onPauseOrDispose { viewModel.stop() }
                        }
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        TunerScreen(
                            state = state,
                            onSelectTuning = viewModel::selectTuning,
                            onA4Change = viewModel::setA4,
                            onLockString = viewModel::lockString,
                        )
                    } else {
                        // Re-check on resume, in case it was granted from Settings.
                        LifecycleResumeEffect(Unit) {
                            granted = hasMicPermission()
                            onPauseOrDispose { }
                        }
                        LaunchedEffect(Unit) { if (!askedOnce) launcher.launch(Manifest.permission.RECORD_AUDIO) }
                        PermissionScreen(
                            permanentlyDenied = askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
                            onAllow = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                            onOpenSettings = {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
