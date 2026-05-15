package com.example.arcore_first_app

import android.opengl.GLES30
import com.example.arcore_first_app.java.samplerender.SampleRender
import com.example.arcore_first_app.java.samplerender.arcore.BackgroundRenderer
import com.example.arcore_first_app.java.samplerender.Mesh
import com.example.arcore_first_app.java.samplerender.Shader
import com.example.arcore_first_app.java.samplerender.Texture
import com.google.ar.core.Session

class ArRenderer(var session: Session?) : SampleRender.Renderer {
    private lateinit var backgroundRenderer: BackgroundRenderer

    // 3D object components
    private lateinit var virtualObjectMesh: Mesh
    private lateinit var virtualObjectShader: Shader
    private lateinit var virtualObjectAlbedoTexture: Texture

    override fun onSurfaceCreated(render: SampleRender) {
        // Set background color to black
        GLES30.glClearColor(0f, 0f, 0f, 1.0f)

        // Initialize background
        backgroundRenderer = BackgroundRenderer(render)

        // Which shaders the BackgroundRenderer should use (to get camera feed)
        // false = loads standard camera background shaders, to make camera appear immediately
        try {
            backgroundRenderer.setUseDepthVisualization(render, false)
            backgroundRenderer.setUseOcclusion(render, false)
        } catch (e: Exception) {
            // Handle shader loading errors
        }

        // Load the 3D Model
        try {
            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj")
            virtualObjectAlbedoTexture = Texture.createFromAsset(
                render,
                "models/pawn_albedo.png",
                Texture.WrapMode.CLAMP_TO_EDGE,
                Texture.ColorFormat.SRGB
            )

            // Load the Shader
            virtualObjectShader = Shader.createFromAssets(
                render,
                "shaders/ar_unlit_object.vert",
                "shaders/ar_unlit_object.frag",
                null
            ).setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)

        } catch (e: Exception) {
            // Handle loading errors
        }

        // Tell ARCore to use the camera texture
        // Use getTextureId() explicitly to avoid conflict with the private field in Texture.java
        val textureId = backgroundRenderer.cameraColorTexture.getTextureId()
        session?.setCameraTextureNames(intArrayOf(textureId))
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        // Update ARCore so it knows how to scale the camera feed to your screen size
        session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        val session = session ?: return

        try {
            val frame = session.update()

            // Update background renderer geometry (handles rotation)
            backgroundRenderer.updateDisplayGeometry(frame)
            // Draw the camera feed
            backgroundRenderer.drawBackground(render)

        } catch (e: Exception) {
            // Handle frame update errors
        }
    }
}