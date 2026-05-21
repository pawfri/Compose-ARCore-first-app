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
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import kotlin.math.pow
import kotlin.math.sqrt

data class WrappedAnchor(
    val anchor: Anchor,
    val trackable: Trackable,
    var previousTrackingMethod: InstantPlacementPoint.TrackingMethod,
    var previousDistanceToCamera: Float,
    var scaleFactor: Float = 1.0f
)

class ArRenderer(var session: Session?, private val tapHelper: TapHelper) : SampleRender.Renderer {
    private lateinit var backgroundRenderer: BackgroundRenderer

    // 3D object components
    private lateinit var virtualObjectMesh: Mesh
    private lateinit var virtualObjectShader: Shader
    private lateinit var virtualObjectAlbedoTexture: Texture

    // List to keep track of placed objects
    private val anchors = mutableListOf<WrappedAnchor>()

    // Matrices for 3D math
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
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

        // Ensure camera texture is linked (fixes black screen/sync issues)
        if (!isTextureSet) {
            val textureId = backgroundRenderer.cameraColorTexture.getTextureId()
            session.setCameraTextureNames(intArrayOf(textureId))
            isTextureSet = true
        }

        val frame = try { session.update() } catch (e: Exception) { return }

        backgroundRenderer.updateDisplayGeometry(frame)
        backgroundRenderer.drawBackground(render)

        // Handle Taps
        val camera = frame.camera
        handleTap(frame, camera)

        // Get Camera Matrices
        if (camera.trackingState != TrackingState.TRACKING) return
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        // Draw placed objects
        // Safety check: Don't draw if the assets failed to load
        if (!::virtualObjectShader.isInitialized || !::virtualObjectMesh.isInitialized) return

        val iter = anchors.iterator()
        while (iter.hasNext()) {
            val wrapped = iter.next()
            val anchor = wrapped.anchor

            if (anchor.trackingState == TrackingState.STOPPED) {
                iter.remove()
                continue
            }
            if (anchor.trackingState != TrackingState.TRACKING) continue

            // SMOOTHING LOGIC: Check if tracking method changed from Approximate to Full
            if (wrapped.trackable is InstantPlacementPoint) {
                val point = wrapped.trackable
                val currentDistance = anchor.pose.distance(camera.pose)

                if (point.trackingMethod ==
                    InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE) {
                    wrapped.previousDistanceToCamera = currentDistance
                } else if (wrapped.previousTrackingMethod ==
                    InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE
                    && point.trackingMethod == InstantPlacementPoint.TrackingMethod.FULL_TRACKING) {
                    // Calculate a scale factor to keep the object appearing the same size during the "jump"
                    wrapped.scaleFactor = currentDistance / wrapped.previousDistanceToCamera
                    wrapped.previousTrackingMethod = InstantPlacementPoint.TrackingMethod.FULL_TRACKING
                }
            }

            anchor.pose.toMatrix(modelMatrix, 0)
            // Apply smoothing scale
            Matrix.scaleM(modelMatrix, 0, wrapped.scaleFactor, wrapped.scaleFactor, wrapped.scaleFactor)

            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, modelViewProjectionMatrix, 0, modelMatrix, 0)

            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render.draw(virtualObjectMesh, virtualObjectShader)
        }
    }

    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = tapHelper.poll() ?: return

        // Try standard Plane hit test first
        var hitResultList = frame.hitTest(tap)

        // If no real floor found, use Instant Placement
        if (hitResultList.isEmpty()) {
            hitResultList = frame.hitTestInstantPlacement(tap.x, tap.y, 2.0f)
        }

        val firstHit = hitResultList.firstOrNull() ?: return
        val trackable = firstHit.trackable

        if (anchors.size >= 10) {
            anchors[0].anchor.detach()
            anchors.removeAt(0)
        }

        val anchor = firstHit.createAnchor()
        val method = if (trackable is InstantPlacementPoint) trackable.trackingMethod
        else InstantPlacementPoint.TrackingMethod.FULL_TRACKING

        anchors.add(WrappedAnchor(anchor, trackable, method, anchor.pose.distance(camera.pose)))
    }

    // Helper to calculate distance between two 3D positions
    private fun Pose.distance(other: Pose): Float {
        return sqrt((tx()-other.tx()).pow(2) + (ty()-other.ty()).pow(2) + (tz()-other.tz()).pow(2))
    }
}
