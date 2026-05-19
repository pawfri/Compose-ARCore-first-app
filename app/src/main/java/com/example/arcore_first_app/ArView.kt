package com.example.arcore_first_app

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.arcore_first_app.java.helpers.TapHelper
import com.example.arcore_first_app.java.samplerender.SampleRender
import com.google.ar.core.Session


@Composable
fun ArView(
    modifier: Modifier = Modifier,
    session: Session?
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val tapHelper = remember { TapHelper(context) }
    val renderer = remember { ArRenderer(session, tapHelper) }

    // Use AndroidView to embed a standard GLSurfaceView for AR rendering
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setOnTouchListener(tapHelper)
                SampleRender(this, renderer, ctx.assets)
            }
        },
        update = {
            // Ensure the renderer always has the current session
            renderer.session = session
        }
    )

    // Handle Lifecycle for the AR Session
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> try { session?.resume() } catch (e: Exception) {}
                Lifecycle.Event.ON_PAUSE -> session?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}