package com.example.dolook.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DoLook 调色板。
 *
 * 参考 Apple HIG 的语义化配色：颜色承载含义（主操作 / 成功 / 警告 / 错误），
 * 且在浅色与深色下分别调校，而不是简单地把同一支颜色调暗。
 * 这里不再使用默认的紫色模板，改为以「系统蓝」为主色，让「运动激励」保持克制、
 * 专业，避免高饱和色在大面积画面上造成视觉疲劳。
 */

// MARK: - 浅色（Light）

val BlueLight = Color(0xFF007AFF)          // 主操作 / 选中态
val BlueContainerLight = Color(0xFFD6E8FF)
val OnBlueContainerLight = Color(0xFF00325C)

val GreenLight = Color(0xFF34C759)         // 成功 / 动作达标
val GreenContainerLight = Color(0xFFD8F6DE)
val OnGreenContainerLight = Color(0xFF0B3D1B)

val OrangeLight = Color(0xFFFF9500)        // 提醒 / 注意力
val OrangeContainerLight = Color(0xFFFFEACC)
val OnOrangeContainerLight = Color(0xFF4A2800)

val RedLight = Color(0xFFFF3B30)           // 错误 / 动作不到位
val RedContainerLight = Color(0xFFFFDAD6)
val OnRedContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFF2F2F7)    // 分组背景
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1C1C1E)
val SurfaceVariantLight = Color(0xFFE5E5EA)
val OnSurfaceVariantLight = Color(0xFF6C6C70)
val OutlineLight = Color(0xFFC7C7CC)
val OutlineVariantLight = Color(0xFFE5E5EA)

// MARK: - 深色（Dark）

val BlueDark = Color(0xFF0A84FF)
val BlueContainerDark = Color(0xFF00325C)
val OnBlueContainerDark = Color(0xFFD6E8FF)

val GreenDark = Color(0xFF30D158)
val GreenContainerDark = Color(0xFF0B3D1B)
val OnGreenContainerDark = Color(0xFFD8F6DE)

val OrangeDark = Color(0xFFFF9F0A)
val OrangeContainerDark = Color(0xFF4A2800)
val OnOrangeContainerDark = Color(0xFFFFEACC)

val RedDark = Color(0xFFFF453A)
val RedContainerDark = Color(0xFF93000A)
val OnRedContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF000000)     // OLED 真黑，材质层次靠 surface 区分
val SurfaceDark = Color(0xFF1C1C1E)
val OnSurfaceDark = Color(0xFFF2F2F7)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val OnSurfaceVariantDark = Color(0xFFAEAEB2)
val OutlineDark = Color(0xFF38383A)
val OutlineVariantDark = Color(0xFF2C2C2E)

/** 相机界面的场景遮罩（scrim）——用于让浮层文字在任何画面上都可读。 */
val CameraScrim = Color(0xCC000000)
val CameraScrimSoft = Color(0x66000000)
