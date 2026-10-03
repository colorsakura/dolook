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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.dolook.ui.components.pressScale
import com.example.dolook.ui.theme.CameraScrim
import com.example.dolook.ui.theme.CameraScrimSoft
import com.example.dolook.ui.theme.DoLookMotion
import com.example.dolook.ui.theme.LocalReducedMotion
import com.google.mediapipe.tasks.core.Delegate
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
fun CameraScreen(
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
            PoseCameraContent(onExit = onExit)
        } else {
            PermissionRequest(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

@Composable
private fun PoseCameraContent(onExit: () -> Unit) {
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

    // GPU delegate 要求 Task 在创建它的线程上使用，因此把 helper 的构造也放到
    // analyzer executor 上，与 detectLiveStream() 保持同一线程。
    var helper by remember { mutableStateOf<PoseLandmarkerHelper?>(null) }

    LaunchedEffect(Unit) {
        helper = analysisExecutor.runAsync {
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
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView 模式，确保 Compose 中叠加的骨架层可见。
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner, previewView, lensFacing, helper) {
        // 等 helper 在 analyzer 线程上建好后再绑相机，避免首帧空跑。
        val currentHelper = helper ?: return@LaunchedEffect
        // 切换摄像头时先清空上一路的结果，避免骨架短暂错位。
        frame = null
        runCatching {
            bindCameraUseCases(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                analysisExecutor = analysisExecutor,
                helper = currentHelper,
                lensFacing = lensFacing,
            )
        }.onFailure { error ->
            errorMessage = "相机启动失败: ${error.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // clear() 必须与创建/推理同线程；shutdown() 会等待已提交任务完成。
            analysisExecutor.execute { helper?.clear() }
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

        StatusCard(
            frame = frame,
            exerciseName = detector.name,
            repetitionCount = exerciseState.repetitionCount,
            exerciseState = exerciseState,
            errorMessage = errorMessage,
            cameraLabel = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "前置" else "后置",
            delegateLabel = helper?.activeDelegate?.let(::delegateLabel) ?: "…",
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraControlButton(
                icon = Icons.Filled.Close,
                contentDescription = "退出训练",
                onClick = onExit,
            )
            CameraControlButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "重置计数",
                onClick = {
                    detector.reset()
                    exerciseState = EmptyExerciseState
                },
            )
            CameraControlButton(
                label = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "后置" else "前置",
                contentDescription = "切换摄像头",
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                },
            )
        }
    }
}

/**
 * 顶部状态浮层。
 *
 * 用半透明材质而非纯色条：既保证在任意画面上文字可读，又不完全遮挡训练画面。
 * 计数变化时用一次弹起强调「完成一次」这一有意义的时刻，并伴随触觉反馈。
 */
@Composable
private fun StatusCard(
    frame: PoseFrame?,
    exerciseName: String,
    repetitionCount: Int,
    exerciseState: ExerciseState,
    errorMessage: String?,
    cameraLabel: String,
    delegateLabel: String,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val countPulse = remember { Animatable(1f) }

    // 因果性 + 节制：只有「完成一次」才触发触觉与弹起，首帧 0 次不触发。
    LaunchedEffect(repetitionCount) {
        if (repetitionCount > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!reduced) {
                countPulse.snapTo(1.22f)
                countPulse.animateTo(1f, DoLookMotion.pop())
            }
        }
    }

    val feedbackColor by animateColorAsState(
        targetValue = if (exerciseState.isActive) {
            Color(0xFF30D158)
        } else {
            Color.White.copy(alpha = 0.9f)
        },
        animationSpec = DoLookMotion.standard(),
        label = "feedbackColor",
    )

    Surface(
        color = CameraScrim,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF453A),
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exerciseName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$cameraLabel · $delegateLabel",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = repetitionCount.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displaySmall.copy(
                        // 等宽数字，计数跳动时宽度不抖。
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier.graphicsLayer {
                        scaleX = countPulse.value
                        scaleY = countPulse.value
                    },
                )
                Text(
                    text = "次",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                )
            }

            Text(
                text = exerciseState.feedback,
                color = feedbackColor,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )

            val landmarkCount = frame?.result?.landmarks()?.sumOf { it.size } ?: 0
            val detailText = buildString {
                append("关键点: $landmarkCount")
                exerciseState.detail?.let { append(" · $it") }
                frame?.let { append(" · 推理耗时: ${it.inferenceTimeMs} ms") }
            }
            Text(
                text = if (frame == null) "正在等待画面…" else detailText,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 相机界面底部的圆形控制按钮。
 *
 * 半透明圆形底衬让按钮在任何画面上都清晰，同时不打断画面；按下即缩放并给出触觉。
 * 切换摄像头按钮直接显示「将切换到的目标」，让操作结果可预期。
 */
@Composable
private fun CameraControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(60.dp)
            .pressScale(interactionSource = interactionSource, pressedScale = 0.9f)
            .clip(CircleShape)
            .background(CameraScrimSoft)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = "DoLook 需要访问相机才能进行姿态识别，画面仅在设备本地处理。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onRequest,
            modifier = Modifier
                .padding(top = 24.dp)
                .pressScale(interactionSource = interactionSource),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            interactionSource = interactionSource,
        ) {
            Text("授予相机权限")
        }
    }
}

private fun delegateLabel(delegate: Delegate): String =
    when (delegate) {
        Delegate.GPU -> "GPU"
        Delegate.CPU -> "CPU"
        else -> delegate.name
    }

/** 在指定 [Executor] 上执行 [block] 并挂起等待结果。 */
private suspend fun <T> Executor.runAsync(block: () -> T): T =
    suspendCancellableCoroutine { continuation ->
        execute {
            val result = runCatching(block)
            if (continuation.isActive) {
                result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) },
                )
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
