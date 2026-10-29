# 项目整合记录

## 2026-10-17

- 用户在聊天中明确授权：将仓库未合并分支全部合并到 main。
- 远端分支检查：仅 slave-units-setup 和 slave-sim-rules 存在未进入 main 的提交，其余分支已包含。
- PR #9 的两项 CI 通过，已合并，合并提交 09c6fe7。
- 为 slave-sim-rules 创建 PR #10。原提交带 skip ci，未触发检查；本次补充整合记录并推送，以触发完整 CI。
- 模拟规则列表和编辑界面仍待接入，对应新增测试尚未补齐；不将本次整合记作功能验收。
- 本地未运行构建：规定的 /opt/android/build.sh 在本机不存在，以 GitHub CI 为验证依据。模拟器和真实硬件未验证。

- 提交 a1a284f 的两项完整 CI 均通过：39045371067（6m1s）、39045375849（4m48s），含单元/界面测试、lint、截图录制、APK 打包与 INTERNET 权限检查。
- PR #10 已合并，合并提交 9c658ac；本地已切回 main 并同步。
- 完成所有现有远端分支的整合，保留原分支和历史；模拟规则界面仍待开发，真机和模拟器未验证。

## 2026-10-18 · 从站模拟规则收尾

- 用户授权继续推进项目，本阶段在 `codex/slave-simulation-ui` 完成规则列表、全页编辑、预览、暂停/恢复与专项测试；未授权合并 main。
- 复用已有 Compose 控件和设计稿；核对 AndroidX 官方状态提升、BackHandler API，草稿由 ViewModel 持有。
- 修复增删 Unit 后保存失败仍返回成功；保存结果在会话互斥区内返回，测试覆盖保存失败与重试。
- 本机缺少规定的 `/opt/android/build.sh`，不绕过本地构建约定；本阶段通过分支 GitHub CI 验证，结果待记。GUI 模拟器及真实硬件未运行。
- 已提交并推送 `e73e7f6`；创建草稿 PR #11：https://github.com/xingyingyuzhui/modbus_android/pull/11 ，不合并。完整 CI：39048660600（push），PR 事件另触发检查。
- 独立静态审阅完成，修复“保存并运行”与自启标志混用、极大有限参数导致预览溢出两项问题；实际测试和布局以本阶段 CI 为准。
- 用户随后明确本机具备 Android 构建与模拟器环境，授权本地构建、安装和截图验证；据此更新注意事项 #22。发现会话继承的 GRADLE_USER_HOME 指向未挂载外置盘，单次命令改用本机 ~/.gradle，已启动串行 Gradle 验证。
- 模拟器 MCP 有旧的启动身份失败记录；确认无运行设备/进程后，使用相同配置 Pixel_9_Pro、端口 5582 从本地 SDK 启动，无窗口、不清数据，后续由 MCP 借用该实例。
- 本地首次全套测试：core 58 项通过，app 800 项中 2 项新 UI 测试失败；定位为测试自动滚动后点击被浮动 dock 遮挡。修正真实滚动并增加遮挡断言，提交 `26e17f3` 已推送。随后完整录制构建 core 58 + app 800 全通过，lint 与 APK 打包成功。
- 本地 Pixel_9_Pro / API 35：新建正弦规则（HR1）、实际改值来源、暂停/恢复、返回放弃确认、删除确认及删除后空列表已验证，当前测试进程 crash buffer 为空。真实键盘与横竖屏均截图，截图仅存本机私有目录。
- 安装遇到存储不足和旧包签名冲突：先用隔离 QA applicationId 安装验证；临时降低安装空间阈值后均立即恢复原设置；随后用户授权清理旧测试包，已卸载旧 Modbus 包。未清其他应用数据。
- 本地截图发现横屏系统手势条贴到保存按钮底缘，补导航栏安全区与专项断言后再验，不将首次截图作为视觉验收通过。
- 横屏安全区修复提交 `1003fb7` 已推送；最终本地完整构建成功（28s）：core 58 项、app 801 项均无失败，含 24dp 系统导航区回归；Roborazzi、lint、APK 打包均通过。Gradle 录制的 PNG 已复制到本机私有证据目录，恢复仓库原有基线，不提交新增基线。
- 正常 applicationId 的最新 APK 已重新安装到本地模拟器，临时 QA 包已卸载；横屏截图确认按钮较原来上移 72px（24dp），系统手势条不再覆盖按钮，当前进程 crash buffer 为空。独立审阅定向复核此修复通过。
- 正常安装包补测非法数字保存：字段错误明确显示、编辑页保留、无崩溃；测试结束后停止测试应用，模拟器未自动关闭。按用户授权清理旧测试包和可回收缓存/编译缓存，保留其他应用数据；空间仍紧张，未执行 AVD wipe。
- 编译缓存清理命令多数包成功，末尾遇到模拟器残留的 com.jetbrains.kmpapp 包记录而退出 1；不记作完整系统清理成功。系统安装空间阈值已复查恢复默认。
- 最新代码 `1003fb7` 的 PR 完整 CI 39049452334 已成功：https://github.com/xingyingyuzhui/modbus_android/actions/runs/39049452334 。含完整测试、Roborazzi、lint、APK 与 INTERNET 权限检查；同提交 push CI 39049448534 也已成功；两项完整 CI 全绿。最后仅有文档记录提交，带 `[skip ci]`，不复跑代码验证。

## 2026-10-18 · 本地虚拟串口 RTU 联调

- 用户要求在本地启动串口对串口服务，模拟 485 联调。新分支 `codex/virtual-rtu-lab` 基于已验证的模拟规则分支；不合并 main。
- 核对本机 Android Emulator 与 QEMU 能力：旧 console monitor 返回 QEMU support no longer available；不把标准 QEMU 的 USB-serial 文档能力当作本机已接通。采用 debug-only ADB Unix-domain socket 串口适配，复用实际 SerialIoWorker 和协议引擎，独立 PyModbus+PTY 为对端。
- Release 不包含调试桥，不增加网络权限；硬件与物理层测试保持未验证，详见 docs/virtual-rtu-lab.md。
- 首次本地 Android 构建：core 58 + app 804 项测试、lint、debug/release 打包通过，APK 检查确认 release 不含桥接 socket 名称、两变体均无 INTERNET 权限。
- 真实模拟器联调发现 debug LocalSocket 的 SO_TIMEOUT 超时抛普通 IOException（Try again），首次空闲 1 秒误断开，导致首次独立探测 28 项失败；不计作协议引擎失败或联调通过。已改为 Os.poll 有限等待读，并新增空闲/数据/EOF 专项测试，准备复验。
- 独立审阅发现并修复主机中继部分写丢字节、分块跨帧乱序、陈旧PID状态误报、短成功读取误判四项；主机工具测试增至10项。
- 复验通过（2026-10-18 01:22，可见窗口 Pixel_9_Pro:5582）。开始前模拟器进程消失过，已用同一 AVD、`-scale 0.4`、`-no-snapshot-load` 重新打开窗口，未做 AVD wipe；通过记录来自重启后的实例。手机作从站：`peer.py probe --baudrate 9600 --timeout 2 --broadcast` 28/28 通过，含两 Unit 的 FC01–04、FC05/06/15/16 写后读回、非法地址异常码 2、广播 4321 读回。手机作主站：对 PyModbus server 发出 FC03 数量 10，收到 1000..1009；默认 FC06 将 HR0 写为 0，应答与请求同帧。主机中继单测 10 项通过。原始报告留在本机 `/tmp`，未入库。真机 USB-RS485 与多站电气仍未验证。详见 `docs/virtual-rtu-lab.md`。

## 2026-10-18 · 迭代状态核对

- 用户要求先检查新一轮迭代，随后授权更新状态文档。本次更新 `docs/status.md`，本地分支为 `mine-export@6cc873f`，main 为 `05ea1e2`。
- 核对源码、远端分支、PR #11–19 与 Actions：历史库 #12、导出 #13、轮询 #19 两项 CI 均成功；日志修复 #14 结果混合；主题/控件/反馈/keyline #15–18 检查失败，最新 #16 为 803 项中 20 项失败。均尚未合并 main。
- 区分当前本地已有的历史库/导出与远端尚未整合的 QA/UI 改动；记录导入未实现、历史容量清理与全量导出内存风险（源码发现、未实测）。
- 本次仅更新文档，未重新运行构建、模拟器或分享/保存流程，未修改源码及现有截图改动，未合并分支。后续模拟器须有可见窗口，Live Edit 尚未建立调试会话。

## 2026-10-18 · 支线修复与整合（已授权）

- 老板在聊天明确授权：整理问题，修复当前能修的问题，最终将支线合并main。根整合分支 `codex/integrate-iterations` 保留各支线历史，PR #20：https://github.com/xingyingyuzhui/modbus_android/pull/20 。
- 已合入模拟UI、虚拟RTU、历史与导出、日志快照、应用级轮询、v2主题/控件/反馈/keyline及CL04工作台，并修正整合冲突。UI、历史、工作台分别实现，独立reviewer只读审阅；全部已发现阻塞项修复并定向复核。
- 最终代码 `1d37136` 本地完整构建46秒成功：core58+app898，0失败/错误/跳过；lint、Roborazzi、debug/release APK通过；PTY工具10项及offline APK检查通过。录制PNG备份到本机私有目录并恢复仓库基线，不提交截图。
- 两项云端CI 39110230148 / 39110227707 均成功（代码1d37136），准备执行授权合并；后续只有文档更新，带skip ci，不重复源码验证。
- 本轮可见模拟器ADB持续offline，重连/冷启动/ADB重启/软件渲染未恢复；没有验证新APK安装和GUI，不wipe其它数据。该项与真实USB/485保留未验证。

- 两项完整CI均成功：39110230148（PR）/39110227707（push），均验证源码1d37136。之后0a63240只有文档；PR #20已在北京时间16:17普通merge至main，合并提交35d364b，带skip ci。
- 本地已切回main并同步；远端无尚未进入main的支线提交。相关main-base PR自动标记合并；堆叠PR #12/13/16/17/18在确认head为main祖先后关闭，原分支保留。最后只补文档收口，不重复源码检查。

## 2026-10-19 · 本地ADB恢复

- 用户要求继续修复离线，并询问QEMU图标及MCP是否为原因。图标是正常模拟器进程；不把MCP存在当作根因。
- 官方SDK已备份并更新，但旧数据仍offline；同AVD/端口用独立数据镜像恢复。随后完整备份原AVD，将可用镜像放入默认路径，保留旧数据，不删除备份。具体坏文件/设置未定位。
- 已核对默认路径正常冷启动、adbd running、ADB device、qtwindow；最新APK安装/启动、MCP截图/UI树通过。Android reboot和完整停止再冷启动后APK及测试文件均保留；停止前sync。MCP保持manual/ready、automatic_shutdown=false。
- 仅环境与文档变更，无项目源码变更；完整交互和硬件回归仍按后续计划补。详见docs/adb-recovery.md。

## 2026-10-24 · 导入合并 main + UI 细化第 1 轮

- 云编译恢复：Room KSP 的 sqlite-jdbc 原生库改解压到 `build/`（云端 `/tmp` 不能映射可执行代码），云端 `assembleDebug` 成功（72eadd0a5da44281）。云端 ARM64 没有 Conscrypt 原生库，Robolectric 测试只在本地和 GitHub CI 跑。
- archive-import：ZIP 导入（预览、替换、before-import 备份、会话中拒绝）+ 向导校验失败滚到第一个错误。本地 core58+app914、lint、debug/release APK、offline 检查通过；GitHub CI 39977846474（e6ea940）/ 39997662014（ac542b9）成功。redroid（Oracle ARM64 Android 16）导出 → 导入 → 面板即时恢复 → 强停重启仍在；连接中导入被拒绝。
- 老板在聊天明确「跑完这个」允许合 main：main 快进到 f3fa90c（最后一个提交为文档，带 skip ci），origin/main 已核对。
- UI 细化第 1 轮（分支 ui-polish-r1）：写入确认的“来自 N 秒前”按秒/分钟/小时/天显示；向导预览占位卡带“预览”徽标、未命名写“未命名组件”；导出浮层在交给系统保存/分享后收起。每项都有去掉修复会失败的断言。云端 MCP 本轮连不上，这三项未做设备验收（未验证）。
- redroid 补齐 PR #21 三项设备验收（云构建 3df2645556324f70）：向导预览徽标、写入确认“来自 1 分钟前的读取”、导出保存后浮层收起（Downloads 有 modbus-export-20261024-054235.zip）均通过。每次云构建 debug 签名不同，安装前卸载旧包。
- 老板在聊天确认合并：PR #21 普通 merge 进 main，合并提交 6972cdd，带 skip ci。

## 2026-10-24 · 更名 Corn Modbus（分支 brand-corn-modbus）

- 老板要求：换玉米图标、应用名 Corn Modbus、设置最后加 by xingyingyuzhui（链到仓库）、代码里藏作者信息、仓库改非商业授权开源。
- 图标：自适应图标（前景 76dp 原图 + #232323 背景 + 单色主题层）；名称改 Corn Modbus（包名不变）；「我的」/完整设置/关于加署名并打开仓库；Authorship + BuildConfig + manifest meta-data + 导出 ZIP 注释；LICENSE.md 为 PolyForm Noncommercial 1.0.0，README 补授权说明。


## 2026-10-24 · 合并 PR #22 Corn Modbus

- 设备验收通过（redroid，build 38ad9086964447ff，f5111f9）。
- 已合进 main。

## 2026-10-24 · 发布 v0.1.0

- 老板要求发布安装包，版本定位 0.1.0（versionName 已是 0.1.0 / versionCode 1，未抬版本）。
- 本地 `./gradlew :app:assembleRelease :app:assembleDebug` 成功；`tools/verify_offline_apks.py` 两变体均无 INTERNET。
- 仓库无正式 release keystore：release APK 先为 unsigned，再用本机 Android Debug 密钥 zipalign+apksigner 签名后发布（便于安装；非商店正式签名）。
- 打 annotated tag `v0.1.0`（指向 a61ffcf）并推送；GitHub Release（非 draft）：https://github.com/xingyingyuzhui/modbus_android/releases/tag/v0.1.0
- 附件：`Corn-Modbus-0.1.0.apk`（约 11.0 MiB，sha256 ac15b1e2ee60b46160d46a067ab17529ccbe4917a1c348e84ee47305700205f8）、`Corn-Modbus-0.1.0-debug.apk`（约 16.3 MiB，sha256 eec73ebdedcaa66ad7057b1dddb5c3888fdd3a894f78efa4675945ddd0d90be9）。APK 未入库（`.gitignore`）。

## 2026-10-24 · README 界面预览

- 从已入库的 Roborazzi 浅色竖屏基线复制 6 张到 `docs/readme/`（连接、工作台、面板、曲线、日志、我的）。字节与 `ShellScreenshotTest.*_light_portrait.png` 相同。未新录基线，未使用实机或模拟器截图。审阅目录里的组件图仍是旧标题「Modbus」，没有放进 README。
- README 增加界面预览、功能一览，以及 Release v0.1.0 两个 APK 的下载说明。PolyForm Noncommercial 声明和作者署名保持原样。未改应用代码、包名、版本和许可证正文。
- 文档与图片副本，不跑本地构建。提交 `4582fff` 带 `[skip ci]`。PR #23：https://github.com/xingyingyuzhui/modbus_android/pull/23 ，不合并 main。

## 2026-10-24 · 状态栏跟随 App 主题 + 仓库改名 corn-modbus（分支 status-bar-follow-theme）

- 老板反馈：系统浅色、App 手动深色时，状态栏（时间 / 电量）看不见。原因：`enableEdgeToEdge()` 默认只看系统深浅色。新增 `SystemBarsFollowTheme()` 按 App 深浅重新设置状态栏 / 导航栏图标；`SystemBarsThemeTest` 3 例（去掉修复时 2 例失败）。
- 本地 `test :app:lintDebug :app:assembleDebug` 通过（core + app 共 982 例，0 失败）。
- redroid 设备验收（云构建 b7d037cb86f4493c，43f0c62）：系统浅色下切到「深色」，状态栏立即变为白色图标、导航栏变深；强停冷启动后仍正确；「跟随系统」浅色时仍为深色图标。
- GitHub 仓库改名 `xingyingyuzhui/modbus_android` → `xingyingyuzhui/corn-modbus`（旧地址 301 重定向），描述改为 Corn Modbus；应用内仓库链接、LICENSE Required Notice 同步改新地址。云构建项目登记的仓库地址仍是旧 URL（靠重定向可用）。
- 合并 main（PR #23 README 预览）进分支，board 冲突两段都保留；删掉重复的一段本分支记录。
- 老板要求合并后升版本发新包：versionName 0.1.1 / versionCode 2，README 下载链接改为 v0.1.1 与新仓库地址。本地 `test`（982 例，0 失败）、`:app:lintDebug`、`:app:assembleRelease :app:assembleDebug` 通过，`tools/verify_offline_apks.py` 两变体通过。

## 2026-10-24 · 合并 PR #24 + 发布 v0.1.1

- 老板在聊天确认「搞完之后合并到 main，然后升级版本号发新包」。PR #24 CI 通过（push 40095177589 / pull_request 40095179630，5dd7855），普通 merge 进 main，合并提交 31be6ec 带 skip ci。
- annotated tag `v0.1.1` → 31be6ec（tree 与已构建的 5dd7855 相同）。GitHub Release（非 draft）：https://github.com/xingyingyuzhui/corn-modbus/releases/tag/v0.1.1
- 附件：`Corn-Modbus-0.1.1.apk`（release，zipalign + 本机 Android Debug 密钥签名，与 0.1.0 同钥可覆盖升级；sha256 1efadf27965c1f87e720fa042a1a06fb7dfe12243adabe5211ca8960793d8ebc）、`Corn-Modbus-0.1.1-debug.apk`（sha256 4974a96851b95dd2c119ca840dfe83ee6bed79b002d4bf3ef925572902ed7e4b）。APK 不入库。

## 2026-10-24 · 面板 UI v3 设计稿（分支 design-panel-v3，仅设计稿）

- 老板反馈：面板及其子页面的 UI / UX 不太好，要求再设计一版，先出预览图。
- 先用本地 Roborazzi 重新渲染当前 main 的面板各页面做现状核对（输出只留在本地 /tmp；录制会覆盖仓库里已跟踪的基线，已全部还原，未提交）。
- 新增 `docs/design/panel-v3.html`（沿用 v2 的 token，#b=1/2/3 切换画板）及三张渲染图 `panel-v3-b1-main.png` / `panel-v3-b2-edit.png` / `panel-v3-b3-wizard.png`。内容：页头 3 行压成 2 行（页签 + 轮询胶囊）、正常不打标签只标异常、控制类 ⚡ 与读数分开且按钮改浅底描边、寄存器表三列、空状态引导；编辑改点选 + 底部工具条、面板设置收进浮层；曲线全屏页头化；向导按只读 / 会写分组、第 2 步只留必填、第 3 步承载名称 / 单位 / 阈值 / 尺寸。
- 只是设计稿，未改应用代码；等老板看图定稿后再分 PR 实现（会涉及注意事项 33 / 36 / 38 等尺寸断言的更新）。

## 2026-10-24 · 面板 v3-1 主页（分支 panel-v3-1-main，叠在 design-panel-v3 上）

- 老板看过设计稿 PR #25：「就按照这个设计，不错，开始改动」。按规范 18 分四个 PR：v3-1 主页 / v3-2 编辑 / v3-3 向导 / v3-4 曲线全屏与行修改。
- v3-1：页头两行（页签 + 轮询胶囊，去掉会话条和 chips）；只标异常（过期写「读取失败 · N 秒前的值」并每秒更新）；值 mono 28、名称 13、底行贴底；控制卡 ⚡ + `TonalButton`，广播红字单独一行；寄存器表三列整行可点；空面板三步引导；曲线卡图例带当前值；横屏一行页头。
- 测试更新到 v3（会话条 / chips / 正常标签 / 时间戳 / 灯 14 / 进度条 8 / 按钮文案 / ⤢ 位置）并新增：不打正常标签、页头变矮、胶囊四种状态、整行可点、表头信息、值字号 28。抽查：加回「正常」标签时 `normalCardsHaveNoBadgeOnlyProblemsDo` 失败。
- 本地 `test`（987，0 失败）、`:app:lintDebug`、`:app:assembleDebug` 通过。注意事项 33 / 34 更新，新增 72–76。
- redroid 设备验收（云构建 1887255d862c48c8，bc5e3c9）：空面板三步引导、「去连接 ›」跳连接页；主站连上后第 2 步打勾、胶囊写「主站」；向导加数值组件后主页显示页签「面板 1」、胶囊「1000 ms」+ 暂停、卡片真实读数无「正常」标签、底行只有地址；点胶囊变「已暂停」+ ▶。控制卡 / 寄存器表 / 横屏未在设备上验（只有 Roborazzi）。

## 2026-10-25 · 面板 v3-2 编辑（分支 panel-v3-2-edit，叠在 panel-v3-1-main 上）

- 编辑模式按 v3：页头「✕ 编辑 · 面板名 ⚙ 完成」+ 一行提示；卡片安静的编辑态（⠿、尺寸标签、灰值、底行“类型 · 地址”，灯不发光）；点选 → 底部工具条（尺寸分段 / 前移 / 后移 / 配置 / 删除，按钮 52 高）；网格最后一格「＋ 添加组件」；名称 / 列数（2 / 3 / 4 分段）/ 全部面板 / 新建 / 删除面板收进 ⚙ 页内浮层（可滚动）；编辑时隐藏 dock / 侧栏。卡片上的 ×、角标、虚线框去掉。
- 测试：编辑页测试按 v3 重写（选中 / 工具条 / 尺寸 / 前后移两端置灰 / 无障碍动作 / 设置浮层 / 曲线只有 2 列尺寸），各流程测试的“点卡片配置”“点 × 删除”改成“选中 → 工具条”；`draftSurvivesLeavingForAnotherTabAndComingBack` 改为 `editModeHidesTheDockAndKeepsTheDraftUntilDone`（编辑时不能再切页）。本地 `test`（989，0 失败）、`:app:lintDebug`、`:app:assembleDebug` 通过。注意事项 36 / 41 更新，新增 77。
- redroid 设备验收（云构建 947496a622db467d，39c0103）：向导加组件回来是 v3 编辑页；点卡片选中 + 工具条；点「2 × 1」卡片立即变宽；只有一个组件时前移 / 后移置灰；⚙ 打开面板设置；「完成」后尺寸保存、dock 恢复。发现并修复：① 编辑态没有 dock 时，设置浮层底部被系统导航条挡住（`InPageSheet` 在没有 dock 时自己让开导航条 + 键盘）；② 从向导回编辑页时 dock 闪一下（离开面板页时不再把“编辑中”复位，只在退出编辑时复位）。两处修复待下一次云构建复核。

## 2026-10-25 · 面板 v3-3 向导对稿（分支 panel-v3-3-wizard，叠在 panel-v3-2-edit 上）

- 按 v3 稿收紧向导：曲线系列行去掉行内线宽 / 线型样例和「2px」，轴改成独立小标签（左轴 / 右轴，`wiz-series-axis-*`）；「系列 N / 8」变成块内标题，空列表也先写「系列 0 / 8」再写原因（文案改成「点下面的“添加系列”」）；曲线预览标题改成「实时预览 · 窗口」并在名称行最右加「预览」徽标（`CurveCardBody(previewBadge=…)`，面板里的真卡片不传）；第 2 步曲线预览、第 3 步曲线预览都放在虚线「假网格」里。
- 数值第 2 步：换算摘要补上小数位（`× 0.1 + 0 · 1 位小数`）；16 位类型不再画字序分段，只留一句「字序只在 32 / 64 位类型时出现」。
- 测试：`PanelCurveWizardTest` 轴标签断言换成新 tag 并新增「行内不出现 px」、曲线预览徽标；`PanelWizardTest` 断言换算摘要带小数位、无字序控件 + 提示。本地 `:app:testDebugUnitTest` 全绿。
- 渲染图更新到 `docs/review/ui-pr-v3-3/`（`wizard_*` / `panel_wizard_*`，33 张）。

## 2026-10-25 · 合并面板 UI v3（#25–#28）+ 发布 v0.1.2

- 老板确认整体 review 通过后合并发版。普通 merge：#25 设计稿 → #26 主页 → #27 编辑 → #28 向导，合并提交均带 [skip ci]。
- versionName 0.1.2 / versionCode 3；README 下载链接改为 v0.1.2。
- 已知未挡发版：向导未做真机/redroid；开关/按钮/寄存器表向导未逐张对稿。
- 本地 `./gradlew test :app:lintDebug :app:assembleRelease :app:assembleDebug` 通过（BUILD SUCCESSFUL）；offline APK 校验通过。
- annotated tag `v0.1.2` → 合并后的发版提交。GitHub Release（非 draft）：https://github.com/xingyingyuzhui/corn-modbus/releases/tag/v0.1.2
- 附件：`Corn-Modbus-0.1.2.apk`（release，zipalign + 本机 Android Debug 密钥签名，与 0.1.0/0.1.1 同钥可覆盖升级；sha256 f7689dfefbf83ebf2dcb2161edecb33460b475607a2a04123c231567cb24c99d）、`Corn-Modbus-0.1.2-debug.apk`（sha256 74a2f6629c9759186ccd72dbc68489ec6dc9311d1861423583c6bc202b9498ee）。APK 不入库。

## 2026-10-26 · 换 Modbus 品牌图标 + 发布 v0.1.3

- 老板授权：Corn-Flashing review 修完后，把 Corn Modbus 图标换成新图（玉米 + 交换面板箭头，不是 Flashing 的芯片图），然后合并发版。
- 启动器自适应图标：各密度 `ic_launcher_foreground` / `ic_launcher_monochrome` 按规范 68 重做（原图 76dp 居中、背景仍 #232323）；单色层是玉米+面板剪影（去掉圆角底板描边）。应用内无单独品牌 drawable（关于/设置仍是文字署名）。
- versionName 0.1.3 / versionCode 4；README 下载链接改为 v0.1.3。
- 本地 `./gradlew --offline :app:assembleRelease :app:assembleDebug` 通过；`tools/verify_offline_apks.py` 两变体通过。
- annotated tag `v0.1.3` → 发版提交。GitHub Release（非 draft）：https://github.com/xingyingyuzhui/corn-modbus/releases/tag/v0.1.3
- 附件：`Corn-Modbus-0.1.3.apk`（release，zipalign + 本机 Android Debug 密钥签名，与 0.1.0–0.1.2 同钥可覆盖升级；sha256 997013b52e8b0ded7382cbaa415599f6882fc4608192e3c6c6eecc2ad9f7c179）、`Corn-Modbus-0.1.3-debug.apk`（sha256 e5d02ad360e5d74fdeb6f1b09c46b952bdb41a9c3502d0bf8703e2d2d4832ce1）。APK 不入库。
- 未提交 `app/src/test/screenshots/*.png`（规范 22）。未在本轮做设备验收（未验证）。

## 2026-10-29 · 发布 v0.1.4

- 内容：#30（串口卡四格下拉、选择控件统一下拉；面板写入功能码可选 FC15/16；从站删除地址段、删 Unit 连带规则与值；面板编辑页可清不显示组件）、#31（从站可删光全部 Unit，0 个 = 不应答）。
- versionName 0.1.4 / versionCode 5；README 下载链接改为 v0.1.4。
- 本地 `./gradlew :app:assembleRelease :app:assembleDebug` 通过（本机 Gradle 依赖缓存已空，本轮联网解析依赖）；`tools/verify_offline_apks.py` 两变体通过。
- annotated tag `v0.1.4` → 发版提交。GitHub Release（非 draft）：https://github.com/xingyingyuzhui/corn-modbus/releases/tag/v0.1.4
- 附件：`Corn-Modbus-0.1.4.apk`（release，zipalign + 本机 Android Debug 密钥签名，证书 SHA-256 cd8e8bc2…22af94，与 0.1.0–0.1.3 同钥可覆盖升级；sha256 d67143561582bde01058310d4acd7f5598074477ce8db23fe4189a16789dfe1c）、`Corn-Modbus-0.1.4-debug.apk`（sha256 ace435d3b92ca87c4f16add8b4b665426b17392b3f2e2be451284bf6e04a028c）。APK 不入库。
- 未在本轮做设备验收（未验证）。
