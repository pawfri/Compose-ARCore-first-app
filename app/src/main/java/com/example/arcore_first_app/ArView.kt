package com.example.arcore_first_app

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Session
import android.opengl.GLSurfaceView
import com.example.arcore_first_app.java.samplerender.SampleRender


@Composable
fun ArView(
    modifier: Modifier = Modifier,
    session: Session?
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val renderer = remember { ArRenderer(session) }

    // Use AndroidView to embed a standard GLSurfaceView for AR rendering
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                SampleRender(this, renderer, ctx.assets)
            }
        },
        update = {
            // Ensure the renderer always has the current session
            renderer.session = session }
    )

    // Handle Lifecycle for the AR Session
    DisposableEffect(lifecycleOwner, session) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                try {
                    session?.resume()
                } catch (e: Exception) {
                    // Log error or handle failure
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                session?.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}