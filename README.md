# AirPods Mode

精简的安卓 App：在三星等安卓机上切换 AirPods Pro 的 **降噪 / 通透 / Adaptive**，并提供控制中心快捷开关。

个人学习项目，不盈利。AirPods 与相关协议归 Apple。本应用不播放音频，不接入苹果官方 SDK。

## 功能（v0.2）

- 三种聆听模式：Noise Cancellation、Transparency、**Adaptive**
- **Adaptive 三档面板**（类似 iOS）：首次点 Adaptive 进入默认强度 50；再点 / 长按 / 点箭头展开毛玻璃卡片栈  
  - 上：更通透（强度 0）  
  - 中：默认 Adaptive（50）  
  - 下：更降噪（100）  
  - 发包：`04 00 04 00 09 00 2E [level] 00 00 00`  
  - **说明：AirPods Pro 3 可能接受该包但听感变化不明显（社区反馈）。**
- **对话感知**开关（AACP `0x28`）
- **空间音频**：诚实处理——可尝试系统 Spatializer / 打开声音设置；**不会假装苹果空间音频已开启**（安卓无 Apple HRTF，通常需 Root）
- 毛玻璃 UI + 桌面壁纸背景（无权限时用渐变兜底）
- 控制中心 Tile：循环切换 + 三个单独开关
- 简体中文 / English，跟随系统深浅色
- **不需要 Root**，不依赖 Xposed

## 系统要求

- **三星 One UI 9 / Android 17**（如 S26 Ultra）。更早 One UI 在不 Root 时往往连不上 L2CAP。
- 先在系统蓝牙里配对 AirPods（Pro 1/2/3、AirPods 4 ANC 等）。
- 授予蓝牙与通知权限；壁纸权限可选。

## 安装

从 [Releases](https://github.com/chinatsu033/AirPodsMode/releases) 下载 `AirPodsMode-*.apk`。

## Adaptive 连接发包顺序

1. Handshake  
2. Feature enable `04 00 04 00 4D 00 FF …`（解锁 Adaptive / 对话感知）  
3. 订阅通知  
4. ListeningModeConfigs `0x1A / 0x0F`  
5. 设模式 Adaptive `0x0D / 0x04`  
6. （可选）Adaptive 强度 `0x2E / level`

协议字节来自公开 AACP 文档（LibrePods AAP Definitions），**未复制 LibrePods 源码**（GPL-3.0）。本仓库 MIT。依赖 HiddenApiBypass（Apache-2.0）。

## 构建

```bash
./gradlew assembleRelease
```

## 说明

- 没有真机 S26 Ultra / AirPods Pro 3 时无法完整验证 L2CAP、Adaptive 三档与对话感知。
- 图标为原创剪影，不是苹果商标素材。
