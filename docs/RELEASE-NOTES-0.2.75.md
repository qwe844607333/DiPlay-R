# DiPlay v0.2.75 — 外置蓝牙RFCOMM入站支持与快速广播、切有线总线死锁预防及冷启动优化

## 更新内容

### 1. 外置 USB 蓝牙适配器（CM390 等 Actions / Realtek 芯片）配对与建链修复
- **RFCOMM (PSM 0x0003) 入站服务与会话应答**：针对 iPhone 在发现蓝牙 SDP 后主动向车机发起 RFCOMM L2CAP 连接的机制，实现 L2CAP PSM 3 服务工厂与 RFCOMM SABM / PN / MSC 握手入站自动应答，彻底消除此前因 `0x0002 (PSM not supported)` 导致 iOS 立即弹窗“连接失败”并挂断的问题。
- **广播名称即时生效**：HCI 初始化阶段优先写入 Local Name（`DiPlay`），消除原本需等待整机流程完成导致长达 2 分钟的蓝牙名称显示延迟。
- **取消强制 PIN 认证**：在 SSP 简易安全配对模式下关闭 `Write_Authentication_Enable` 强制 PIN 认证，根除 Actions/Realtek 芯片触发 `EVT_PIN_CODE_REQUEST` 被对端拒绝导致配对失败的缺陷。
- **精简 SDP 记录**：移除未支持的 HFP (Handsfree) 记录，避免 iPhone 误连通道 2 并在 AT 指令无响应后断链。
- **USB 热插拔权限保持**：补全 USB 设备过滤器标准蓝牙控制器类（0xE0/0x01/0x01）与主流芯片 VID，拔插后自动恢复设备权限。

### 2. 切换有线或关闭无线时 USB 总线死锁与车机重启预防
- **安全释放 USB 蓝牙适配器**：切换至有线连接或关闭无线模式时，显式调用 `disable()`，安全中断并 join 轮询 pump 线程，避免在阻塞 `bulkTransfer` 过程中强行关闭导致 Linux 内核驱动进入 D-state 死锁。
- **总线并发保护**：在无 ACL 连接时让 ACL 轮询休眠，彻底消除全志 T3、MTK 等老旧单控制器 USB Hub 在多端点并发传输时的驱动互锁与看门狗整机重启。

### 3. 冷启动性能优化
- **消除重复证书验签**：重构首次启动验证逻辑，由原先在 assets 与 staging 目录的 3 次重复全量 ECDSA 验签优化为仅在 staging 执行单次验签并就地重命名，消除了不必要的大内存分配，老旧车机冷启动耗时从 10-15s 大幅降低至 1s 内。

---

## 验证与发布

- **版本标识**：`versionName 0.2.75` / `versionCode 95`，最低支持 Android 4.2（API 17）。
- **签名校验**：Release APK 包含完整的 V1 + V2 签名方案，签名证书 SHA-256 与历史版本保持一致（`bca015c0cb43469fee55539b4054ff862671e7b41e6612eee0cb8566d327b7ae`）。
- **自动化测试**：全量单元测试、Lint 扫描及多架构构建全部通过。
