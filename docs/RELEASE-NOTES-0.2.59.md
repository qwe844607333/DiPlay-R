# DiPlay v0.2.59 — 老款车机有线 CarPlay 与诊断兼容修复

## 更新

- 老款车机兼容：USB、VPN、蓝牙、音视频、设置和安装等路径按实际 Android API 选择实现，避免 T3 等虚报 SDK 的车机调用不存在的系统方法。
- 有线连接：修正 AirPlay IPv6 接口 scope 查找，补齐 mDNS 服务枚举应答，改善 iPhone 发出 SYN 后车机监听端无法匹配的问题。
- 有线重试：复用可用的 VPN TUN 并重建 NCM 桥接与服务发现，减少 Android 4.4 遗留 fwmark 规则导致的连接失败。
- 视频解密：Android 4.4 优先尝试已打包的 Conscrypt 原生实现，并保留原有回退路径。
- 安装与设置：补齐 Android 4.2/4.4 的安装入口和外部目录回退，修复设置面板重建时重复移除父容器造成的崩溃。
- 诊断报告：新增“复制全部”按钮，避免旧车机长按文本选择时闪退；崩溃堆栈逐行脱敏保存。
- Release 兼容：修复跨模块方法可见性及有线控制状态分支编译问题。

## 验证

- versionName 0.2.59 / versionCode 79，最低支持 Android 4.2（API 17）。
- Release APK 完整性及发布签名校验通过，与 v0.2.21 原始正式签名一致；离线认证探针通过。
- GitHub Actions APK 版本、SHA-256 和正式签名发布校验均通过。
- shared 的 1,142 项及 Home 的 4 项单元测试通过；三个 Debug 包构建和 Debug Lint、mobile Release Lint 通过。
- common 的 924 项测试中仍有 4 项失败、2 项跳过，涉及设置页滚动/焦点及诊断文本选择断言，完整 CI 尚未通过。
- 实际车机的 CarPlay 连接、重连、音视频及覆盖安装仍需在目标设备复测。
