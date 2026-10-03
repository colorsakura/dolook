# AGENTS.md

## 技术栈要求

本项目固定使用 **Kotlin + Jetpack Compose (Material 3)** 进行 Android 开发。所有新增代码必须遵守以下约定。

### 必选

- **语言**：Kotlin。不得新增 `.java` 源文件。
- **UI**：Jetpack Compose（`@Composable`），主题使用 Material 3（`androidx.compose.material3`）。
- **Activity 基类**：`ComponentActivity`，通过 `setContent { ... }` 挂载 Compose 内容。
- **依赖管理**：统一在 `gradle/libs.versions.toml` 声明版本与别名，在模块 `build.gradle.kts` 中用 `libs.*` 引用，禁止硬编码版本号。
- **Compose 依赖**：通过 `androidx.compose:compose-bom` 统一版本，不在单个依赖中写 Compose 版本。

### 禁止

- 禁止使用 XML 布局（`res/layout/**`）搭建界面。
- 禁止使用 `findViewById` / `setContentView` / ViewBinding / DataBinding / Kotlin synthetics。
- 禁止引入 `androidx.appcompat` 等基于 View 体系的 UI 框架。

### 例外

- 需要原生绘制或与 Compose 之外的框架（如 CameraX 的 `PreviewView`、自定义 `Canvas` 覆盖层）交互时，允许通过 `AndroidView` 在 Compose 中桥接，但界面结构仍由 Compose 组织。

### 当前版本基线（`gradle/libs.versions.toml`）

- Kotlin `2.2.10`
- AGP `9.4.1`（内置 Kotlin 编译）
- Compose BOM `2026.02.01`
- Compose Compiler 插件 `org.jetbrains.kotlin.plugin.compose`

升级上述版本时需同步更新本节。
