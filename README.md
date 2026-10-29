# Corn Modbus

作者：[xingyingyuzhui](https://github.com/xingyingyuzhui) · 授权：[PolyForm Noncommercial 1.0.0](LICENSE.md)（非商业授权开源）

Android 离线 Modbus RTU 主站 / 从站调试器。协议侧有多站总线帧扫描和回显跟踪。界面已包含连接、主从站工作台、组件面板、曲线、通信日志和设置。真机和多站总线未测。

规范只看 `docs/spec.md`。`research/` 没有放进仓库。

## 界面预览

下面六张是 **Roborazzi UI 渲染**（浅色、竖屏），不是真机或模拟器照片。短文件名副本在 [`docs/readme/`](docs/readme/)，字节与 `app/src/test/screenshots/` 里对应的 `ShellScreenshotTest.*_light_portrait.png` 相同。深色和横屏的同组渲染仍在该测试目录，这里不重复贴。

面板、曲线两张是还没配置组件时的空状态：面板显示「添加组件」；曲线页写明当前面板没有曲线。配好点位之后，面板可以放数值、曲线、指示灯等组件，有曲线时再进全屏曲线。

| 连接 | 工作台 | 面板 |
| --- | --- | --- |
| <img src="docs/readme/connection-light.png" width="200" alt="连接页：串口参数、主站或从站、演示设备"> | <img src="docs/readme/workbench-light.png" width="200" alt="工作台：读寄存器与写入区"> | <img src="docs/readme/panel-light.png" width="200" alt="面板页：未添加组件时的空状态"> |
| 选 USB 串口或演示设备，主站 / 从站后连接 | 主站读写信文；从站看虚拟寄存器 | 未添加组件时的空面板 |

| 曲线 | 日志 | 我的 |
| --- | --- | --- |
| <img src="docs/readme/curve-light.png" width="200" alt="曲线页：当前面板没有曲线"> | <img src="docs/readme/log-light.png" width="200" alt="日志页：尚未记录，工具栏有开始、清除、保存"> | <img src="docs/readme/mine-light.png" width="200" alt="我的页：外观、显示与格式、安全、通信默认值、关于"> |
| 当前面板没有曲线时只说明原因 | 默认不记录；开始后按帧看收发 | 外观、通信默认值、关于 |

## 功能一览

- USB 串口 Modbus RTU 主站 / 从站。同一口一次只跑一个角色。不申请 `INTERNET`，运行不联网。
- 连接页可选「演示设备」，不需要转换器。具体初值见下方「构建」。
- 工作台读写寄存器 / 线圈；写入前确认。
- 面板组件、通信日志、曲线采样（样本只在内存）。日志默认不记，点「开始」才记，可保存。
- 「我的」可看存储用量和历史会话，并导出工程、从站配置、点表、寄存器快照、采样历史和内存里的通信日志。
- 真机 USB-RS485 和多站总线未测。

## 下载

安装包在 GitHub Release [v0.1.4](https://github.com/xingyingyuzhui/corn-modbus/releases/tag/v0.1.4)，APK 不入库。

- [`Corn-Modbus-0.1.4.apk`](https://github.com/xingyingyuzhui/corn-modbus/releases/download/v0.1.4/Corn-Modbus-0.1.4.apk)：日常安装，走 USB 串口和演示设备。
- [`Corn-Modbus-0.1.4-debug.apk`](https://github.com/xingyingyuzhui/corn-modbus/releases/download/v0.1.4/Corn-Modbus-0.1.4-debug.apk)：多一条本地虚拟串口，用来和本机 PTY / PyModbus 做 RTU 联调。Release 包没有这条调试桥。做法见 [`docs/virtual-rtu-lab.md`](docs/virtual-rtu-lab.md)。

## 工具链

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk | 37.2 |
| targetSdk | 37 |
| minSdk | 26 |
| usb-serial-for-android | 3.11.0 |
| kotlinx-coroutines | 1.11.0 |

稳定 API 是 37。`compileSdk` 用平台 `android-37.2`，因为当前 Compose BOM、Navigation 和 Lifecycle 要求至少编译到 API 37。AGP 9.4 的 `targetSdk` 只接受主版本，所以目标版本是 37。

## 目录

```
app/            Compose、USB、前台服务、会话
modbus-core/    纯 JVM 协议栈，不能依赖 android.*
docs/           规格、路线、审阅记录、变更说明、界面计划（docs/ui-plan.md）
docs/readme/    README 界面预览用的 Roborazzi 副本（浅色竖屏）
```

## 构建

```bash
./gradlew :modbus-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

演示模式不需要转换器。连接页选「演示设备」：主站可读 Unit 1 的 HR0–9（0、10、20…90）；从站的 HR0 每 5 秒被演示主站改一次。

本地有 SDK/JDK 时可直接执行上述命令；若环境变量中的 Gradle 缓存路径指向未挂载外置盘，可在本次命令前设置 `GRADLE_USER_HOME="$HOME/.gradle"`。一次只运行一个本地构建，完成后执行 `./gradlew --stop`。模拟器允许清理旧测试包后重装；截图仅留本地。

## 本期没有做

FTDI 延迟界面、历史导入、Hilt，以及任何网络权限。配置导入（工程 / 从站配置）已有。「我的」页可以看存储用量和历史会话，并导出工程、从站配置、点表、寄存器快照、采样历史和内存里的通信日志。Room 记下会话和默认 5 秒的点位快照；曲线样本仍只在内存。日志保存和曲线快照可用。多站扫描和分块回显已在协议核心里实现；真机和多站总线未测。

## 授权

本项目以 **PolyForm Noncommercial License 1.0.0** 开源，全文见 [`LICENSE.md`](LICENSE.md)。

- 可以：个人学习、研究、测试、自用，以及非营利组织、教育和公共研究机构使用；可以修改和分发，但必须附上 `LICENSE.md`（含开头的 `Required Notice` 署名行）。
- 不可以：任何商业用途（包括把它或修改版用于销售、收费服务、商业产品或公司内部的营利业务）。商业授权请联系作者 [xingyingyuzhui](https://github.com/xingyingyuzhui)。
- 注意：非商业授权不属于 OSI 定义的“开源许可证”，这里的“开源”指源码公开、可在非商业条件下使用。

Copyright 2026 xingyingyuzhui. Corn Modbus 名称与图标归作者所有。
