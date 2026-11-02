# DiPlay v0.2.64 — Android 4 深度兼容与在线更新修复

## 更新内容

### 1. Android 4.2/4.3 基础运行库兼容性修复
- **加密套件平滑降级**：老旧 Android 4.2/4.3 (API 17/18) 平台的系统 `Cipher` 缺失 `updateAAD()` 方法，调用时会触发 `NoSuchMethodError` 导致工作线程异常退出（#170, #178）。本次更新增加系统版本检测与 `LinkageError` 防御，当包含 AAD 时自动平滑降级至内置 BouncyCastle ChaCha20-Poly1305 实现。
- **线程退出兼容**：将 `AndroidMediaSink`、`LegacyWifiP2pGroupManager` 及 `WifiP2pGroupManager` 中调用的 `HandlerThread.quitSafely()`（API 18 引入）在 API 17 下兼容替换为 `quit()`，避免 Android 4.2 车机断连或重连时因方法缺失崩溃。

### 2. Android 4.x/5.x 应用内自动更新调起修复
- **PackageInstaller 协议适配**：老款车机系统内置的 PackageInstaller 仅支持 `file://` URI 协议，对 FileProvider 生成的 `content://` 协议支持不完善。更新后针对老款系统自动转换为 `file://` URI 调起。
- **文件全局读权限授予**：调起安装前自动递归赋予下载 APK 及其父目录的全局可读执行权限（`makeWorldReadable`），防止老旧系统安装器因跨应用沙箱权限不足解析安装包失败。
- **多候选存储路径回退**：覆盖标准 Downloads 目录、外部存储多种常见命名路径（`/sdcard/Download`、`/storage/emulated/0` 等）及内部更新回退路径。
- **Task 标志位补全**：更新 Intent 显式添加 `FLAG_ACTIVITY_NEW_TASK`，确保顺利激活系统包安装器界面。

## 验证与测试

- **版本标识**：`versionName 0.2.64` / `versionCode 84`，最低支持 Android 4.2（API 17）。
- **签名校验**：Release APK 包含完整的 V1 + V2 签名方案，签名证书 SHA-256 与历史版本完全一致（`bca015c0cb43469fee55539b4054ff862671e7b41e6612eee0cb8566d327b7ae`）。
- **离线认证探针**：内置离线认证探针通过完整签名校验，网络请求数严格为 0。
- **单元测试**：主线全量单元测试全部通过。
