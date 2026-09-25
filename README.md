# DoLook

DoLook(你做我看) 是一款运动辅助软件，他能监控你的运动过程，指出动作不到位的地方。

## 姿态识别 (Pose Landmarker)

App 使用 **CameraX** 采集摄像头帧，交给 **MediaPipe Pose Landmarker** 做端上实时推理，
并把检测到的 33 个人体关键点绘制在预览画面之上。

### 架构

| 文件                             | 职责                                                                                        |
|--------------------------------|-------------------------------------------------------------------------------------------|
| `pose/PoseLandmarkerHelper.kt` | 封装 MediaPipe `PoseLandmarker`（LIVE_STREAM 模式）：把 CameraX 的 `ImageProxy` 转成 `MPImage` 并异步推理 |
| `ui/CameraScreen.kt`           | 相机权限、CameraX `Preview` + `ImageAnalysis` 绑定、Compose UI 与状态栏                               |
| `ui/PoseOverlayView.kt`        | 自定义 `View`，按 FILL_CENTER 规则把归一化关键点映射到屏幕并绘制骨架                                              |

### 数据流

```
CameraX ImageAnalysis (RGBA_8888, 640x480)
        │  analysisExecutor (单线程)
        ▼
PoseLandmarkerHelper.detectLiveStream()   // 旋转 / 镜像 → Bitmap → MPImage
        │  PoseLandmarker.detectAsync()
        ▼
LandmarkerListener.onResults()            // 切回主线程
        │
        ▼
PoseOverlayView.setResults()              // 绘制关键点与骨架
```

### 关键配置

- `ImageAnalysis`：`STRATEGY_KEEP_ONLY_LATEST` 背压 + `OUTPUT_IMAGE_FORMAT_RGBA_8888`（MediaPipe 要求）。
- 模型：默认 `pose_landmarker_full.task`（更精准）。可在
  `PoseLandmarkerHelper` 的 `modelAssetPath` 中切换为 `lite` / `heavy`。
- 摄像头：底部按钮可在前后摄像头间切换；切前置时对输入做水平镜像，使关键点坐标与预览保持一致。

### 推理加速与线程模型

- **delegate**：默认优先启用 GPU（`Delegate.GPU`），设备不支持或初始化失败时自动回退到 CPU；
  当前生效的 delegate 会显示在状态栏（`开合跳 · 0 次 · 后置 · GPU`）。
- **线程约束**：MediaPipe 的 Task（尤其 GPU delegate 的 EGL 上下文）必须在创建它的线程上使用，
  因此 `PoseLandmarkerHelper` 的构造、`detectLiveStream()` 与 `clear()` 统一在 CameraX 的
  单线程 analyzer executor 上执行（见 `CameraScreen.kt`）。
- **回退逻辑**：`PoseLandmarkerHelper.setupPoseLandmarker()` 按「首选 delegate → CPU」顺序尝试，
  任一成功即停止，两者都失败才通过 `onError` 上报。

### 模型下载

模型未纳入版本控制之外的分发流程时，可用以下命令重新获取：

```bash
# full（默认，约 9 MB）
curl -L -o app/src/main/assets/pose_landmarker_full.task \
  https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task

# lite（更快，约 5.5 MB）
curl -L -o app/src/main/assets/pose_landmarker_lite.task \
  https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
```

## 开合跳动作检测

基于 Pose Landmarker 的 33 个关键点，实时识别开合跳并计数。

### 判定方法

| 特征               | 计算                 | 说明                    |
|------------------|--------------------|-----------------------|
| 腿张开比例 `legRatio` | 双脚踝水平间距 / 肩宽       | 用水平间距而非欧氏距离，消除画面宽高比影响 |
| 双手上举             | 双腕 y 是否高于同侧肩膀 / 鼻尖 | 判断手臂是否举起              |

状态机：`UNKNOWN/CLOSED → OPEN → CLOSED`，每完成一次「收拢 → 展开 → 收拢」计数 +1。
采用「连续稳定帧（默认 3）+ 最小相位间隔（默认 200ms）」防抖，避免关键点抖动造成误计数。

### 代码位置

| 文件                                            | 职责                         |
|-----------------------------------------------|----------------------------|
| `pose/exercise/ExerciseDetector.kt`          | 通用 `ExerciseDetector` / `ExerciseState` 契约 |
| `pose/exercise/JumpingJackDetector.kt`        | 开合跳实现（纯逻辑状态机，无 Android 依赖） |
| `pose/PosePoint.kt` / `pose/PoseLandmarks.kt` | 与 MediaPipe 解耦的关键点模型与索引    |
| `app/src/test/.../JumpingJackDetectorTest.kt` | 9 个单元测试（含接口契约）          |

### 扩展新动作

相机与 UI 层只依赖通用接口：

```kotlin
interface ExerciseDetector {
    val name: String
    val repetitionCount: Int
    fun update(points: List<PosePoint>, timestampMs: Long): ExerciseState
    fun reset()
}
```

新增动作（例如深蹲）只需：

1. 实现 `ExerciseDetector`，在 `update()` 中返回自定义的 `ExerciseState` 实现；
2. 将 `CameraScreen` 中的 `JumpingJackDetector()` 换为新实现即可，其余代码无需改动。

动作特有的量化指标可通过 `ExerciseState.detail` 以文本形式补充展示。

### 可调参数

`JumpingJackDetector` 构造函数暴露：`openLegRatio`(1.3)、`closeLegRatio`(0.8)、
`minVisibility`(0.5)、`stableFrames`(3)、`minPhaseDurationMs`(200)。

> 该方法假设人物正面朝向相机。侧身时肩宽测量不可靠，会暂停判定并提示「请让全身进入画面」。

### 运行

需要一台带摄像头的真机（模拟器无法提供有效相机帧）：

```bash
./gradlew :app:installDebug
```
