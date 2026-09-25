package com.example.dolook.pose

/**
 * 与 MediaPipe 解耦的归一化人体关键点。
 *
 * 坐标取值范围为 0..1，原点在图像左上角（x 向右、y 向下），
 * [visibility] 表示该关键点被模型检测到的置信度。
 *
 * 使用独立的数据类而不是直接依赖 MediaPipe 的 `NormalizedLandmark`，
 * 便于对动作检测逻辑做纯 JVM 单元测试。
 */
data class PosePoint(
    val x: Float,
    val y: Float,
    val visibility: Float = 1f,
)
