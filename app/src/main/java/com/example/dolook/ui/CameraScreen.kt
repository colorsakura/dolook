package com.example.dolook.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.dolook.pose.PoseLandmarkerHelper
import com.example.dolook.pose.PosePoint
import com.example.dolook.pose.exercise.EmptyExerciseState
import com.example.dolook.pose.exercise.ExerciseDetector
import com.example.dolook.pose.exercise.ExerciseState
import com.example.dolook.pose.exercise.JumpingJackDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 一帧推理结果及其输入尺寸，用于驱动叠加层绘制。 */
private data class PoseFrame(
    val result: PoseLandmarkerResult,
    val imageWidth: Int,
    val imageHeight: Int,
    val inferenceTimeMs: Long,
)

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current

    // 运动辅助需要持续观察画面，避免系统无操作后自动息屏。
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            PoseCameraContent()
        } else {
            PermissionRequest(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

@Composable
private fun PoseCameraContent() {
    val context = LocalContext.current
    // MainActivity 本身即 LifecycleOwner，直接复用可避免额外依赖。
    val lifecycleOwner = context as LifecycleOwner

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var frame by remember { mutableStateOf<PoseFrame?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // 以通用接口持有检测器，后续接入新动作时 UI 层无需改动。
    val detector: ExerciseDetector = remember { JumpingJackDetector() }
    var exerciseState by remember { mutableStateOf<ExerciseState>(EmptyExerciseState) }

    // CPU delegate 允许在主线程创建、后台线程推理；创建失败会在 onError 回调。
    val helper = remember {
        PoseLandmarkerHelper(
            context = context.applicationContext,
            modelAssetPath = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
            listener = object : PoseLandmarkerHelper.LandmarkerListener {
                override fun onResults(
                    result: PoseLandmarkerResult,
                    inputWidth: Int,
                    inputHeight: Int,
                    inferenceTimeMs: Long,
                ) {
                    mainExecutor.execute {
                        frame = PoseFrame(result, inputWidth, inputHeight, inferenceTimeMs)
                        val landmarks = result.landmarks().firstOrNull()
                        val points = landmarks?.map {
                            PosePoint(it.x(), it.y(), it.visibility().orElse(1f))
                        }
                        exerciseState = detector.update(
                            points = points ?: emptyList(),
                            timestampMs = System.currentTimeMillis(),
                        )
                    }
                }

                override fun onError(message: String) {
                    mainExecutor.execute { errorMessage = message }
                }
            },
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView 模式，确保 Compose 中叠加的骨架层可见。
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner, previewView, lensFacing) {
        // 切换摄像头时先清空上一路的结果，避免骨架短暂错位。
        frame = null
        runCatching {
            bindCameraUseCases(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                analysisExecutor = analysisExecutor,
                helper = helper,
                lensFacing = lensFacing,
            )
        }.onFailure { error ->
            errorMessage = "相机启动失败: ${error.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            helper.clear()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        AndroidView(
            factory = { PoseOverlayView(it) },
            update = { overlay ->
                val current = frame
                if (current == null) {
                    overlay.clear()
                } else {
                    overlay.setResults(
                        poseLandmarkerResult = current.result,
                        imageWidth = current.imageWidth,
                        imageHeight = current.imageHeight,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        StatusBar(
            frame = frame,
            exerciseName = detector.name,
            exerciseState = exerciseState,
            errorMessage = errorMessage,
            cameraLabel = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "前置" else "后置",
            modifier = Modifier.align(Alignment.TopStart),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    detector.reset()
                    exerciseState = EmptyExerciseState
                },
            ) {
                Text("重置计数")
            }
            FilledTonalButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                },
            ) {
                Text("切换摄像头")
            }
        }
    }
}

@Composable
private fun StatusBar(
    frame: PoseFrame?,
    exerciseName: String,
    exerciseState: ExerciseState,
    errorMessage: String?,
    cameraLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val landmarkCount = frame?.result?.landmarks()?.sumOf { it.size } ?: 0
                val feedbackColor = if (exerciseState.isActive) {
                    Color(0xFF7CFF7C)
                } else {
                    Color.White.copy(alpha = 0.9f)
                }
                Text(
                    text = "$exerciseName · ${exerciseState.repetitionCount} 次 · $cameraLabel",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = exerciseState.feedback,
                    color = feedbackColor,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val detailText = buildString {
                    append("关键点: $landmarkCount")
                    exerciseState.detail?.let { append(" · $it") }
                    frame?.let { append(" · 推理耗时: ${it.inferenceTimeMs} ms") }
                }
                Text(
                    text = if (frame == null) "正在等待画面…" else detailText,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "需要相机权限才能进行姿态识别",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = onRequest,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("授予相机权限")
        }
    }
}

/**
 * 创建并绑定 CameraX 的 [Preview] 与 [ImageAnalysis] 用例。
 *
 * [ImageAnalysis] 配置为 RGBA_8888 输出（MediaPipe 要求），并采用
 * KEEP_ONLY_LATEST 背压策略以避免帧堆积导致的延迟。
 */
private suspend fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analysisExecutor: Executor,
    helper: PoseLandmarkerHelper,
    lensFacing: Int,
) {
    val cameraProvider = context.awaitCameraProvider()

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()
    val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT

    val preview = Preview.Builder()
        .build()
        .also { it.surfaceProvider = previewView.surfaceProvider }

    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                // 前置摄像头对输入做水平镜像，使关键点坐标与镜像显示的预览一致。
                helper.detectLiveStream(imageProxy, isFrontCamera = isFrontCamera)
            }
        }

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        imageAnalysis,
    )
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        continuation.invokeOnCancellation { future.cancel(true) }
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (t: Throwable) {
                    continuation.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
