# DiPlay-R｜影豹R MSM8996 专项优化版

[简体中文](README.zh-CN.md) · [English](README.md)

**本个人维护分支针对广汽传祺影豹R车机的高通 Qualcomm MSM8996 处理器配置进行专项调优与排障，目标是无线 CarPlay 在动态交互中稳定接近 60 FPS。** 优化依据来自这台车机的实际日志，重点处理高通硬解视频路径中的积压、恢复停顿和短暂网络变化。应用包名保持为 `com.shihab.diplay`。

项目基于 [DiPlay Legacy Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android) 与 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)，保留其旧版 Android 兼容工作。专项优化包含通用视频管线修复；其他车型和固件的兼容性仍需分别验证。

[个人主仓库](https://github.com/qwe844607333/DiPlay-R) · [版本发布](https://github.com/qwe844607333/DiPlay-R/releases) · [问题反馈](https://github.com/qwe844607333/DiPlay-R/issues/new)

## 当前个人维护版：0.3.13（versionCode 133）

| 项目 | 本分支主要调优配置 |
| --- | --- |
| 车型 | 广汽传祺影豹R |
| 处理器 / 架构 | 高通 Qualcomm MSM8996 / arm64-v8a |
| 车机系统 | Android 8.1 / API 27 |
| 显示 | 1920×720；系统声明 59 Hz，尚非外部实测 |
| 无线拓扑 | iPhone 与车机连接同一外部 5 GHz Wi-Fi 网络 |

### 最新版本优化点

- **短时过载继续显示，减少反复停顿**：软积压先请求关键帧，同时继续有效的 H.264 参考链；队列中已有新的独立关键帧时，直接从最新可用位置追赶，并保留后续配置、Surface 及其依赖帧。持续过载仍保留硬限恢复兜底。
- **过时解码画面有替代帧时再跳过**：关闭“流畅视频”时，送入解码器后已等待至少 150 ms 的输出若已有更新输出可替代，可以跳过显示；最后一张画面仍正常提交，不任意丢弃压缩预测帧。
- **短暂 Wi-Fi 波动容错**：选定网络或已公布地址丢失后先核验 1.5 秒，原链路恢复则保留会话；持续失效或换成另一个 Network 才重连。单纯新增 IPv6 地址不再强制重连，重连工作放在后台。
- **降低恢复期额外开销**：关键帧扫描直接检查原始数据，避免反复复制视频载荷；同时沿用此前的队列预算并发修复、诊断日志后台写入和协议 TRACE 惰性构造改进。
- **细化性能诊断**：分别记录接收／解密、软件队列、解码器输入、输出取出及释放等待，并记录触控队列与应用 CPU／GC。新增 `skippedOutputs`、`catchUpFrames` 解释主动追赶；`shown` 是输出提交计数，不是物理屏幕帧率。
- **影豹R专属默认车机按钮**：默认名称为 **“影豹R”**，图标按提供的红色 R 参考重绘，提供[高清矢量母版](docs/assets/yingbao-r.svg)。协议内嵌图标控制在 192×192／32 KB 以内；旧的未自定义 BYD 默认名称一次性迁移，已有自定义名称和图标继续保留。
- **新安装默认请求 60 FPS**：覆盖升级保留已保存帧率；此前设置为 30 的用户，需要在显示设置改为 60 并重新连接。

### 60 FPS 起始配置与验证边界

建议先使用 **H.264／60 FPS／80% 分辨率／高效硬件图层渲染开启**，关闭 **“流畅视频”** 和 **“按帧率请求解码性能”**。若仍积压，保持 60 FPS，只降低一档分辨率做对照；30 FPS 仅作为排障对照。现有证据不支持通过强制全部 CPU 核心、强制 GPU 渲染或增加虚拟内存解决问题。

0.3.13 交付时 **2398 项测试执行通过，6 项按已有平台条件跳过**，完整 CI、lint、构建与签名检查通过。**尚未完成影豹R实车稳定 60 FPS 和断连恢复验收**；设置中选择 60、系统声明的刷新率以及输出提交数量，都不等于已测得屏幕实际稳定 60 FPS。持续无线离网仍需检查外部盒子及网络环境。

详见 [0.3.13 更新说明](docs/RELEASE-NOTES-0.3.13-PERSONAL.md) 与[个人签名及覆盖升级说明](docs/PERSONAL-MAINTENANCE.md)。

---

## 继承的旧版 Android 兼容特性

以下为继承的旧版车机兼容工作，不代表本次针对影豹R的新增优化，也不等于所有对应硬件已实测。T3 / Android 4.4 的 API 19 软件与模拟器专项检查，不能替代 USB、蓝牙、画面、声音和触控的真机全链路验证，详见 [T3 验证记录](docs/T3-ANDROID44-VALIDATION.md)。

### 1. 深度适配低版本 Android 系统 (Android 4.4 ~ Android 7.0)
- **系统门槛下探至 Android 4.4 (API 19)**：为基础运行路径增加旧版实现与 API 判断。仅在高版本可用的实验性功能不会因此获得低版本硬件支持：
  - 修复 Android 7.0 以下因 `Configuration.locales` 引发的 `NoSuchFieldError` 启动闪退。
  - 修复 Android 6.0 以下因调用 `Context.getSystemService(Class)` 引发的 `NoSuchMethodError` 闪退。
  - 修复 Android 5.0 以下 `Theme.Material` 主题缺失导致的膨胀异常，回退兼容 Holo 主题。
  - 修复无 `BluetoothLeScanner` 低版本系统的蓝牙扫描，支持手动输入蓝牙 MAC 地址连接。
  - 修复非比亚迪 AOSP 老车机音频通道（默认 stream 14 异常，自适应降级为标准 STREAM_MUSIC）。

### 2. 解决安装与解析包失败问题
- **双重签名机制 (v1 JAR + v2 APK Signature)**：
  - 上游原版通常仅打 v2 签名，导致 Android 4.4 / 6.0 老车机安装时直接提示“无法解析安装包”。
  - 本项目采用生产签名证书，同时生成 v1 与 v2 签名，满足旧版 Android 的签名格式要求。具体安装和覆盖升级结果见专项验证记录。

### 3. 全架构 CPU 支持 (包含 32 位老车机芯片)
- **原生编译 4 种 CPU 架构**：包括 `armeabi-v7a` (32位 ARM)、`x86` (32位 Intel)、`arm64-v8a`、`x86_64`。
- **提供 32 位车机所需的 ARMv7 原生库**。这解决缺少该架构库的问题，不代表 T3 / AC8227L / 展讯 / 联发科所有固件的 USB、蓝牙和解码器均已实测。

### 4. 内置离线 MFi 认证证书
- 独立安装包构建会带入本地私有认证材料，无需配置外部认证服务器。该身份是实验性的，本地签名成功并不能保证 iPhone 接受，也不是 Apple 认证。

### 5. USB 底层连接优化与驱动冲突解决
- **自动驱动脱钩 (解决 `iPhone USB configuration is busy` / errno 16)**：底层 C 语言驱动层在切换 Configuration 6 遇到 `EBUSY` 时，自动探测并执行 `USBDEVFS_DISCONNECT` 解绑内核冲突驱动，并自动重试。
- **全志 T3 等芯片专属单 fd 共享**：USBMUX 与 NCM 数据通道共用已授权的底层设备描述符，避免二次 `openDevice` 导致内核抛出 `ENOENT` / `EBUSY`。

### 6. 东风日产等搭载航盛定制协议栈车机深度适配 (HSAE P170/P660)
- **航盛私有蓝牙服务 AIDL 深度适配**：支持日产逍客、轩逸、奇骏、天籁等搭载的航盛专用蓝牙栈（`com.hsae.bluetoothservice` 与 `com.anwsdk.service`），突破原生 AOSP RFCOMM Socket 读超时断连限制 (`IOException: read failed ... ret: -1`)。
- **混合蓝牙适配架构 (Hybrid Bluetooth Adapter)**：运行时动态探测航盛服务，在非日产设备上 100% 干净回退至标准 Android 蓝牙，做到对所有平台透明无感。
- **Linux 4.9 内核热点 IPv6 路由黑洞修复**：解决日产定制 Android 9 内核下 link-local IPv6 导致 iPhone 热点连接“正在搜索设备”卡死的问题，优先采用稳定 IPv4 握手。
- **AudioTrack 兼容防护**：针对定制系统 `AudioTrack.getAudioAttributes()` 调用异常添加保护，防止音频通道初始化崩溃。

---

## 安装与升级重要说明

> [!IMPORTANT]
> **首次从其他签名版本迁移才需要卸载。**
>
> 上游或其他作者签署的 APK 与本个人维护版签名不同，不能直接覆盖。首次迁移请先记录设置，卸载旧签名版后安装；卸载会删除应用私有设置。此后使用同一个人密钥、相同包名和更高 versionCode，即可覆盖升级并保留设置。此前的 0.3.10～0.3.12 个人版可由 0.3.13 直接覆盖。

> [!TIP]
> **车机真实 Android 版本检测（排查安装解析失败）**：
> 很多老款后装/改装车机在系统界面显示的“Android 10/11/12”存在商家虚标（实测底层常为 Android 4.4、5.1 或 6.0 深度魔改）。
> 如果您在安装时提示 **“解析程序包出现问题”** 或安装失败，可以使用这位开发者提供的车机版本检测工具查看真实的 Android 系统版本与 API Level：
> 👉 **[headunit-version-inspector (车机版本检测)](https://github.com/493330227-wq/headunit-version-inspector)**

---

## 连接使用与排查建议

### 有线 (USB) 连接
1. **排查原厂投屏软件占用**：
   - 很多老款车机（如全志 T3、亿连车机）出厂自带“亿连”、“CarLife”、“Zlink”等后台守护进程。这些程序开机后会抢先霸占 iPhone 的 USB 接口。
   - 建议在车机【设置】→【应用管理】中，将原厂的“亿连”或“CarLife”点击**【强行停止】**（或关闭其自启动权限）。
2. **连接步骤**：
   - 拔下 iPhone 数据线；
   - 打开 DiPlay 进入等待连接界面；
   - 将 iPhone 插入车机主 USB 数据口（支持互联的数据口，非仅供电口）；
   - iPhone 弹出“信任此电脑”时点击**【信任】**。

### 无线连接
- 支持 车机热点（Car Hotspot）、Wi-Fi Direct 以及 局域网（Same LAN）三种模式。
- 在部分不支持蓝牙 BLE Scanning 的老车机上，可在设置中手动填入车机蓝牙 MAC 地址以完成握手配对。

---

## 架构支持与构建范围

影豹R MSM8996 的主要运行架构为 **arm64-v8a**。当前个人维护包包含四种 ABI；下表说明继承的架构范围，拆分安装包是否提供以实际发布附件为准。

| 架构 | 对应范围 | 说明 |
| :--- | :--- | :--- |
| **arm64-v8a** | 影豹R MSM8996 等 64 位 ARM 车机 | 本分支的主要调优配置 |
| **armeabi-v7a** | 全志 T3、AC8227L 等 32 位 ARM 设备 | 保留兼容路径，不代表已完成相应固件实测 |
| **x86 / x86_64** | 对应 Intel／模拟器平台 | 保留构建支持，不代表影豹R采用此架构 |

---

## 问题反馈与交流讨论

- **Issues 讨论**：如果遇到任何问题，欢迎在 [Issues](https://github.com/qwe844607333/DiPlay-R/issues) 中发帖讨论，作者有时间就会回复解答。
- **提供日志**：遇到具体问题时，**最好贴出详细的运行日志或诊断报告**，以便快速排查定位问题：
  1. 打开 DiPlay【设置】→【诊断】→【保存诊断报告】（v0.2.27 起已支持自动同步写入插上的 U 盘及 SD 卡根目录 `/mnt/sdcard/DiPlay/`）。
  2. 将导出的 `.txt` 诊断报告或相关截图/崩溃信息附在 Issue 描述中。
  3. 注明车机芯片型号、实际 Android 系统版本、iPhone 型号及 iOS 版本。
- **欢迎提交 PR**：老车机硬件型号繁多、ROM 深度定制，欢迎有能力的开发者提交 Pull Request (PR) 共同完善兼容支持！
- ⭐ **点赞支持**：如果这个项目对你的老车机有所帮助，请顺手帮我点个 **Star**，感谢大家的支持与鼓励！

---

## 开源协议与鸣谢

- 基于 [xcertplay](https://github.com/shilapi/xcertplay) (GPL-3.0) 与 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) 开发。
- UI 交互衍生自 [DiAuto](https://github.com/shihabal3amri/DiAuto) (AGPL-3.0)。
- CarPlay 为 Apple Inc. 的注册商标，本项目仅供技术学习与车载兼容性研究。
