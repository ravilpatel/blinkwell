package com.mitalipurohit.blinkwell.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.mitalipurohit.blinkwell.BlinkWellApp

/**
 * CameraX ImageAnalysis analyzer backed by Google MediaPipe Tasks Vision FaceLandmarker.
 *
 * Implements a Dual-Signal Fusion Pipeline:
 * 1. Geometric Eye Aspect Ratio (EAR) from 12 key 3D eyelid landmarks (immune to lens glare & refraction).
 * 2. High-precision ARKit-compatible facial blendshapes (eyeBlinkLeft, eyeBlinkRight).
 * 3. Head pose tracking (pitch & yaw) for gaze orientation gating.
 */
class BlinkAnalyzer(
    private val context: Context = BlinkWellApp.instance,
    private val blinkDetector: BlinkDetector
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BlinkAnalyzer"

        // Landmark indices for Left Eye EAR (anatomical left = screen right in front camera)
        private const val L_INNER_CANTHUS = 362
        private const val L_OUTER_CANTHUS = 263
        private const val L_TOP_1 = 385
        private const val L_BOTTOM_1 = 380
        private const val L_TOP_2 = 387
        private const val L_BOTTOM_2 = 373

        // Landmark indices for Right Eye EAR (anatomical right = screen left in front camera)
        private const val R_OUTER_CANTHUS = 33
        private const val R_INNER_CANTHUS = 133
        private const val R_TOP_1 = 160
        private const val R_BOTTOM_1 = 144
        private const val R_TOP_2 = 158
        private const val R_BOTTOM_2 = 153

        // Standard EAR boundaries: eye fully closed typically <= 0.10, eye open typically >= 0.25
        private const val EAR_CLOSED_THRESHOLD = 0.10f
        private const val EAR_OPEN_THRESHOLD = 0.25f
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        var bitmap: Bitmap? = null

        try {
            val landmarker = MediaPipeFaceLandmarkerHelper.getOrCreate(context)
            if (landmarker == null) {
                blinkDetector.onFrameProcessed(
                    faceDetected = false,
                    leftEyeProb = null,
                    rightEyeProb = null,
                    timestamp = currentTimestamp
                )
                return
            }

            // Convert CameraX ImageProxy to Bitmap
            bitmap = imageProxy.toBitmap()

            // Build MPImage and set orientation from CameraX rotation
            val mpImage = BitmapImageBuilder(bitmap).build()
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()

            // Synchronous detection on the dedicated CameraX worker thread
            val result = landmarker.detect(mpImage, imageProcessingOptions)

            val faceLandmarksList = result.faceLandmarks()
            Log.d(TAG, "Frame: ${imageProxy.width}x${imageProxy.height}, rot=${imageProxy.imageInfo.rotationDegrees}, faces=${faceLandmarksList.size}")

            if (faceLandmarksList.isNotEmpty()) {
                val landmarks = faceLandmarksList[0]

                // 1. Calculate Geometric Eye Aspect Ratio (EAR)
                val earLeft = computeEar(
                    landmarks = landmarks,
                    p1 = L_INNER_CANTHUS,
                    p4 = L_OUTER_CANTHUS,
                    p2 = L_TOP_1,
                    p6 = L_BOTTOM_1,
                    p3 = L_TOP_2,
                    p5 = L_BOTTOM_2
                )

                val earRight = computeEar(
                    landmarks = landmarks,
                    p1 = R_OUTER_CANTHUS,
                    p4 = R_INNER_CANTHUS,
                    p2 = R_TOP_1,
                    p6 = R_BOTTOM_1,
                    p3 = R_TOP_2,
                    p5 = R_BOTTOM_2
                )

                // Normalize EAR into standard 0.0 (closed) to 1.0 (open) score
                val earNormLeft = ((earLeft - EAR_CLOSED_THRESHOLD) / (EAR_OPEN_THRESHOLD - EAR_CLOSED_THRESHOLD)).coerceIn(0.0f, 1.0f)
                val earNormRight = ((earRight - EAR_CLOSED_THRESHOLD) / (EAR_OPEN_THRESHOLD - EAR_CLOSED_THRESHOLD)).coerceIn(0.0f, 1.0f)

                // 2. Extract Blendshapes (eyeBlinkLeft, eyeBlinkRight)
                var blendOpenLeft: Float? = null
                var blendOpenRight: Float? = null

                if (result.faceBlendshapes().isPresent) {
                    val blendshapesList = result.faceBlendshapes().get()
                    if (blendshapesList.isNotEmpty()) {
                        val categories = blendshapesList[0]
                        for (category in categories) {
                            when (category.categoryName()) {
                                "eyeBlinkLeft" -> {
                                    // MediaPipe blendshape: 1.0 = shut closed, 0.0 = wide open
                                    blendOpenLeft = (1.0f - category.score()).coerceIn(0.0f, 1.0f)
                                }
                                "eyeBlinkRight" -> {
                                    blendOpenRight = (1.0f - category.score()).coerceIn(0.0f, 1.0f)
                                }
                            }
                        }
                    }
                }

                // 3. Dual-Signal Fusion: 3D Blendshapes + Geometric EAR
                // When an eye closes, the lower signal captures closure promptly (vital for spectacles & glare)
                val finalLeftProb = if (blendOpenLeft != null) {
                    val minSig = Math.min(blendOpenLeft, earNormLeft)
                    val avgSig = (blendOpenLeft + earNormLeft) / 2.0f
                    (0.6f * minSig + 0.4f * avgSig).coerceIn(0.0f, 1.0f)
                } else {
                    earNormLeft
                }

                val finalRightProb = if (blendOpenRight != null) {
                    val minSig = Math.min(blendOpenRight, earNormRight)
                    val avgSig = (blendOpenRight + earNormRight) / 2.0f
                    (0.6f * minSig + 0.4f * avgSig).coerceIn(0.0f, 1.0f)
                } else {
                    earNormRight
                }

                // 4. Head Pose Tracking: True 3D rotation from MediaPipe facialTransformationMatrixes
                var eulerX: Float? = null
                var eulerY: Float? = null

                if (result.facialTransformationMatrixes().isPresent && result.facialTransformationMatrixes().get().isNotEmpty()) {
                    val matrix = result.facialTransformationMatrixes().get()[0]
                    val r02 = matrix[8]
                    val r12 = matrix[9]
                    val r22 = matrix[10]

                    eulerX = Math.toDegrees(Math.asin(-r12.toDouble().coerceIn(-1.0, 1.0))).toFloat()
                    eulerY = Math.toDegrees(Math.atan2(r02.toDouble(), r22.toDouble())).toFloat()
                }

                Log.i(TAG, "Face: probL=$finalLeftProb, probR=$finalRightProb, blendL=$blendOpenLeft, blendR=$blendOpenRight, earL=$earLeft, earR=$earRight, pitch=$eulerX, yaw=$eulerY")

                blinkDetector.onFrameProcessed(
                    faceDetected = true,
                    leftEyeProb = finalLeftProb,
                    rightEyeProb = finalRightProb,
                    timestamp = currentTimestamp,
                    headEulerAngleX = eulerX,
                    headEulerAngleY = eulerY
                )
            } else {
                blinkDetector.onFrameProcessed(
                    faceDetected = false,
                    leftEyeProb = null,
                    rightEyeProb = null,
                    timestamp = currentTimestamp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during MediaPipe frame analysis: ${e.message}", e)
            blinkDetector.onFrameProcessed(
                faceDetected = false,
                leftEyeProb = null,
                rightEyeProb = null,
                timestamp = currentTimestamp
            )
        } finally {
            try {
                bitmap?.recycle()
            } catch (ignored: Exception) {}
            // Hard requirement: Always close imageProxy immediately to prevent buffer starvation and leaks
            imageProxy.close()
        }
    }

    private fun computeEar(
        landmarks: List<NormalizedLandmark>,
        p1: Int, p4: Int,
        p2: Int, p6: Int,
        p3: Int, p5: Int
    ): Float {
        val dP2P6 = distance(landmarks[p2], landmarks[p6])
        val dP3P5 = distance(landmarks[p3], landmarks[p5])
        val dP1P4 = distance(landmarks[p1], landmarks[p4]).coerceAtLeast(0.001f)

        return (dP2P6 + dP3P5) / (2.0f * dP1P4)
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    fun release() {
        // Singleton helper manages model lifecycle across camera bind cycles
    }
}
