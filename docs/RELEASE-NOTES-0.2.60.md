# DiPlay v0.2.60 — 有线 mDNS 组播应答与崩溃诊断修复

## 更新

- 有线发现：非 QU 的 mDNS 查询增加组播应答，改善 iPhone 忽略单播应答后持续查询、未建立 AirPlay TCP 连接的问题；保留原有单播路径，QU 查询仍只使用单播应答。
- 崩溃诊断：迭代读取异常 cause 链，识别循环并限制深度和堆栈长度，避免报告生成时发生 StackOverflowError、丢失原始异常信息。
- 回归测试：补充有线 mDNS 单播/组播、IPv6 UDP 校验和及循环/过深异常链的自动化用例。

## 验证

- versionName 0.2.60 / versionCode 80，最低支持 Android 4.2（API 17）。
- Release APK 完整性及最低 API 17 的签名校验通过，保留 v1/v2 签名，与 v0.2.21 原始正式签名一致；离线认证探针的 6 次签名验证通过。
- 新增 10 条回归测试通过；shared 的全部 1,152 项及 Home 的 4 项单元测试通过。
- 三个 Debug 包构建和 Debug Lint、mobile Release Lint 及正式签名构建通过。
- Android 4.2.2（API 17）、4.4.2（API 19）和 Android 11（API 30）模拟器覆盖安装、首页及设置入口启动检查通过。
- common 的 924 项测试中仍有 4 项失败、2 项跳过，涉及设置页滚动/焦点及诊断文本选择断言，完整 CI 尚未通过。
- 实际车机的 CarPlay 发现、连接、重连和崩溃报告仍需在目标设备复测。
