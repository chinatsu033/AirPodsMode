# AirPods Mode

精简的安卓 App：在三星等安卓机上切换 AirPods Pro 的 **降噪 / 通透 / Adaptive**，并提供控制中心（任务栏）快捷开关。

个人学习项目，不盈利。AirPods 与相关协议归 Apple。本应用不播放音频，不接入苹果官方 SDK。

## 功能

- 三种聆听模式：Noise Cancellation、Transparency、**Adaptive**（必做）
- 控制中心 Tile：
  - **AirPods Mode**：一键循环 降噪 → 通透 → Adaptive
  - 三个单独 Tile：降噪 / 通透 / Adaptive
- App 内三个大圆角按钮 + 连接状态 + 电量（能读到时）
- 简体中文 / English，跟随系统深浅色
- **不需要 Root**，不依赖 Xposed

## 系统要求

- **三星 One UI 9 / Android 17**（S26 Ultra 等）。更早的 One UI 在不 Root 时往往连不上 AACP 的 L2CAP 通道。
- 先在系统蓝牙里配对 AirPods（支持 AirPods Pro 1/2/3、AirPods 4 ANC 等）。
- 授予「附近的设备 / 蓝牙」和通知权限。

## 安装

从 [Releases](https://github.com/chinatsu033/AirPodsMode/releases) 下载 `AirPodsMode-*.apk` 安装。

添加快捷开关：下拉控制中心 → 编辑 → 添加「AirPods Mode」或三个单独开关。

## Adaptive 怎么切

连接后会按顺序发送：

1. Handshake `00 00 04 00 01 00 02 00 …`
2. Feature enable `04 00 04 00 4D 00 FF 00 …`（解锁 Adaptive）
3. 订阅通知 `04 00 04 00 0F 00 FF FF FE FF`
4. ListeningModeConfigs `0x1A / 0x0F`（开启全部模式含 Adaptive）
5. 设置模式 `04 00 04 00 09 00 0D 04 00 00 00`（Adaptive = `0x04`）

协议字节来自公开的 AACP 文档（LibrePods 的 AAP Definitions / control_commands），**未复制 LibrePods 源码**（该项目为 GPL-3.0）。本仓库为 MIT。依赖 HiddenApiBypass（Apache-2.0）以打开经典 BR/EDR L2CAP `PSM 0x1001`。

## 构建

```bash
./gradlew assembleRelease
```

签名密钥不要提交。本地可在 `local.properties` 指向 signing.properties。

## 说明

- 没有真机 S26 Ultra / AirPods Pro 3 时无法完整验证 L2CAP 与 Adaptive。
- 若连接失败，确认系统为 One UI 9，AirPods 已配对且在附近，并重启蓝牙后再试。
