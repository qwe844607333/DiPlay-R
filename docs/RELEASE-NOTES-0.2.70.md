# DiPlay v0.2.70 — 优化冷启动耗时、完善外置USB蓝牙配对与热插拔、解决有线连接卡死与看门狗重启

## 更新内容

### 1. 冷启动时间大幅削减（10~15秒降至 <0.5秒）
- **离线凭据快速校验**：在 `DiPlayBootstrap` 中引入 `matchesAssets` 快速路径。当私有数据目录中已存在与 APK 资源一致的离线 MFi 凭据时，直接跳过耗时的全量重新解包、SHA-256 哈希比对以及 3 次 BouncyCastle 椭圆曲线非对称加解密签名验证。
- **杜绝主线程 UI 冻结**：在低端 Cortex-A7 处理器及 Android 4.x Dalvik 运行时上，彻底消除安装后首次或每次冷启动 10~15 秒的主线程卡顿。
- **保护用户调试日志配置**：防止每次冷启动无条件将已开启的调试日志配置重置为关闭。

### 2. 外置 USB 蓝牙适配器与配对完善
- **支持 Link Key 与 PIN 码负响应**：当收到手机配对重连抛出的 `EVT_LINK_KEY_REQUEST (0x17)` 或 `EVT_PIN_CODE_REQUEST (0x16)` 时，若本地无已存密钥，向 HCI 控制器回复 `OP_LINK_KEY_REQUEST_NEGATIVE_REPLY`，使控制器平滑回退至安全简易配对（SSP 数字比对）模式，彻底解决配对失败。
- **即插即用预初始化与热插拔保活**：适配器初始化时注册热插拔广播并自动下发 `Write_Local_Name("DiPlay")`，消除外置适配器初次插入时残留的电脑蓝牙名称；拔出蓝牙时保留广播接收器，重新插入时自动识别并重新激活广播。

### 3. 有线连接卡死与整机看门狗重启根治
- **消除 `readSynchronously` 死循环 100% CPU 满载**：在 `IphoneUsbHost` 同步读取中加入纳秒级绝对超时与 `checkOpen()` 检查，避免底层 1ms 超时高频空转吃满 4 核心 CPU，消除触控/物理按键冻结。
- **消除 NCM 接口释放内核死锁**：调整 `LwipNcmBridge.close()` 释放时序，等待后台 RX 接收线程完全退出后再执行 `ncm.close()` 释放底层 USB 接口，彻底消除全志 T3 Linux 3.4 内核 usbfs 驱动进入 D-state 死锁诱发的整机看门狗重启。

### 4. 兼容尾部截断的 mDNS 查询包（#199, #204）
- **宽容解析有效 DNS 负载**：在 `WiredMdnsResponder` 中改用实际到达的有效 UDP 负载长度解析，避免因 USB NCM 尾部填充截短丢弃查询，确保及时回复 iPhone 的 mDNS 查询，解决手机反复卡在“正在连接 CarPlay”的问题。

### 5. 老旧 ROM 热点设置一键直达（#200）
- **适配 Android 4.2 / 4.4 便携式热点页面**：在设置跳转中补充旧版 AOSP 的 `com.android.settings.TetherSettings` 与 `WifiApSettings` 类目解析候选，解决掌讯等老旧车机点击“打开热点”误跳普通 Wi-Fi 列表的问题。

### 6. 模式切换自动安全重置
- **会话平滑重置**：在 `CarPlayHostActivity.onResume` 中加入连接方式（无线/有线）比对，检测到模式变更时主动回收旧控制器并重建会话栈，防止 TCP/7000 端口与 mDNS 状态互锁。

---

## 验证与发布

- **版本标识**：`versionName 0.2.70` / `versionCode 90`，最低支持 Android 4.2（API 17）。
- **签名校验**：Release APK 包含完整的 V1 + V2 签名方案，签名证书 SHA-256 与历史版本完全一致（`bca015c0cb43469fee55539b4054ff862671e7b41e6612eee0cb8566d327b7ae`）。
- **自动化测试**：全量单元测试与构建校验全部通过。
