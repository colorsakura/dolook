package com.example.dolook.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 动效设计系统。
 *
 * Apple 用「damping ratio（阻尼比）+ response（响应时间）」两个设计师友好的参数
 * 取代物理三要素（mass / stiffness / damping）。Compose 的 [spring] 暴露的是
 * dampingRatio + stiffness，二者可以这样对应：
 * - dampingRatio 一一对应：1.0 临界阻尼（不回弹），< 1.0 会有回弹；
 * - response 与 stiffness 成反比（response 越小越「脆」），
 *   因此用 Compose 的 [Spring] 预设近似映射：
 *   response≈0.2s → Medium，0.3s → MediumLow，0.4s → Low。
 *
 * 默认一律使用临界阻尼（无回弹）；只有在手势本身带速度时（甩动、拖拽释放）
 * 才换成略带回弹的 [momentum]。
 */
object DoLookMotion {

    /** 默认 UI：临界阻尼、无回弹，稳重不喧宾夺主。 */
    fun <T> standard(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** 手势带速度后的落位：略带回弹，让「甩出去」有物理感。 */
    fun <T> momentum(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    /** 更慢、更柔和的进出场。 */
    fun <T> gentle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    /** 快速响应（按压反馈、选中弹起），响应时间约 0.2s。 */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** 选中态弹起：带一点点回弹，呼应「被选中」的物理感。 */
    fun <T> pop(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)

    /** 降低动效时的替代：仅做短促的淡入淡出。 */
    fun <T> fade(): FiniteAnimationSpec<T> = tween(durationMillis = 200)

    /** 完全关闭动效（瞬时切换）。 */
    fun <T> none(): FiniteAnimationSpec<T> = snap()
}

/**
 * 当前是否应「减弱动效」。
 *
 * Android 没有 CSS 的 `prefers-reduced-motion`，但系统的「移除动画」辅助功能
 * 会把 [Settings.Global.ANIMATOR_DURATION_SCALE] 置为 0，语义等价。
 * 由 [DoLookTheme] 统一注入，供各组件读取。
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * 读取并持续观察系统的动画时长缩放系数。
 *
 * 返回 `true` 表示用户要求减弱动效，此时空间位移类动画应替换为淡入淡出或瞬时切换。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(context.isAnimationDisabled()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = context.isAnimationDisabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun Context.isAnimationDisabled(): Boolean =
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
