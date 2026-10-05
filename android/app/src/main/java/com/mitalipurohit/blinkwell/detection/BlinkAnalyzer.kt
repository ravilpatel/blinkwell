package com.mitalipurohit.blinkwell.detection

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class BlinkAnalyzer(
    private val blinkDetector: BlinkDetector
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val primaryFace = faces[0]
                    val leftProb = primaryFace.leftEyeOpenProbability
                    val rightProb = primaryFace.rightEyeOpenProbability
                    val eulerX = primaryFace.headEulerAngleX
                    val eulerY = primaryFace.headEulerAngleY

                    blinkDetector.onFrameProcessed(
                        faceDetected = true,
                        leftEyeProb = leftProb,
                        rightEyeProb = rightProb,
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
            }
            .addOnFailureListener {
                blinkDetector.onFrameProcessed(
                    faceDetected = false,
                    leftEyeProb = null,
                    rightEyeProb = null,
                    timestamp = currentTimestamp
                )
            }
            .addOnCompleteListener {
                // Hard requirement: Always close imageProxy immediately to prevent buffer starvation and leaks
                imageProxy.close()
            }
    }

    fun release() {
        try {
            detector.close()
        } catch (ignored: Exception) {
        }
    }
}
