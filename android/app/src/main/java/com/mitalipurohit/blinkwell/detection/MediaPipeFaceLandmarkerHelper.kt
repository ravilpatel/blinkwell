package com.mitalipurohit.blinkwell.detection

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

/**
 * Singleton manager for Google MediaPipe Tasks Vision FaceLandmarker.
 * Reuses the native TFLite pipeline and model weights across camera duty-cycle
 * bind/unbind cycles to avoid cold-start interpreter re-allocation latency.
 */
object MediaPipeFaceLandmarkerHelper {
    private const val TAG = "MediaPipeFaceHelper"
    private const val MODEL_NAME = "face_landmarker.task"

    @Volatile
    private var faceLandmarker: FaceLandmarker? = null

    @Synchronized
    fun getOrCreate(context: Context): FaceLandmarker? {
        if (faceLandmarker == null) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_NAME)
                    .setDelegate(Delegate.CPU) // CPU with XNNPACK provides rock-solid stability across older Mali/Adreno GPUs
                    .build()

                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setNumFaces(1)
                    .setOutputFaceBlendshapes(true)
                    .setOutputFacialTransformationMatrixes(true)
                    .setRunningMode(RunningMode.IMAGE)
                    .build()

                faceLandmarker = FaceLandmarker.createFromOptions(context.applicationContext, options)
                Log.i(TAG, "MediaPipe FaceLandmarker successfully initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaPipe FaceLandmarker: ${e.message}", e)
            }
        }
        return faceLandmarker
    }

    @Synchronized
    fun release() {
        try {
            faceLandmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing FaceLandmarker", e)
        } finally {
            faceLandmarker = null
        }
    }
}
