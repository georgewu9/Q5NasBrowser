# Q5 NAS 浏览器（海美迪 Q5 / Android 5.1.1）

一个极简的局域网 NAS 浏览器，用于解决海美迪 Q5 自带"媒体中心 → 网上邻居 → 自动搜索"
扫不到现代 NAS 的问题。它不依赖老旧的 NetBIOS 广播浏览，而是：

1. **主动扫描局域网**（探测 445/139 端口）发现 SMB 设备；
2. 用 **jcifs-ng** 走 **SMB1 ~ SMB3** 列出共享与目录（兼容 Buffalo 与 WD My Cloud Home）；
3. 点击文件时，在本地起一个 **HTTP 代理**把 SMB 文件流式喂给系统播放器（含海美迪自带高清播放器），
   播放器无需懂 SMB 协议。

## 功能
- 按钮「扫描局域网NAS」：扫描同网段 SMB 设备并列出 IP；
- 点击设备 → 列出共享目录；
- 点击共享/文件夹 → 逐层进入子目录；
- 点击视频文件 → 调用系统播放器播放（支持拖动进度，已处理 Range 请求）；
- 「设置账号」页：填写 NAS 用户名/密码，**留空则匿名/访客访问**。

## 构建 APK

### 方式一：本地 Android Studio（推荐，最简单）
1. 用 Android Studio 打开本目录；
2. 菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)；
3. 产物在 `app/build/outputs/apk/debug/app-debug.apk`。

### 方式二：GitHub Actions 云端出包
1. 把本仓库推到 GitHub；
2. 在仓库 Actions 页手动运行 `Build Q5NasBrowser APK`；
3. 运行完成后下载 Artifact `q5nasbrowser-debug-apk`（里面是 `app-debug.apk`）。

### 方式三：本地命令行（已装 Gradle/Android SDK）
```
gradle assembleDebug
```

## 安装到 Q5
Q5 需允许「未知来源」安装（设置里打开）。然后用已连接的 adb 安装：
```
adb connect 192.168.0.216:5555
adb install app-debug.apk
```

## 技术栈
- Kotlin + AndroidX（minSdk 21 / targetSdk 22，兼容 Android 5.1.1）
- jcifs-ng 2.1.10（SMB 浏览）
- NanoHTTPD 2.3.1（本地 HTTP 流式代理）
- 纯 Java 依赖，无原生库，兼容 ARM 32/64 位

## 说明
- 扫描基于 /24 网段假设（家用路由器常见）。若你的网段不是 255.255.255.0，可在 `LanScanner.kt` 调整。
- 若 Buffalo/WD 需要账号才能看到共享，请先在「设置账号」页填写。
- 调用播放器使用 `ACTION_VIEW` + 选择器，确保能选中「海美迪播放器」；
  若想默认直接拉起某播放器，可在 `MainActivity.playFile()` 中按包名显式启动。
