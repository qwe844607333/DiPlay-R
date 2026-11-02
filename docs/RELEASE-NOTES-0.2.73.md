# DiPlay v0.2.73 — 老旧车机USB配置回退、无线蓝牙稳定性加固与Android 6.0安装器崩溃修复

## 更新内容

### 1. 老旧车机 USB 配置回退机制与连接稳定性（#217）
- **USB 配置优雅降级与描述符容错**：针对全志 T3、MTK 等老款 Android 4.4 车机在升级至 v0.2.72 后可能出现的有线连接中断问题，重构了 USB 设备配置切换逻辑，支持自动识别多 Configuration 并平滑回退，防止 iPhone 协商失败。

### 2. 无线蓝牙 RFCOMM 建链稳定性加固（#218）
- **RFCOMM 链路跟踪与错误重试**：增强对 Android 4.4.2 车机原生蓝牙协议栈与外置 USB 蓝牙适配器的链路监控，加固 RFCOMM 连接超时保护与异常自动恢复，减少无线等待循环。

### 3. Android 6.0 应用安装器崩溃修复（#215）
- **优先走 FileProvider content:// 安装协议**：Android 6.0+（API 23+）在应用内更新时不再向系统 PackageInstaller 传递裸 `file://` URI，优先走授予读权限的 `FileProvider` `content://` URI，彻底解决 Marshmallow 车机点击安装更新时弹出“应用安装器已停止运行”的系统崩溃问题。

### 4. 测试与构建流水线稳定性提升
- **测试堆内存上限扩展**：将 `common` 模块 Robolectric 单元测试进程的最大堆内存上限调优至 2 GB，消除高并发与跨 SDK 沙箱测试时的 GC 抖动及缓存损坏风险。

---

## 验证与发布

- **版本标识**：`versionName 0.2.73` / `versionCode 93`，最低支持 Android 4.2（API 17）。
- **签名校验**：Release APK 包含完整的 V1 + V2 签名方案，签名证书 SHA-256 与历史版本保持一致（`bca015c0cb43469fee55539b4054ff862671e7b41e6612eee0cb8566d327b7ae`）。
- **自动化测试**：全量 1,200+ 单元测试、Lint 扫描及多架构构建全部通过。
