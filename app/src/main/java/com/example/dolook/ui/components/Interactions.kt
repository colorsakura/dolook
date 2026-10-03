package com.example.dolook.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.dolook.ui.theme.DoLookMotion
import com.example.dolook.ui.theme.LocalReducedMotion

/**
 * 按压即缩放的反馈。
 *
 * Apple 强调「响应发生在按下的一刻，而不是松开时」：控件被按下的瞬间就应给出
 * 视觉反馈。这里通过 [collectIsPressedAsState] 读取按下状态，用高刚度弹簧把
 * 缩放推到 [pressedScale]，松开即回弹到 1，触感直接、无延迟。
 *
 * @param interactionSource 与点击控件共享的交互源。
 * @param pressedScale 按下时的缩放比例，默认 0.96（约 4% 的收缩，克制但可感知）。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (reduced) DoLookMotion.none() else DoLookMotion.snappy(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
