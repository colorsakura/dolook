# DoLook

DoLook(你做我看) 是一款运动辅助软件，他能监控你的运动过程，指出动作不到位的地方。

## 姿态识别 (Pose Landmarker)

App 使用 **CameraX** 采集摄像头帧，交给 **MediaPipe Pose Landmarker** 做端上实时推理，
并把检测到的 33 个人体关键点绘制在预览画面之上。

### 架构

| 文件 | 职责 |
| --- | --- |
| `pose/PoseLandmarkerHelper.kt` | 封装 MediaPipe `PoseLandmarker`（LIVE_STREAM 模式）：把 CameraX 的 `ImageProxy` 转成 `MPImage` 并异步推理 |
| `ui/CameraScreen.kt` | 相机权限、CameraX `Preview` + `ImageAnalysis` 绑定、Compose UI 与状态栏 |
| `ui/PoseOverlayView.kt` | 自定义 `View`，按 FILL_CENTER 规则把归一化关键点映射到屏幕并绘制骨架 |

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
- 摄像头：当前固定后置；前置摄像头时传入 `isFrontCamera = true` 做水平镜像即可。

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

### 运行

需要一台带摄像头的真机（模拟器无法提供有效相机帧）：

```bash
./gradlew :app:installDebug
```
