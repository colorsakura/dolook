package com.example.dolook.pose.exercise

import com.example.dolook.pose.PoseLandmarks
import com.example.dolook.pose.PosePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpingJackDetectorTest {

    @Test
    fun `implements generic exercise detector contract`() {
        val detector: ExerciseDetector = newDetector()

        assertEquals("开合跳", detector.name)
        assertEquals(0, detector.repetitionCount)
    }

    @Test
    fun `initial pose is unknown with zero count`() {
        val detector = newDetector()

        val state = detector.update(closedPose(), 0L)

        assertEquals(0, state.repetitionCount)
        assertEquals(JumpingJackPhase.UNKNOWN, state.phase)
        assertFalse(state.handsAboveShoulders)
    }

    @Test
    fun `opens phase after stable open pose`() {
        val detector = newDetector()
        var time = 0L

        repeat(STABLE_FRAMES) {
            detector.update(openPose(), time)
            time += FRAME_INTERVAL_MS
        }

        val state = detector.update(openPose(), time)
        assertEquals(JumpingJackPhase.OPEN, state.phase)
        assertEquals(0, state.repetitionCount)
        assertTrue(state.handsAboveHead)
    }

    @Test
    fun `counts one repetition after a full open-close cycle`() {
        val detector = newDetector()
        var time = 0L

        repeat(STABLE_FRAMES) { detector.update(closedPose(), time); time += FRAME_INTERVAL_MS }
        repeat(STABLE_FRAMES) { detector.update(openPose(), time); time += FRAME_INTERVAL_MS }
        val finalState = repeatFrames(STABLE_FRAMES) {
            val state = detector.update(closedPose(), time)
            time += FRAME_INTERVAL_MS
            state
        }

        assertEquals(1, finalState.repetitionCount)
        assertEquals(JumpingJackPhase.CLOSED, finalState.phase)
    }

    @Test
    fun `counts multiple repetitions`() {
        val detector = newDetector()
        var time = 0L

        repeat(3) {
            repeat(STABLE_FRAMES) { detector.update(openPose(), time); time += FRAME_INTERVAL_MS }
            repeat(STABLE_FRAMES) { detector.update(closedPose(), time); time += FRAME_INTERVAL_MS }
        }

        assertEquals(3, detector.repetitionCount)
    }

    @Test
    fun `does not count when open pose is not stable enough`() {
        val detector = newDetector()
        var time = 0L

        // 只有一帧展开，未达到 stableFrames
        detector.update(openPose(), time); time += FRAME_INTERVAL_MS
        repeat(STABLE_FRAMES + 1) { detector.update(closedPose(), time); time += FRAME_INTERVAL_MS }

        assertEquals(0, detector.repetitionCount)
        assertEquals(JumpingJackPhase.UNKNOWN, detector.update(closedPose(), time).phase)
    }

    @Test
    fun `hands only does not trigger phase switch`() {
        val detector = newDetector()
        var time = 0L

        val handsUpButLegsClosed = closedPose().toMutableList().apply {
            this[PoseLandmarks.LEFT_WRIST] = PosePoint(0.30f, 0.05f)
            this[PoseLandmarks.RIGHT_WRIST] = PosePoint(0.70f, 0.05f)
        }

        repeat(STABLE_FRAMES + 2) {
            val state = detector.update(handsUpButLegsClosed, time)
            time += FRAME_INTERVAL_MS
            // 手臂已上举但腿未打开，应停留在起始阶段并给出提示
            if (it >= STABLE_FRAMES) {
                assertEquals(JumpingJackPhase.UNKNOWN, state.phase)
                assertTrue(state.handsAboveShoulders)
            }
        }

        assertEquals(0, detector.repetitionCount)
    }

    @Test
    fun `low visibility frames are ignored`() {
        val detector = newDetector()

        val invisible = closedPose().map { it.copy(visibility = 0.1f) }
        val state = detector.update(invisible, 0L)

        assertEquals("请让全身进入画面", state.feedback)
        assertEquals(JumpingJackPhase.UNKNOWN, state.phase)
    }

    @Test
    fun `reset clears count and phase`() {
        val detector = newDetector()
        var time = 0L

        repeat(STABLE_FRAMES) { detector.update(openPose(), time); time += FRAME_INTERVAL_MS }
        repeat(STABLE_FRAMES) { detector.update(closedPose(), time); time += FRAME_INTERVAL_MS }
        assertEquals(1, detector.repetitionCount)

        detector.reset()

        assertEquals(0, detector.repetitionCount)
        assertEquals(JumpingJackPhase.UNKNOWN, detector.update(closedPose(), time).phase)
    }

    private fun newDetector() = JumpingJackDetector(
        minPhaseDurationMs = 0L,
        stableFrames = STABLE_FRAMES,
    )

    private inline fun repeatFrames(times: Int, block: () -> JumpingJackState): JumpingJackState {
        var state = JumpingJackState()
        repeat(times) { state = block() }
        return state
    }

    /** 双腿并拢、双手放于体侧的起始姿势。肩距 0.2，踝距 0.04 → 比例 0.2。 */
    private fun closedPose(): List<PosePoint> {
        val points = MutableList(PoseLandmarks.COUNT) { PosePoint(0.5f, 0.5f, 1f) }
        points[PoseLandmarks.NOSE] = PosePoint(0.50f, 0.08f)
        points[PoseLandmarks.LEFT_SHOULDER] = PosePoint(0.40f, 0.30f)
        points[PoseLandmarks.RIGHT_SHOULDER] = PosePoint(0.60f, 0.30f)
        points[PoseLandmarks.LEFT_WRIST] = PosePoint(0.35f, 0.50f)
        points[PoseLandmarks.RIGHT_WRIST] = PosePoint(0.65f, 0.50f)
        points[PoseLandmarks.LEFT_ANKLE] = PosePoint(0.48f, 0.90f)
        points[PoseLandmarks.RIGHT_ANKLE] = PosePoint(0.52f, 0.90f)
        return points
    }

    /** 双腿打开、双手举过头顶的展开姿势。肩距 0.2，踝距 0.4 → 比例 2.0。 */
    private fun openPose(): List<PosePoint> = closedPose().toMutableList().apply {
        this[PoseLandmarks.LEFT_WRIST] = PosePoint(0.30f, 0.05f)
        this[PoseLandmarks.RIGHT_WRIST] = PosePoint(0.70f, 0.05f)
        this[PoseLandmarks.LEFT_ANKLE] = PosePoint(0.30f, 0.90f)
        this[PoseLandmarks.RIGHT_ANKLE] = PosePoint(0.70f, 0.90f)
    }

    private companion object {
        const val STABLE_FRAMES = 3
        const val FRAME_INTERVAL_MS = 50L
    }
}
