# iwaraPhone

iwara.tv 的安卓第三方客户端（个人自用），Kotlin + Jetpack Compose + Media3。

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
- 发布包：`.\gradlew.bat assembleRelease` → `app\build\outputs\apk\release\app-release.apk`
  - 签名读取根目录 `keystore.properties`（指向 `E:\Android\keystore\iwaraphone.jks`），两者都不进 git；丢失签名文件将无法覆盖升级已安装的 App，需自行备份
  - release 开启了 R8 全量优化；改动后要用 release 包跑一遍 `tools\test_features.py`（模板默认的 `packageScope` 局部优化会导致启动崩溃，不要加回去）
- 功能测试（模拟器上逐项点击验证，21 项，约 7 分钟）：`python -u tools\test_features.py`
  - 只跑部分用例：`python -u tools\test_features.py 视频 历史`（按用例名前缀筛选）
  - 登录用例需要环境变量 `IW_EMAIL` / `IW_PWD`，**不要把账号写进任何文件**
  - 失败截图在 `build\test\fail_*.png`；先确认不是本机代理抖动（logcat 里 chromium `handshake failed ... net_error -100`），再查代码
- 在 WebView 页面里调接口（探索 API 用）：`python tools\cdp_fetch.py "/videos?limit=2"`
- Python 依赖：`websocket-client`

## 架构约定

```
bridge/WebBridge      浏览器通道：后台 WebView 通过 Cloudflare 验证，接口请求在页面内 fetch
data/
  model/              数据模型、枚举、JSON 解析（不依赖网络）
  remote/ApiClient    请求入口：请求头、令牌注入（依赖 TokenProvider 接口）、错误映射
  local/Session       本地存储（令牌、搜索历史）
  *Repository         按业务拆分：Auth（登录状态唯一来源 AuthState）/ Video / User / Tag
ui/
  common/             通用组件：Paged<T> 分页状态、VideoGrid、RemoteImage、OptionRow
  nav/Navigator       二级页面栈，每个页面独立 ViewModelStore（出栈即释放）
  home/ search/ follow/ profile/ video/ user/ debug/   按功能分包，Screen + ViewModel 放一起
```

- 新接口加到对应 Repository，UI 不直接调用 ApiClient（debug 页除外）
- 接口细节和坑见 `docs/iwara-api.md`（如搜索 `type` 必须是 `videos`）
- 不要用 `withTimeout` 包裹会被 `Paged` 捕获的请求：超时异常是 CancellationException，会被静默吞掉；用 `withTimeoutOrNull` 再抛普通异常
- 模拟器的 goldfish 硬解码器解不了部分 H.264，`PlayerFactory` 里已把它排到最后
- 播放器控制层是自研的 Compose 组件（`ui/video/VideoPlayer.kt`），PlayerView 只负责渲染（`useController = false`）
- 离开视频页必须 `stopForLeave()` 停止预缓冲，否则后台下载会占满带宽，其它页面请求失败
- 观看历史 `data/local/HistoryStore` 只存本机；VideoViewModel 每 5 秒及离开页面时保存进度
