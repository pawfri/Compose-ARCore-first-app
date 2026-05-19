package com.example.arcore_first_app

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.example.arcore_first_app.java.helpers.TapHelper
import com.example.arcore_first_app.java.samplerender.SampleRender
import com.example.arcore_first_app.java.samplerender.arcore.BackgroundRenderer
import com.example.arcore_first_app.java.samplerender.Mesh
import com.example.arcore_first_app.java.samplerender.Shader
import com.example.arcore_first_app.java.samplerender.Texture
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

class ArRenderer(var session: Session?, private val tapHelper: TapHelper) : SampleRender.Renderer {
    private lateinit var backgroundRenderer: BackgroundRenderer

    // 3D object components
    private lateinit var virtualObjectMesh: Mesh
    private lateinit var virtualObjectShader: Shader
    private lateinit var virtualObjectAlbedoTexture: Texture

    // List to keep track of placed objects
    private val anchors = mutableListOf<Anchor>()

    // Matrices for 3D math
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)

    private var isTextureSet = false

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
            Log.e("ArRenderer", "Failed to setup background", e)
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
            ).setTexture("u_Texture", virtualObjectAlbedoTexture)

        } catch (e: Exception) {
            Log.e("ArRenderer", "Failed to load 3D assets: ${e.message}", e)
        }
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        // Update ARCore so it knows how to scale the camera feed to the screen size
        session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        val session = session ?: return

        // 1. Ensure camera texture is linked (fixes black screen/sync issues)
        if (!isTextureSet) {
            val textureId = backgroundRenderer.cameraColorTexture.getTextureId()
            session.setCameraTextureNames(intArrayOf(textureId))
            isTextureSet = true
        }

        val frame = try { session.update() } catch (e: Exception) { return }

        backgroundRenderer.updateDisplayGeometry(frame)
        backgroundRenderer.drawBackground(render)

        // 2. Handle Taps
        handleTap(frame)

        // 3. Get Camera Matrices
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        // 4. Draw placed objects
        // Safety check: Don't draw if the assets failed to load
        if (!::virtualObjectShader.isInitialized || !::virtualObjectMesh.isInitialized) return

        for (anchor in anchors) {
            if (anchor.trackingState != TrackingState.TRACKING) continue

            // Get position
            anchor.pose.toMatrix(modelMatrix, 0)

            // Math
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            // Send matrices to the shader and draw
            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render.draw(virtualObjectMesh, virtualObjectShader)
        }
    }

    private fun handleTap(frame: Frame) {
        val tap = tapHelper.poll() ?: return
        val hitResultList = frame.hitTest(tap)

        // Find the first hit that is on a plane (floor/table)
        val firstHit = hitResultList.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
        }

        if (firstHit != null) {
            // Keep maximum 10 anchors to save memory
            if (anchors.size >= 10) {
                anchors[0].detach()
                anchors.removeAt(0)
            }
            // Create a permanent location in the real world
            anchors.add(firstHit.createAnchor())
        }
    }
}