package com.example.dolook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

/**
 * 在预览之上绘制 Pose Landmarker 输出的骨架。
 *
 * 输入坐标是归一化的（0..1），需要映射到 View 像素。PreviewView 使用
 * FILL_CENTER（居中裁剪），因此使用 max 比例缩放并做居中偏移。
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val pointPaint = Paint().apply {
        color = Color.parseColor("#FFEB3B")
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private var result: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        this.result = poseLandmarkerResult
        this.imageWidth = imageWidth.coerceAtLeast(1)
        this.imageHeight = imageHeight.coerceAtLeast(1)
        invalidate()
    }

    fun clear() {
        result = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val poseResult = result ?: return
        if (width == 0 || height == 0) return

        val scale = max(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight,
        )
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        for (landmarks in poseResult.landmarks()) {
            // 绘制关键点
            for (landmark in landmarks) {
                if (landmark.visibility().orElse(1f) < VISIBILITY_THRESHOLD) continue
                canvas.drawPoint(
                    offsetX + landmark.x() * imageWidth * scale,
                    offsetY + landmark.y() * imageHeight * scale,
                    pointPaint,
                )
            }

            // 绘制骨架连线
            for (connection in PoseLandmarker.POSE_LANDMARKS) {
                connection ?: continue
                val start = landmarks.getOrNull(connection.start()) ?: continue
                val end = landmarks.getOrNull(connection.end()) ?: continue
                if (start.visibility().orElse(1f) < VISIBILITY_THRESHOLD) continue
                if (end.visibility().orElse(1f) < VISIBILITY_THRESHOLD) continue
                canvas.drawLine(
                    offsetX + start.x() * imageWidth * scale,
                    offsetY + start.y() * imageHeight * scale,
                    offsetX + end.x() * imageWidth * scale,
                    offsetY + end.y() * imageHeight * scale,
                    linePaint,
                )
            }
        }
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8f
        private const val VISIBILITY_THRESHOLD = 0.5f
    }
}
