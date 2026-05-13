package com.example.arcore_first_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    private var mSession: Session? = null
    private var mUserRequestedInstall = true
    private var isArSupported by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeEnableArButton() // Check if ARCore is supported on device

        setContent {
            ArcorefirstappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)) {
                    Greeting(name = "AR User")

                    // Use the state to conditionally show the AR button
                    if (isArSupported) {
                        Button(
                            onClick = { /* Start AR activity/view here */ },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Enter AR Experience")
                        }
                    } else {
                        Text(
                            "AR is not supported on this device",
                            modifier = Modifier.padding(top = 16.dp)
                        )
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
            requestCameraPermission()
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

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), 0
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!hasCameraPermission()) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Permission denied with "Do not ask again".
            }
            finish()
        }
    }
}

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