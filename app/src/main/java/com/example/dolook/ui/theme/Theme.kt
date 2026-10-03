package com.example.dolook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 浅色配色。基于 Apple 语义化系统色，且每个角色都在浅色下单独调校。
 */
private val LightColorScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = SurfaceLight,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = OnBlueContainerLight,

    secondary = GreenLight,
    onSecondary = SurfaceLight,
    secondaryContainer = GreenContainerLight,
    onSecondaryContainer = OnGreenContainerLight,

    tertiary = OrangeLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = OrangeContainerLight,
    onTertiaryContainer = OnOrangeContainerLight,

    error = RedLight,
    onError = SurfaceLight,
    errorContainer = RedContainerLight,
    onErrorContainer = OnRedContainerLight,

    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = BackgroundLight,
    surfaceContainerHigh = SurfaceVariantLight,
    surfaceContainerHighest = SurfaceVariantLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

/** 深色配色。以 OLED 真黑为背景，用 surface 层级区分「浮层材质」。 */
private val DarkColorScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = SurfaceLight,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = OnBlueContainerDark,

    secondary = GreenDark,
    onSecondary = SurfaceDark,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = OnGreenContainerDark,

    tertiary = OrangeDark,
    onTertiary = SurfaceDark,
    tertiaryContainer = OrangeContainerDark,
    onTertiaryContainer = OnOrangeContainerDark,

    error = RedDark,
    onError = SurfaceLight,
    errorContainer = RedContainerDark,
    onErrorContainer = OnRedContainerDark,

    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceVariantDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = SurfaceVariantDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/**
 * DoLook 主题。
 *
 * 有意关闭 Material You 动态取色：Apple 的「Familiarity / Craft」要求品牌与语义
 * 颜色稳定可预期，动态取色会让「成功=绿、错误=红」等语义随壁纸漂移。
 * 如确需跟随壁纸，可将 [dynamicColor] 打开。
 *
 * 同时把系统「减弱动效」开关注入 [LocalReducedMotion]，供组件替换位移动画。
 */
@Composable
fun DoLookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val reducedMotion = rememberReducedMotion()

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
