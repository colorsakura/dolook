package com.example.dolook.pose.exercise

import com.example.dolook.pose.PoseLandmarks
import com.example.dolook.pose.PosePoint
import java.util.Locale
import kotlin.math.abs

/** 开合跳所处的动作阶段。 */
enum class JumpingJackPhase {
    /** 尚未识别到有效姿态。 */
    UNKNOWN,

    /** 双腿并拢、双手放于体侧的起始/收拢姿势。 */
    CLOSED,

    /** 双腿打开、双手上举的展开姿势。 */
    OPEN,
}

/**
 * 一次检测输出：计数、阶段、量化指标与给用户的提示。
 *
 * @param repetitionCount 已完成的完整开合次数。
 * @param phase 当前动作阶段。
 * @param legRatio 双脚水平间距 / 肩宽，用于衡量腿张开程度。
 * @param handsAboveShoulders 双手是否都高于同侧肩膀。
 * @param handsAboveHead 双手是否都高于鼻尖（约等于举过头顶）。
 * @param feedback 面向用户的实时提示。
 */
data class JumpingJackState(
    override val repetitionCount: Int = 0,
    val phase: JumpingJackPhase = JumpingJackPhase.UNKNOWN,
    val legRatio: Float = 0f,
    val handsAboveShoulders: Boolean = false,
    val handsAboveHead: Boolean = false,
    override val feedback: String = "准备开始",
) : ExerciseState {

    /** 展开阶段视为动作到位。 */
    override val isActive: Boolean
        get() = phase == JumpingJackPhase.OPEN

    override val detail: String
        get() = "腿开比例 ${String.format(Locale.US, "%.2f", legRatio)}"
}

/**
 * 开合跳动作检测器（纯逻辑，可在 JVM 上单元测试）。
 *
 * 判定思路：
 * - 用「双脚水平间距 / 肩宽」衡量腿部张开程度，避免相机远近与画面宽高比造成的偏差；
 * - 用「手腕是否高于肩膀/头顶」衡量手臂上举；
 * - 通过状态机在「收拢 → 展开 → 收拢」后计一次数，并使用连续稳定帧 + 最小相位
 *   持续时间做防抖，避免关键点抖动造成误计数。
 *
 * 所有方法均非线程安全，请在同一个线程上调用。
 */
class JumpingJackDetector(
    /** 关键点可见度低于该值时视为不可用。 */
    private val minVisibility: Float = 0.5f,
    /** 腿张开比例达到该值且双手上举，视为展开姿势。 */
    private val openLegRatio: Float = 1.3f,
    /** 腿张开比例低于该值且双手下垂，视为收拢姿势。 */
    private val closeLegRatio: Float = 0.8f,
    /** 相邻两次相位切换之间的最小间隔，防止高频抖动。 */
    private val minPhaseDurationMs: Long = 200L,
    /** 需要连续满足判据的帧数，达到后才切换相位。 */
    private val stableFrames: Int = 3,
) : ExerciseDetector {

    override val name: String = "开合跳"

    override var repetitionCount: Int = 0
        private set

    private var phase = JumpingJackPhase.UNKNOWN
    private var lastPhaseChangeMs: Long? = null
    private var openStreak = 0
    private var closedStreak = 0

    private var legRatio = 0f
    private var handsAboveShoulders = false
    private var handsAboveHead = false

    /** 清空计数与内部状态。 */
    override fun reset() {
        repetitionCount = 0
        phase = JumpingJackPhase.UNKNOWN
        lastPhaseChangeMs = null
        openStreak = 0
        closedStreak = 0
        legRatio = 0f
        handsAboveShoulders = false
        handsAboveHead = false
    }

    /**
     * 用一帧姿态更新检测器。
     *
     * @param points 33 个归一化关键点；数量不足或关键点不可见时返回等待提示。
     * @param timestampMs 该帧的时间戳（毫秒，需单调递增）。
     */
    override fun update(points: List<PosePoint>, timestampMs: Long): JumpingJackState {
        val metrics = measure(points)
        if (metrics == null) {
            openStreak = 0
            closedStreak = 0
            handsAboveShoulders = false
            handsAboveHead = false
            return state("请让全身进入画面")
        }

        legRatio = metrics.legRatio
        handsAboveShoulders = metrics.handsAboveShoulders
        handsAboveHead = metrics.handsAboveHead

        val legsOpen = metrics.legRatio >= openLegRatio
        val legsClosed = metrics.legRatio <= closeLegRatio
        val openPose = legsOpen && metrics.handsAboveShoulders
        val closedPose = legsClosed && !metrics.handsAboveShoulders

        openStreak = if (openPose) openStreak + 1 else 0
        closedStreak = if (closedPose) closedStreak + 1 else 0

        val canTransition = lastPhaseChangeMs
            ?.let { timestampMs - it >= minPhaseDurationMs }
            ?: true
        when (phase) {
            JumpingJackPhase.OPEN -> {
                if (closedStreak >= stableFrames && canTransition) {
                    phase = JumpingJackPhase.CLOSED
                    lastPhaseChangeMs = timestampMs
                    repetitionCount++
                }
            }

            JumpingJackPhase.UNKNOWN,
            JumpingJackPhase.CLOSED -> {
                if (openStreak >= stableFrames && canTransition) {
                    phase = JumpingJackPhase.OPEN
                    lastPhaseChangeMs = timestampMs
                }
            }
        }

        return state(feedbackFor(legsOpen, legsClosed))
    }

    private fun feedbackFor(legsOpen: Boolean, legsClosed: Boolean): String = when {
        phase == JumpingJackPhase.UNKNOWN -> "准备开始"
        // 腿已打开但手没跟上
        legsOpen && !handsAboveShoulders -> "双手举过头顶"
        // 手已上举但腿没打开
        handsAboveHead && !legsOpen -> "双腿再打开一些"
        phase == JumpingJackPhase.OPEN -> "保持，然后合拢"
        legsClosed -> "合拢，准备下一次"
        else -> "继续"
    }

    private fun state(feedback: String) = JumpingJackState(
        repetitionCount = repetitionCount,
        phase = phase,
        legRatio = legRatio,
        handsAboveShoulders = handsAboveShoulders,
        handsAboveHead = handsAboveHead,
        feedback = feedback,
    )

    private fun measure(points: List<PosePoint>): Metrics? {
        if (points.size < PoseLandmarks.COUNT) return null

        val nose = points[PoseLandmarks.NOSE]
        val leftShoulder = points[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = points[PoseLandmarks.RIGHT_SHOULDER]
        val leftWrist = points[PoseLandmarks.LEFT_WRIST]
        val rightWrist = points[PoseLandmarks.RIGHT_WRIST]
        val leftAnkle = points[PoseLandmarks.LEFT_ANKLE]
        val rightAnkle = points[PoseLandmarks.RIGHT_ANKLE]

        val required = listOf(
            nose, leftShoulder, rightShoulder,
            leftWrist, rightWrist, leftAnkle, rightAnkle,
        )
        if (required.any { it.visibility < minVisibility }) return null

        // 用水平间距而非欧氏距离，避免图像宽高比导致的尺度失真。
        val shoulderSeparation = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderSeparation < MIN_SHOULDER_SEPARATION) return null

        val ankleSeparation = abs(leftAnkle.x - rightAnkle.x)
        val legRatio = ankleSeparation / shoulderSeparation

        val handsAboveShoulders =
            leftWrist.y < leftShoulder.y && rightWrist.y < rightShoulder.y
        val handsAboveHead =
            leftWrist.y < nose.y && rightWrist.y < nose.y

        return Metrics(legRatio, handsAboveShoulders, handsAboveHead)
    }

    private data class Metrics(
        val legRatio: Float,
        val handsAboveShoulders: Boolean,
        val handsAboveHead: Boolean,
    )

    private companion object {
        /** 肩宽过窄说明人物侧身或距离过远，此时测量不可靠。 */
        const val MIN_SHOULDER_SEPARATION = 0.02f
    }
}
