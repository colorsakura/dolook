package com.example.dolook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.example.dolook.ui.AppTab
import com.example.dolook.ui.CameraScreen
import com.example.dolook.ui.DoLookNavigationBar
import com.example.dolook.ui.HomeScreen
import com.example.dolook.ui.ProfileScreen
import com.example.dolook.ui.theme.DoLookMotion
import com.example.dolook.ui.theme.DoLookTheme
import com.example.dolook.ui.theme.LocalReducedMotion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoLookTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DoLookApp()
                }
            }
        }
    }
}

/**
 * 应用外壳与导航。
 *
 * 空间一致性（Spatial consistency）：
 * - 首页 / 我的 之间是同级切换，用淡入淡出（iOS 标签栏的做法），不引入方向性位移；
 * - 训练是「模态任务」，从底部弹起并让背景略微后退、压暗，退出时沿原路返回；
 * - 模态展示时不停用输入，训练全程保持可操作。
 */
@Composable
private fun DoLookApp() {
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // 背景内容只跟随非训练标签，避免打开训练时下层被清空。
    var contentTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    LaunchedEffect(tab) {
        if (tab != AppTab.Training) contentTab = tab
    }

    val reduced = LocalReducedMotion.current
    val trainingVisible = tab == AppTab.Training

    // 背景后退：模态出现时轻微缩小，强化「浮层在前、内容在后」的层级。
    val backgroundScale by animateFloatAsState(
        targetValue = if (trainingVisible) 0.94f else 1f,
        animationSpec = DoLookMotion.standard(),
        label = "backgroundScale",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backgroundScale
                    scaleY = backgroundScale
                },
            bottomBar = {
                DoLookNavigationBar(
                    selectedTab = tab,
                    onTabSelected = { tab = it },
                )
            },
        ) { innerPadding ->
            Crossfade(
                targetState = contentTab,
                animationSpec = DoLookMotion.standard(),
                label = "tabCrossfade",
            ) { current ->
                when (current) {
                    AppTab.Home -> HomeScreen(
                        onStart = { tab = AppTab.Training },
                        modifier = Modifier.padding(innerPadding),
                    )

                    AppTab.Profile -> ProfileScreen(
                        modifier = Modifier.padding(innerPadding),
                    )

                    AppTab.Training -> Unit
                }
            }
        }

        // 模态遮罩：压暗背景，把注意力收到训练画面上。
        AnimatedVisibility(
            visible = trainingVisible,
            enter = fadeIn(DoLookMotion.fade()),
            exit = fadeOut(DoLookMotion.fade()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
        }

        // 训练模态：从底部弹起，退出时沿同一路径返回。
        AnimatedVisibility(
            visible = trainingVisible,
            enter = if (reduced) {
                fadeIn(DoLookMotion.fade())
            } else {
                slideInVertically(
                    animationSpec = DoLookMotion.momentum<IntOffset>(),
                    initialOffsetY = { height -> height },
                ) + fadeIn(DoLookMotion.standard())
            },
            exit = if (reduced) {
                fadeOut(DoLookMotion.fade())
            } else {
                slideOutVertically(
                    animationSpec = DoLookMotion.standard<IntOffset>(),
                    targetOffsetY = { height -> height },
                ) + fadeOut(DoLookMotion.standard())
            },
        ) {
            CameraScreen(onExit = { tab = contentTab })
        }
    }
}
