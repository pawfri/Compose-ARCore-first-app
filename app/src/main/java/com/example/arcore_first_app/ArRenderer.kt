package com.example.arcore_first_app

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArRenderer(var session: Session?) : GLSurfaceView.Renderer {
    private var textureId: Int = -1
    private val backgroundRenderer = BackgroundRenderer()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 1. Set background color to black
        GLES20.glClearColor(0f, 0f, 0f, 1.0f)

        // 2. Generate the texture where ARCore will "write" the camera pixels
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        // 3. Initialize our background renderer (compiles the shaders)
        backgroundRenderer.createOnGlThread()

        // 4. Tell ARCore to use this texture
        session?.setCameraTextureNames(intArrayOf(textureId))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        // Update ARCore so it knows how to scale the camera feed to your screen size
        session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = session ?: return

        try {
            // 5. Update the session to get the latest camera frame
            val frame = session.update()

            // 6. Draw the camera feed onto the screen
            backgroundRenderer.draw(frame, textureId)

        } catch (e: Exception) {
            // Handle frame update errors
        }
    }
}