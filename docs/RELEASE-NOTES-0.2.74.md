# DiPlay v0.2.74 — 修复老旧车机误跳过USB重枚举导致有线CarPlay连接中断

## 更新内容

### 1. 修复老旧车机 USB 重枚举误跳过与死循环重试（#217, #219）
- **USB 描述符快速探测与 Vendor Request 恢复**：修复了因误将 iPhone 标准 Product ID（`0x12a8`）当作“已在 CarPlay 模式”而直接跳过 Apple Vendor Request（`0x52` 控制传输）的严重缺陷。全志 T3、MTK 等 Android 4.4 车机在手机插入后，在至多 800ms 的非阻塞探测未发现就绪配置时，必然正常发送 Vendor Request，使 iPhone 正常弹出信任提示并切换到配置 6 重枚举，彻底根治“读不到 USB 数据配置”及死循环断连问题。
- **STALL 容错与配置挽救**：即便在快速拔插场景下手机对 Vendor Request 返回 STALL，增加最后的异步描述符确认机制挽救可用会话，避免误报失败。

### 2. 诊断提示优化与误报消除
- **消除“模拟盒”误报**：移除原先在 iPhone STALL 时将所有正常 iPhone 误报为“emulation box”的提示文案，改为准确指导用户解锁屏幕、授予信任或更换原装线缆。

---

## 验证与发布

- **版本标识**：`versionName 0.2.74` / `versionCode 94`，最低支持 Android 4.2（API 17）。
- **签名校验**：Release APK 包含完整的 V1 + V2 签名方案，签名证书 SHA-256 与历史版本保持一致（`bca015c0cb43469fee55539b4054ff862671e7b41e6612eee0cb8566d327b7ae`）。
- **自动化测试**：全量 2,200+ 单元测试、Lint 扫描及多架构构建全部通过。
