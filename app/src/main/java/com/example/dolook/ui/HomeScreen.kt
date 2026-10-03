package com.example.dolook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dolook.ui.components.pressScale
import com.example.dolook.ui.theme.DoLookMotion
import com.example.dolook.ui.theme.DoLookTheme
import com.example.dolook.ui.theme.LocalReducedMotion

/**
 * 首页：应用介绍与进入训练的入口。
 *
 * 设计要点：
 * - 元素分层进场（staged reveal），先「看见品牌」，再引导到唯一的主操作；
 * - 主按钮是全屏唯一的强调色，符合「Simplicity — 让核心目的最显眼」；
 * - 按压即缩放并伴随轻触觉反馈，响应发生在按下的一刻。
 */
@Composable
fun HomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // 首帧后再触发进场，让动画从「无」到「有」，而不是与首帧渲染竞争。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Reveal(appeared, delayMillis = 0, reduced = reduced) {
            AppMark()
        }

        Spacer(modifier = Modifier.height(28.dp))

        Reveal(appeared, delayMillis = 60, reduced = reduced) {
            Text(
                text = "DoLook",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Reveal(appeared, delayMillis = 120, reduced = reduced) {
            Text(
                text = "你做我看 · 运动姿态助手",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Reveal(appeared, delayMillis = 180, reduced = reduced) {
            Text(
                text = "实时识别你的动作，指出不到位的地方",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(44.dp))

        Reveal(appeared, delayMillis = 260, reduced = reduced) {
            Button(
                onClick = {
                    // 因果性：真正的动作（进入训练）才触发触觉，避免过度反馈。
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(interactionSource = interactionSource),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                interactionSource = interactionSource,
            ) {
                Text(
                    text = "开始运动",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Reveal(appeared, delayMillis = 320, reduced = reduced) {
            Text(
                text = "需要一台带摄像头的设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 品牌标记：一枚柔和的渐变方块，作为页面唯一的视觉锚点。 */
@Composable
private fun AppMark() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(88.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = primary,
                spotColor = primary,
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 分层进场的容器：淡入 + 轻微上移，用延迟制造层次。
 *
 * 仅在首屏出现时使用；按钮等可交互元素不套用，以免拖慢可操作性。
 * 开启「减弱动效」时退化为纯淡入。
 */
@Composable
private fun Reveal(
    visible: Boolean,
    delayMillis: Int,
    reduced: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 420, delayMillis = if (reduced) 0 else delayMillis),
        ) + slideInVertically(
            animationSpec = if (reduced) {
                DoLookMotion.none()
            } else {
                DoLookMotion.gentle()
            },
            initialOffsetY = { height -> height / 4 },
        ),
        exit = fadeOut(),
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DoLookTheme {
        HomeScreen(onStart = {})
    }
}
