package com.example.dolook.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.dolook.ui.components.pressScale
import com.example.dolook.ui.theme.DoLookMotion
import com.example.dolook.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

/** 底部导航栏的目的地。 */
enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("首页", Icons.Filled.Home),
    Training("训练", Icons.Filled.PlayArrow),
    Profile("我的", Icons.Filled.Person),
}

/**
 * DoLook 底部导航栏。
 *
 * 参照 Apple 的「材质与层级」：
 * - 做成浮在内容之上的半透明胶囊，而不是贴底通栏 —— 内容可以从它四周透出，
 *   材质厚度由圆角、细高光边与投影共同建立；
 * - 选中态是一枚会在 tab 间平滑滑动的指示器（共享元素），而不是各画各的，
 *   位移方向本身就在传达「我从哪里去了哪里」；
 * - 点击时图标有一次轻微回弹，呼应「被选中」的物理感。
 */
@Composable
fun DoLookNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 胶囊半高 = 内容高 / 2 + 上下内边距。圆角取半高，左右两端才是真正的半圆
    // （若用 percent=50，x/y 半径分别按宽高计算，会变成椭圆角）。
    val barContentHeight = 52.dp
    val barVerticalPadding = 6.dp
    val pillRadius = barContentHeight / 2 + barVerticalPadding
    val shape = RoundedCornerShape(pillRadius)

    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    // 整条栏的回弹：点击任意 tab（无论是否切换）都让胶囊像被按下一样弹一下，
    // 使导航栏读作一个整体，而不是互不相干的三个按钮。
    val barScale = remember { Animatable(1f) }
    val bounceBar: () -> Unit = {
        if (!reduced) {
            scope.launch {
                barScale.snapTo(0.96f)
                barScale.animateTo(1f, DoLookMotion.momentum())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = barScale.value
                    scaleY = barScale.value
                }
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                ),
            shape = shape,
            color = colors.surface.copy(alpha = 0.90f),
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.7f)),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(barVerticalPadding),
            ) {
                val count = AppTab.entries.size
                val cellWidth = maxWidth / count
                val selectedIndex = selectedTab.ordinal

                // 共享指示器：在等宽单元之间平滑滑动。
                val indicatorOffset by animateDpAsState(
                    targetValue = cellWidth * selectedIndex,
                    animationSpec = DoLookMotion.momentum(),
                    label = "tabIndicatorOffset",
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(cellWidth)
                        .height(barContentHeight)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.primary.copy(alpha = 0.14f)),
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    AppTab.entries.forEach { tab ->
                        NavigationItem(
                            tab = tab,
                            selected = tab == selectedTab,
                            onClick = {
                                if (tab != selectedTab) {
                                    onTabSelected(tab)
                                }
                                bounceBar()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(barContentHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = DoLookMotion.standard(),
        label = "tabTint",
    )

    // 选中瞬间的回弹：先略微收缩再弹出，像被按下去又弹回来。
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected, reduced) {
        when {
            reduced -> bounce.snapTo(1f)
            selected -> {
                bounce.snapTo(0.86f)
                bounce.animateTo(1.12f, DoLookMotion.pop())
                bounce.animateTo(1f, DoLookMotion.snappy())
            }

            else -> bounce.animateTo(0.94f, DoLookMotion.standard())
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .pressScale(interactionSource = interactionSource, pressedScale = 0.94f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(23.dp)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                },
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
