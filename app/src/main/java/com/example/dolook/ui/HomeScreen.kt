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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dolook.ui.components.pressScale
import com.example.dolook.ui.theme.DoLookMotion
import com.example.dolook.ui.theme.DoLookTheme
import com.example.dolook.ui.theme.LocalReducedMotion

/**
 * 首页：应用介绍与进入训练的入口。
 *
 * 构图采用 Apple 常见的「英雄区 + 行动区」编辑式排版，而不是把所有元素堆在正中：
 * - 顶部左对齐的品牌区建立识别与信任；
 * - 底部把「价值点 → 唯一主操作 → 补充说明」按视线顺序排好，
 *   让用户一眼知道这是什么、能做什么、下一步点哪里；
 * - 主按钮是全屏唯一的实心强调色，符合「Simplicity — 让核心目的最显眼」。
 */
@Composable
fun HomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // 首帧后再触发进场，让动画从「无」到「有」，而不是与首帧渲染竞争。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Box(modifier = modifier.fillMaxSize()) {
        // 品牌光晕：一枚极淡的径向渐变，为纯色背景引入纵深，不喧宾夺主。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .size(420.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.primary.copy(alpha = 0.16f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Hero(reduced = reduced, appeared = appeared)

            Column {
                Reveal(appeared, delayMillis = 240, reduced = reduced) {
                    FeatureRow()
                }
                Spacer(modifier = Modifier.height(24.dp))
                Reveal(appeared, delayMillis = 300, reduced = reduced) {
                    Button(
                        onClick = {
                            // 因果性：真正的动作（进入训练）才触发触觉，避免过度反馈。
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStart()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .pressScale(interactionSource = interactionSource)
                            .shadow(
                                elevation = 18.dp,
                                shape = RoundedCornerShape(18.dp),
                                ambientColor = colors.primary,
                                spotColor = colors.primary,
                            ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        interactionSource = interactionSource,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "开始运动",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Reveal(appeared, delayMillis = 360, reduced = reduced) {
                    Text(
                        text = "需要一台带摄像头的设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 顶部品牌区：标记、名称与一句话定位，全部左对齐以形成稳定的阅读起点。 */
@Composable
private fun Hero(reduced: Boolean, appeared: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.Start) {
        Reveal(appeared, delayMillis = 0, reduced = reduced) {
            AppMark()
        }
        Spacer(modifier = Modifier.height(28.dp))
        Reveal(appeared, delayMillis = 60, reduced = reduced) {
            Text(
                text = "DoLook",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Reveal(appeared, delayMillis = 120, reduced = reduced) {
            Text(
                text = "你做我看 · 运动姿态助手",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Reveal(appeared, delayMillis = 180, reduced = reduced) {
            Text(
                text = "实时识别你的动作，指出不到位的地方。",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** 价值点：用一组轻量 chip 说明「能做什么」，比长句更容易被扫读。 */
@Composable
private fun FeatureRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeatureChip("实时识别")
        FeatureChip("逐次计数")
        FeatureChip("本地处理")
    }
}

@Composable
private fun FeatureChip(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** 品牌标记：渐变圆角方块 + 顶部高光，读起来像一枚有厚度的实体图标。 */
@Composable
private fun AppMark() {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .size(96.dp)
            .shadow(
                elevation = 26.dp,
                shape = shape,
                ambientColor = colors.primary,
                spotColor = colors.primary,
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        // 顶部高光：模拟光从上方打在材质上的反射，增强「实体感」。
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = 0.30f),
                            0.5f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colors.onPrimary,
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
