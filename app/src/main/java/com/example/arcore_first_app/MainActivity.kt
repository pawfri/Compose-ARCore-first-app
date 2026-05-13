package com.example.arcore_first_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.arcore_first_app.ui.theme.ArcorefirstappTheme
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

class MainActivity : ComponentActivity() {
    // ARCore state
    private var mSession by mutableStateOf<Session?>(null)
    private var mUserRequestedInstall = true
    private var isArSupported by mutableStateOf(false)
    private var showArExperience by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Camera permission is needed to run this application",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeEnableArButton() // Check if ARCore is supported on device

        setContent {
            ArcorefirstappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showArExperience) {
                        // Show the dedicated AR View
                        ArView(
                            modifier = Modifier.fillMaxSize(),
                            session = mSession
                        )
                    } else {
                        // Show the standard Landing UI
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .padding(16.dp)
                        ) {
                            Greeting(name = "AR User")
                            if (isArSupported) {
                                Button(onClick = { showArExperience = true }) {
                                    Text("Enter AR Experience")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // ARCore requires camera permission to operate.
        if (!hasCameraPermission()) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        // Ensure that Google Play Services for AR is installed and create session
        try {
            if (mSession == null) {
                when (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // Success: Safe to create the AR session.
                        mSession = Session(this)
                    }
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        mUserRequestedInstall = false
                        return
                    }
                }
            }
        } catch (e: Exception) {
            val message = when (e) {
                is UnavailableUserDeclinedInstallationException -> "Please install Google Play Services for AR"
                else -> "ARCore not supported: $e"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Explicitly close the ARCore session to release native resources.
        mSession?.close()
        mSession = null
    }

    private fun maybeEnableArButton() {
        ArCoreApk.getInstance().checkAvailabilityAsync(this) { availability ->
            isArSupported = availability.isSupported
        }
    }

    // Permission Helpers
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        ArcorefirstappTheme {
            Greeting("Android")
        }
    }
}