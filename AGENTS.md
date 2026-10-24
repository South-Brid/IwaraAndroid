# iwaraPhone

iwara.tv 的安卓第三方客户端（个人自用），Kotlin + Jetpack Compose + Media3。

## 用户偏好

- 用户自己测试 App：改完只需编译出包（必要时装到已运行的模拟器），不要编写验证 / 测试脚本
- 每次出包都递增版本号（见下方"版本号"）

## 环境

- 开发工具和缓存一律放 E 盘，不要装到 C 盘
- Android SDK：`E:\Jet\AndroidStudioSDK`；JDK：Android Studio 自带 `E:\Jet\Android Studio\jbr`
- 用户环境变量已设置：`ANDROID_HOME`、`JAVA_HOME`（上面两个路径）、`GRADLE_USER_HOME=E:\Jet\GradleHome`、`ANDROID_AVD_HOME=E:\Jet\AndroidAVD`；PATH 含 `platform-tools`、`emulator`、`%JAVA_HOME%\bin`
- `local.properties`（不进 git）里 `sdk.dir` 指向上面的 SDK
- Gradle 和 Maven 依赖走国内镜像（见 `gradle-wrapper.properties`、`settings.gradle.kts`），官方源作为备用
- 模拟器：`Medium_Phone_API_37.0`，存放在 `E:\Jet\AndroidAVD`，需带代理启动才能访问 iwara：
  `emulator -avd Medium_Phone_API_37.0 -http-proxy http://127.0.0.1:7897`

## 命令（PowerShell）

- 编译安装：`.\gradlew.bat installDebug`
- 测试包：`.\gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\IwaraPhone-<版本>-debug.apk`
  - 包名 `com.example.iwaraphone.debug`、桌面名「IwaraPhone 测试版」，可与正式版同时安装（数据和登录互相独立）
- 版本号：改 `app\build.gradle.kts` 顶部的 `appVersionCode`（每次发版 +1）/ `appVersionName`；「我的」页底部显示当前版本
- 发布包：`.\gradlew.bat assembleRelease` → `app\build\outputs\apk\release\IwaraPhone-<版本>-release.apk`
  - 签名读取根目录 `keystore.properties`（指向 `E:\Android\keystore\iwaraphone.jks`，别名 `iwaraphone`），两者都不进 git；`E:\Android\keystore\` 下另存了一份 `keystore.properties`，备份时整个目录一起备份。丢失签名文件将无法覆盖升级已安装的 App
  - 当前签名于 2026-10-24（1.3.0）重新生成，证书 SHA-256 `54eb48e5…20280864`；更早的正式版签名不同，需卸载后安装
  - release 开启了 R8 全量优化（模板默认的 `packageScope` 局部优化会导致启动崩溃，不要加回去）
- 图标生成：`python tools\gen_icons.py`（源图 `art\icon_source.png`）

## 架构约定

```
data/
  model/                 数据模型、枚举、JSON 解析（不依赖网络）
  remote/Network         OkHttp 客户端，与 WebView 共用 Cookie（cf_clearance）和 User-Agent
  remote/ApiClient       请求入口：请求头、令牌注入（依赖 TokenProvider 接口）、网络失败重试、错误映射
  remote/CloudflareGate  请求被 Cloudflare 拦截时请求界面弹出验证，用户通过后 ApiClient 重试
  local/Session          本地存储（令牌、搜索历史）
  *Repository            按业务拆分：Auth（登录状态唯一来源 AuthState）/ Video / User / Tag
ui/
  common/                通用组件：Paged<T> 分页状态、VideoGrid、RemoteImage、OptionRow、FollowButton、CloudflareDialog
  nav/Navigator          二级页面栈，每个页面独立 ViewModelStore（出栈即释放）
  home/ search/ follow/ profile/ video/ user/ history/   按功能分包，Screen + ViewModel 放一起
```

- 新接口加到对应 Repository，UI 不直接调用 ApiClient
- 接口细节和坑见 `docs/iwara-api.md`（如搜索 `type` 必须是 `videos`）
- 不要用 `withTimeout` 包裹会被 `Paged` 捕获的请求：超时异常是 CancellationException，会被静默吞掉；用 `withTimeoutOrNull` 再抛普通异常
- 模拟器的 goldfish 硬解码器解不了部分 H.264，`PlayerFactory` 里已把它排到最后
- 播放器控制层是自研的 Compose 组件（`ui/video/VideoPlayer.kt`），PlayerView 只负责渲染（`useController = false`）
- 离开视频页必须 `stopForLeave()` 停止预缓冲，否则后台下载会占满带宽，其它页面请求失败
- 观看历史 `data/local/HistoryStore` 只存本机；VideoViewModel 每 5 秒及离开页面时保存进度
