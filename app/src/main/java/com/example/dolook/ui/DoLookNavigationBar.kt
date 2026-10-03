package com.example.dolook.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * 参照 Apple 的「材质与层级」：导航栏是一层浮在内容之上的半透明材质，
 * 用一条极淡的高光边而非硬分割线；选中项用强调色 + 轻微弹性弹起表达，
 * 未选中项保持中性，避免整条 bar 争夺注意力。
 */
@Composable
fun DoLookNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 半透明材质：内容从其下方滑过时仍有层次感（此处用 surface 的 alpha 近似）。
    val material = colors.surface.copy(alpha = 0.92f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(material)
            .drawBehind {
                // 顶边一条渐隐高光，代替 1px 硬边框。
                val stroke = 0.6.dp.toPx()
                drawRect(
                    color = colors.outlineVariant.copy(alpha = 0.6f),
                    size = size.copy(height = stroke),
                )
            }
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                NavigationItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = {
                        if (tab != selectedTab) {
                            onTabSelected(tab)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
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
    // 选中时轻微弹起，呼应「被选中」的物理感；未选中缩小一档。
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = if (reduced) DoLookMotion.none() else DoLookMotion.pop(),
        label = "tabIconScale",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = DoLookMotion.standard(),
        label = "tabIndicator",
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
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
            .pressScale(interactionSource = interactionSource, pressedScale = 0.92f)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 选中态底衬：柔和的主色 tint，暗示「当前所在」。
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 30.dp)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.primary.copy(alpha = 0.14f)),
            )
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
