package com.example.dolook.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * 对 MediaPipe Pose Landmarker 的轻量封装。
 *
 * 当前只暴露 LIVE_STREAM 模式（配合 CameraX 的 [ImageAnalysis] 使用）：
 * 调用 [detectLiveStream] 投递帧后，结果通过 [LandmarkerListener] 异步回调。
 *
 * 线程约束：MediaPipe 的 Task（尤其 GPU delegate 持有的 EGL 上下文）必须在创建它的
 * 线程上创建与调用，因此构造与 [detectLiveStream] 都应运行在同一个单线程上
 * （见 [com.example.dolook.ui.CameraScreen] 中的 analyzer executor）。
 *
 * delegate 策略：优先使用 [preferredDelegate]（默认 GPU），若设备不支持 GPU 或初始化
 * 失败会自动回退 CPU，避免直接不可用；回退结果由 [activeDelegate] 暴露。
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val preferredDelegate: Delegate = Delegate.GPU,
    private val modelAssetPath: String = MODEL_POSE_LANDMARKER_FULL,
    private val runningMode: RunningMode = RunningMode.LIVE_STREAM,
    private val minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    private val minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    private val minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    private val listener: LandmarkerListener? = null,
) {

    private var poseLandmarker: PoseLandmarker? = null

    /** 实际初始化成功的 delegate；null 表示尚未就绪或 GPU 与 CPU 均失败。 */
    var activeDelegate: Delegate? = null
        private set

    val isReady: Boolean
        get() = poseLandmarker != null

    init {
        if (runningMode == RunningMode.LIVE_STREAM && listener == null) {
            throw IllegalStateException(
                "listener must be set when runningMode is LIVE_STREAM."
            )
        }
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        // 依次尝试首选 delegate 与 CPU，任一成功即停止。
        val candidates = listOfNotNull(
            preferredDelegate,
            Delegate.CPU.takeIf { it != preferredDelegate },
        )
        for (candidate in candidates) {
            val landmarker = try {
                createLandmarker(candidate)
            } catch (t: Throwable) {
                // GPU delegate 不受支持、模型损坏或 native 库加载失败等都会走到这里。
                Log.e(TAG, "使用 $candidate delegate 初始化失败，尝试回退", t)
                continue
            }
            poseLandmarker = landmarker
            activeDelegate = candidate
            if (candidate != preferredDelegate) {
                Log.w(TAG, "$preferredDelegate delegate 不可用，已回退到 $candidate")
            }
            return
        }
        listener?.onError("Pose Landmarker 初始化失败：GPU 与 CPU delegate 均不可用")
    }

    private fun createLandmarker(delegate: Delegate): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setDelegate(delegate)
            .setModelAssetPath(modelAssetPath)
            .build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinTrackingConfidence(minPoseTrackingConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setRunningMode(runningMode)

        if (runningMode == RunningMode.LIVE_STREAM) {
            optionsBuilder
                .setResultListener(this::onLivestreamResult)
                .setErrorListener(this::onLivestreamError)
        }

        return PoseLandmarker.createFromOptions(context, optionsBuilder.build())
    }

    /**
     * 处理一帧来自 CameraX [ImageAnalysis] 的图像。
     *
     * 会把 YUV/RGBA 帧拷贝到 [Bitmap]，按传感器方向旋转（前置摄像头再做水平镜像），
     * 然后交给 MediaPipe 异步推理。函数内部负责关闭 [imageProxy]。
     *
     * @param isFrontCamera 前置摄像头时对图像做水平镜像，使坐标与预览一致。
     */
    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        require(runningMode == RunningMode.LIVE_STREAM) {
            "detectLiveStream() 只能在 RunningMode.LIVE_STREAM 下调用。"
        }

        val frameTime = SystemClock.uptimeMillis()
        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // CameraX 已配置为 RGBA_8888 输出，planes[0] 即 ARGB_8888 位图数据
        val bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        imageProxy.use {
            bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer)
        }

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, width.toFloat(), height.toFloat())
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, width, height, matrix, true
        )

        landmarker.detectAsync(BitmapImageBuilder(rotatedBitmap).build(), frameTime)
    }

    /**
     * 释放底层原生资源。之后不能再调用推理接口。
     *
     * 必须在创建该 Task 的同一线程上调用，否则 GPU delegate 的 EGL 上下文会崩溃。
     */
    fun clear() {
        poseLandmarker?.close()
        poseLandmarker = null
        activeDelegate = null
    }

    private fun onLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val inferenceTimeMs = SystemClock.uptimeMillis() - result.timestampMs()
        listener?.onResults(
            result = result,
            inputWidth = input.width,
            inputHeight = input.height,
            inferenceTimeMs = inferenceTimeMs,
        )
    }

    private fun onLivestreamError(error: RuntimeException) {
        Log.e(TAG, "Pose Landmarker 推理失败", error)
        listener?.onError(error.message ?: "Pose Landmarker 推理时发生未知错误")
    }

    /** 推理结果回调。 */
    interface LandmarkerListener {
        fun onResults(
            result: PoseLandmarkerResult,
            inputWidth: Int,
            inputHeight: Int,
            inferenceTimeMs: Long,
        )

        fun onError(message: String)
    }

    companion object {
        private const val TAG = "PoseLandmarkerHelper"

        const val MODEL_POSE_LANDMARKER_LITE = "pose_landmarker_lite.task"
        const val MODEL_POSE_LANDMARKER_FULL = "pose_landmarker_full.task"
        const val MODEL_POSE_LANDMARKER_HEAVY = "pose_landmarker_heavy.task"

        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5f
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5f
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5f
    }
}
