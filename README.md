# AirPods Mode

精简安卓 App：切换 AirPods 降噪 / 通透 / Adaptive，控制中心快捷开关，毛玻璃 UI。

个人学习项目，不盈利。不播放音频，不接入苹果官方 SDK。

## v0.3 功能

- 多机型：Pro / Pro 2 / Pro 3 / AirPods 4 ANC / Max / Max 2（按产品 ID / 型号字符串 / 蓝牙名识别；不支持的功能自动隐藏）
- Adaptive 三档强度（0 / 50 / 100，协议 `0x2E`）
- 对话感知（支持的机型）
- 自定义背景：设置里从相册选图（可持久化 URI），或系统壁纸 / 纯色
- 毛玻璃圆角按压动画（圆角 ripple，无方形水波）
- **上次连接位置**：前台服务运行中断开时记录 GPS（不是 Find My）
- 电池优化申请 + 三星自启动说明
- 已移除空间音频入口
- 快捷控制条：点 QS Tile / 通知打开类似双卡条的全宽三档面板（第三方 Tile 无法真正自由改尺寸，已在界面说明）

## Find My

无法在安卓上实现苹果「查找」网络与耳机盒发声（需逆向/Root）。请用「上次连接位置」。

## 系统要求

三星 **One UI 9 / Android 17**（不 Root）。先系统蓝牙配对。

## 安装

[Releases](https://github.com/chinatsu033/AirPodsMode/releases) 下载 `AirPodsMode-*.apk`。

协议字节来自公开 AACP 文档，未复制 LibrePods GPL 源码。本仓库 MIT。
