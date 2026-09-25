package com.example.dolook.pose.exercise

import com.example.dolook.pose.PosePoint

/**
 * 通用动作检测器契约。
 *
 * 实现类接收连续的姿态关键点流，输出重复次数与实时反馈。新增动作（如深蹲、
 * 俯卧撑）只需实现该接口，相机与 UI 层无需改动。
 *
 * 实现不要求线程安全，请在单一（通常是主）线程上调用。
 */
interface ExerciseDetector {

    /** 动作名称，用于 UI 展示，例如「开合跳」。 */
    val name: String

    /** 已完成的重复次数。 */
    val repetitionCount: Int

    /**
     * 用一帧姿态更新检测器。
     *
     * @param points 33 个归一化关键点。
     * @param timestampMs 该帧的时间戳（毫秒，需单调递增）。
     * @return 更新后的通用状态，供 UI 统一渲染。
     */
    fun update(points: List<PosePoint>, timestampMs: Long): ExerciseState

    /** 清空计数与内部状态。 */
    fun reset()
}

/**
 * 动作检测器输出的公共状态。
 *
 * 只暴露所有动作都具备的语义字段；动作特有的量化指标可通过 [detail]
 * 以文本形式补充展示。
 */
interface ExerciseState {

    /** 已完成的重复次数。 */
    val repetitionCount: Int

    /** 面向用户的实时提示。 */
    val feedback: String

    /** 当前是否处于「动作到位」的阶段，UI 可据此高亮反馈。 */
    val isActive: Boolean

    /** 可选的补充信息（如量化指标），用于调试展示；无则为 null。 */
    val detail: String?
        get() = null
}

/** 尚未开始或尚未识别到动作时的占位状态。 */
object EmptyExerciseState : ExerciseState {
    override val repetitionCount: Int = 0
    override val feedback: String = "准备开始"
    override val isActive: Boolean = false
}
