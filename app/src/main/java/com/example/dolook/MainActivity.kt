package com.example.dolook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.dolook.ui.AppTab
import com.example.dolook.ui.CameraScreen
import com.example.dolook.ui.DoLookNavigationBar
import com.example.dolook.ui.HomeScreen
import com.example.dolook.ui.ProfileScreen
import com.example.dolook.ui.theme.DoLookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoLookTheme {
                var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (tab) {
                        // 训练页为沉浸式的全屏相机界面，不显示底部导航栏。
                        AppTab.Training -> CameraScreen(onExit = { tab = AppTab.Home })
                        else -> Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                DoLookNavigationBar(
                                    selectedTab = tab,
                                    onTabSelected = { tab = it },
                                )
                            },
                        ) { innerPadding ->
                            when (tab) {
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
                }
            }
        }
    }
}
