# Ainsmail 开发日志

---
更新日期：2026-11-15
当前分支：`test`（重构后的页面骨架）

## 现在到哪一步

页面框架、账号接入、Task 2.1 首拉落库、Task 2.2 增量 / IDLE / 已读回写已做完。Task 0.1 列表时间分隔、Task 0.2 一周内的日期、Task 0.3 正文长按选择已做完。Task 3.1 正文落文件已做完。下一步是 3.2 阅读与单个 WebView。模型分类、手势与视觉还没开始。


## 已经有的

- 窄屏 / 宽屏同一套导航。宽屏用 `TwoPaneStrategy` + `SplitLayout`：邮件是「列表 | 正文」，设置是「栏目 | 栏目内容」。分界线可拖，比例默认 40%，范围 35%～60%。
- 左侧遮罩：头像列 + 板块列。按钮、点空白、返回键开关。列表和遮罩上的横滑已删掉，等手势那一节再整段做。
- 邮箱会话是纯归约：`MailboxIntent` → `reduce` → `MailboxSession` → 投影。
  - 根界面只读 `MailboxChrome`（范围、板块、账号、提示）
  - 列表只读 `MailboxList`（筛选、行）
  - 正文按导航里的 mailId 取 `MailDetailUi`，打开的信不写入会话
- 样例账号和邮件由 `MailApp` 注入 `MailboxViewModel`。没有 Repository，也没有第二份实现。
- 设置里能读写 DeepSeek / Google 客户端密钥（`SecretStore`）。通知、隐私栏目是空的。
- 列表行结构、类型胶囊、筛选对话框、多选菜单、删除 / 归档 / 打标签的内存逻辑都在。视觉和弹簧还是占位水平。
- 归约有单元测试。`MailboxReducerTest` 覆盖板块、标签、删除、归档、以及「已读后 chrome 不变」。

## 明确还没做

按需求文档往后推，大致是这些大块：

1. 增量、IDLE 和已读回写已在 2.2 完成
2. 列表时间分隔（0.1）已做完：投影里分桶，列表另画一条分隔
3. 正文清洗落本地文件（3.1）已做完。下一步是 Markdown 阅读与单个 WebView（3.2）。列表预览来自洗完的 Markdown，不再读内存 `Mail.body`
4. DeepSeek 分类和标签池衰减
5. 需求第 7 节手势：列表横滑开遮罩、长按多选、左右滑快捷操作。分隔线不接手势
6. 样式、动效、流光等高完成度视觉

不要提前做：只有一个实现的 Repository、自建聚合的空类型、第三种宽屏空右栏抽象、撤销超时后再同步服务器。

## 接着开发时先看什么

- 改邮箱规则：`ui/mailbox/`（`MailboxReducer`、`MailboxProjection`、`MailboxViewModel`）
- 改导航和根组合：`ui/nav/`
- 改分栏 / 遮罩 / 头像列：`ui/shell/`
- 改列表 / 正文 / 设置页：`ui/list/`、`ui/detail/`、`ui/settings/`
- 领域枚举和模型：`model/`
- 样例数据：`data/SampleMailbox.kt`
- 已登录账号和凭证：`security/AccountBook.kt`、`security/SecretStore.kt`、`security/ProviderCredentials.kt`

---
下一次更新在后面接续日志。

---

## 2026-11-15 20:47

按开发先后写了 `docs/tasks/DevelopTask.md`。第 0 项页面骨架已勾完。下一步从第 1 项账号接入开始。视觉、手势和收信都还没勾。

## 2026-11-15 23:55

第 1 项账号接入完成。

- 加密文件升到 schema 2。DeepSeek 和 Google 客户端仍在 `Secrets`。QQ 授权码和 Gmail 刷新令牌在同一份文件的 `accounts` 段，不进 `Secrets`，`toString` 不带原文。保存密钥时不会清掉账号。
- QQ / Foxmail 用邮箱和授权码添加。显示名默认是 `@` 前一段，可以从相册选头像。这一阶段不连 IMAP。
- Gmail 在客户端 ID 或密钥没保存时不能添加。登录用系统浏览器、PKCE 和 `127.0.0.1` 回环，范围含 openid、email、profile、`https://mail.google.com/`。只把刷新令牌落盘。
- 还没有真实账号时，头像列和列表继续用样例。写入第一个账号后改成已登录账号，样例信清掉。设置里可以改显示名、换头像、删除账号。
- 下一步是第 2 项收信与本地库。手势和视觉仍未做。

## 2026-11-16 01:55

为注册 Google OAuth 客户端写了 `docs/legal/隐私政策.md`。

- 按现有产品写：无自有服务器；凭证用系统密钥库加密留在本机；Gmail 范围为 `openid`、`email`、`profile`、`https://mail.google.com/`；只有填写 DeepSeek 密钥后才把单封轻量正文发出去。
- 单独写了 Google 用户数据的有限使用，供同意屏幕上的隐私政策链接使用。
- 联系邮箱仍是占位，公开前需要换成真实邮箱。任务清单没有对应项，未改 `DevelopTask.md`。

## 2026-11-16 02:30

Gmail 登录从桌面回环改成 Android 客户端 + Play 授权。

- 设置里以 Android 客户端 ID 为准；Web 客户端密钥改为可选，不再作为添加门槛。
- 登录用 `AuthorizationClient`，授权完成后回到应用，不再监听 `127.0.0.1`。
- 仍请求 offline access，用 server auth code 换刷新令牌；若公开客户端换令牌失败，可再填同项目 Web 客户端密钥。

## 2026-11-16 02:35

按上述实现修正了 `docs/prd/需求文档.md`（版本 0.3）。

- 「桌面应用 + 系统浏览器 + 本机回环 + 必填客户端密钥」改为「Android 客户端 + Play 授权回应用 + 必填客户端 ID，Web 密钥可选」。
- 补充包名、SHA-1、同意屏幕与验收、模块表里的对应表述。

## 2026-11-16 02:42

用根目录 `Ainsmail.jpg` 换上应用图标。

- 生成各密度 `ic_launcher` / `ic_launcher_round` / `ic_launcher_foreground`。
- API 26+ 走白色底的自适应图标；清单补上 `roundIcon`。

## 2026-11-16 03:05

登录（尤其 Gmail / Play 授权）补了可诊断错误日志。

- 统一标签 `AinsmailLogin`（`LoginLog`）。只记步骤与原因，不写客户端密钥、授权码、访问令牌、刷新令牌原文。
- `GoogleAndroidLogin`：缺 Gmail 范围、无 server auth code、换令牌 HTTP / OAuth 错误、缺刷新令牌、userinfo 失败、`ApiException` 状态码都会打日志。
- `AccountLoginViewModel` / `AccountBook`：未配置客户端、授权页取消、落盘失败等路径同样记录。
- 令牌响应用 `diagnoseTokenResponse` 只暴露 `error` / `error_description` 或字段有无；单测覆盖不泄密。

## 2026-11-16 03:35

按「仅 Android 客户端」合规要求重构 Gmail 登录，清掉桌面回环 / offline access 遗留。

- 根因：`requestOfflineAccess(Android 客户端 ID)` 不符合 Google 规定（该参数必须是 Web server client），手机上表现为 `ApiException 8 [28405]`。
- 授权请求只带四个范围，不再 offline access、不再换刷新令牌、不再传客户端 ID 给 Play。
- 加密 schema 升到 3：Gmail 存 `accessToken` + 过期时间；去掉 `refreshToken`、设置里的 Web 客户端密钥与相关换令牌代码。
- `AccountLoginViewModel` 去掉 `pendingClient` / `waitingForBrowser` 等回环时代命名；设置与隐私政策、需求文档同步。

## 2026-11-16 03:50

对 Task 1 账号模块做可维护性 / 可扩展性审阅，结论写入 `docs/issues/审阅记录.md`。

总评：不是屎山；凭证边界与 `AccountBook` SSOT 可保留。要在收信前收口的雏形包括：同步层凭证出口、Gmail 令牌过期判定、设置反向依赖 `ui.account`、登录对话框状态堆在 `MailApp`、双 `SecretStore`、头像选择器重复、`AvatarScroller` 体积。

本轮按审阅落地：`AccountError` / `readImage` 迁出 UI 包、登录步骤入 VM、共享 `SecretStore`、`ProviderCredentials` + `credentialsFor`、Gmail 过期判定、图片选择器与 `AvatarScroller` 抽取、合并 `ApiException` 映射。不建空 Repository。

## 2026-11-16 03:56

重写 `DevelopTask.md` 第 2 项「收信与本地库」。

- 原 Task 2 把首拉、增量、IDLE、已读回写、归档不变量平铺成一条，验收边界不清，且把分类 / 标签写成了本阶段交付。
- 改为 **2.1 连通、落库与首拉**（IMAP 连通、板块映射、本地库信头与游标、7 天 / 30 天·50 封首拉、列表改订库 Flow）与 **2.2 增量、保活与本机状态**（UID 增量、IDLE / 15 分钟、当前范围刷新、已读回写、归档本机标记、不抢滑动主线程）。
- 正文清洗仍归第 3 项，模型分类仍归第 4 项；本项只预留类型 / 标签字段。仍强调有真实来源再落库、不建空 Repository。

## 2026-11-16 04:30

完成 Task 2.1：连通、落库与首拉。

- 依赖：Room 2.8.4 + KSP 2.3.4（适配 AGP 9 built-in Kotlin）、Angus Mail（Jakarta IMAP / XOAUTH2）。
- `MailDatabase` / `MailDao`：信头、本机已读与归档、同步游标；upsert 保留本地标记；类型 / 标签字段预留。
- `openImapStore`：QQ 授权码、Gmail XOAUTH2；令牌过期或鉴权失败 → `NeedsReauth`。`AccountBook.updateGmailAccess` 供重登写回。前台静默 `authorize()` 未接 Activity 回调，失败时 Snackbar 提示重登。
- `resolveRemoteFolders`：INBOX + SPECIAL-USE / 常见中英文名；对不上不归错夹。
- `MailFirstSync`：先 7 天，再按全局 50 封 / 30 天补历史；失败不清库。
- `MailboxViewModel`：真账号后订 `MailLocalStore.observeMails()`，触发首拉；本机意图仍走归约并 `persistLocalMails`。
- 单测：`FirstSyncPolicyTest`、`ReplaceMails` 归约。本机工程路径含「项目」，Windows 上 Gradle 测试进程 ClassNotFound（args 文件编码），`:app:compileDebugKotlin` 已通过；ASCII 路径下再跑测试。
- 下一步：Task 2.2 增量 / IDLE / 已读回写。

## 2026-11-16 06:10

审阅并修好 Task 2.1。

- Android Studio 报 KSP 2.3.4 `Plugin was not found`：该版插件标记只有 pom、没有 Gradle module。`settings.gradle.kts` 改为直接解析 `symbol-processing-gradle-plugin`，并加阿里云仓库作备用。
- 去掉 `mail.imaps.ssl.trust=*`，改回系统信任库。
- IMAP 只返回 `RemoteHeader`，Room 实体留在 `data/mail`。删掉未使用的 `readUidValidity` 和 `cursorsForAccount`。
- `MailboxViewModel` 不再引用数据库 / IMAP 类型，观察、首拉、回写由 `MailApp` 传入。
- 本机已读 / 归档先记挂起项，库快照跟上后再放开，避免旧 Flow 把操作冲掉；改显示名不再重连 IMAP。
- `:app:compileDebugKotlin` 通过。

## 2026-11-18 01:50

修好过期账号在设置里消失、侧栏头像还在的问题。

- 设置「账号」以前只订 `AccountSettingsViewModel` 的目录。目录还停在 `Loading` 时页面什么都不画，侧栏却仍用邮箱会话里的账号，于是出现幽灵头像。
- 设置列表现在直接读 `MailboxViewModel.savedAccounts`（样例不计入）。过期记在会话的 `reauthAccountIds` 里，账号行显示「登录已过期」和「重新登录」。
- 同一邮箱再次登录改为写回原记录的令牌或授权码，不再报「已经添加过」。成功后清掉过期标记并重新收信。头像仍留在侧栏，直到用户自己删除账号。

## 2026-11-18 02:10

登录成功的提示在锁屏解锁后会再弹一次。

- 「已添加账号」由 `savedTick` 驱动。这个计数留在 ViewModel 里，锁屏导致界面重建时 `LaunchedEffect` 会把同一次成功再执行一遍。
- 展示前先在 `ShownTick` 里认领这次计数。同一次成功只弹一次。失败提示和设置里的保存提示用同一套，避免同样被锁屏重放。

## 2026-11-18 03:05

审阅 Task 2.1 首拉与落库，结论写入 `docs/issues/审阅记录.md`。未改业务代码。

- 分层可以接着做 2.2：IMAP 未进 ViewModel，失败不清库，样例和真账号有闸门。
- 写增量之前要单独定契约：本机删除不要改 `remoteFolder`。这一列现在既是服务器夹，又被删除写成废件箱，会和废件箱里相同 UID 撞唯一索引，游标也会认错夹。
- 7 天与历史补齐是两段重复循环。历史补齐在截断前整批 FETCH。本机回写逐封更新，每次都让整表 Flow 再推一次。
- P0 的删除落库方式要单独点头后再动。

## 2026-11-18 03:18

按审阅修了 Task 2.1 的落库和首拉。未改任务勾选。

- 本机删除改记 `locallyDeleted`，`remoteFolder` 保持服务器夹。库从 version 1 迁到 2，旧的「夹被改成废件箱」的行改回主键里的夹名。
- 信封用 `ON CONFLICT(id)` 只更新信封列。本机回写只写已读、归档、本机删除，放进同一事务，并和同步共用一把写锁。类型和标签仍只留在内存。
- 7 天和历史补齐收成一个窗口函数。最近一阶段失败的账号不再补历史。全库到 50 封或预算用尽时，把这些账号的 `historySyncDone` 标上，不改最高 UID。
- 历史补齐先按 UID 截断，再取信封和标志。搜索只用收到日期。附件标记先不取。
- 挂起合并抽到 `LocalMailOverlay`。`:app:compileDebugKotlin` 与单元测试源码编译通过。`testDebugUnitTest` 仍是工程路径含「项目」导致的 ClassNotFound，测试没有真正跑起来。

## 2026-11-18 03:52

审阅 Task 0 壳层（`ui/shell`、`ui/nav`、`ui/detail`），结论写入 `docs/issues/审阅记录.md`。未改业务代码。

- 分栏、越界阻力和遮罩开合按布局 / 绘制阶段读状态，没有按每一帧重组。没有 P0。
- 手势把右栏收成全屏或从全屏拉回时，松手会立刻解除冻结，弹簧期间正文按每一帧宽度重排。点全屏按钮的路径会冻到动画结束。Task 3 / Task 5 之前应收口，这次只记录。
- `MailDetailPane` 有未使用的 `LocalDetailHandlesBack`。`UnavailableKind.Login` 没有构造点。全屏图标和「在新窗口打开」各写了一份 `icon()`。账号遮罩和分栏机制同包，壳层还依赖 `AccountUi` 与 `security.resolveInside`。

## 2026-11-18 04:10

按壳层审查修了全屏弹簧的右栏冻结，并清掉死代码和重复图标。账号遮罩没有搬出 `ui.shell`。

- `animateTo` 在全屏进度动画期间保持 `resizing`。手指离开时若弹簧还在走或即将开始，不再先解开。越界回弹仍松手就按当前栏宽重排。
- 删掉 `MailDetailPane` 里未使用的返回读取、`UnavailableKind.Login` 和对应文案。矢量路径收成 `pathIcon`。
- `SplitDragStateTest` 只断言同步的冻结状态。`:app:compileDebugKotlin` 通过。本机路径含「项目」时单元测试进程仍会 ClassNotFound。

## 2026-11-18 05:50

按列表与会话审查收紧重组范围，并让多选跟着当前可见行走。

- 列表改订 `MailboxPlace`（范围、板块），不再读 `MailboxChrome`。对话框草稿留在外层，行列表单独组合；行点击走同一份 `MailListActions`，没变的行可以跳过重组。
- 已读、标签、类型三种筛选写上之后，多选 id 只留下还看得到的信。多选模式保留，顶栏数量就是可见勾选数。
- 投影对全部邮件只扫一遍，勾选不写进行模型。同一分钟里信件、目录、账号、范围、板块和筛选都没变时，沿用上一份行、板块 id 和标签分组。
- `:app:compileDebugKotlin` 与单元测试源码编译通过。`testDebugUnitTest` 仍因工程路径含「项目」无法启动测试进程。

## 2026-11-18 06:20

修好全部账号下列表角标只露出字的一角。

- 小圆以前用 8.sp 却保留正文行高，字形沉到圆外，只在右下角露出头顶。
- 有账号头像时角标直接画那张图。同一条路径只解码一次，侧栏和列表共用，滑动时不按行重读文件。
- 没有头像时，字号和内边距按侧栏 48.dp / 14.sp 的比例缩小到 16.dp，并关掉字体内边距，两个字完整落在圆心里。
- `:app:compileDebugKotlin` 通过。

## 2026-11-18 06:30

加了临时记帧按钮，和邮箱逻辑分开。

- 调试包右上角有「记帧」。点一次开始，再点停止。停止时把超时帧和最慢的几帧写到 logcat 标签 `AinsmailFrame`，并追加到 `files/debug/frame-jank.log`。
- 回调里只加计数，不把帧率写进界面。按钮只在点击时重组。
- 入口只在 `MainActivity` 叠了一层。删 `ui/debug/FrameJankOverlay.kt` 和这层调用即可去掉。
- `:app:compileDebugKotlin` 通过。

## 2026-11-18 08:00

退回列表右缘纯色淡出，恢复按槽宽做的离屏淡出。

- 纯色盖层画在字形宽度上，不跟分界线走，也会挡住以后的底色动画。
- 代码回到槽宽离屏层加 `DstIn` 的那一版。字形按固定宽度测量，淡出仍跟着栏宽。

## 2026-11-18 08:10

淡出改成只重画右缘一条固定宽度的透明带。

- 字形仍按固定宽度测量，放进尺寸不变的层。槽宽变化时这一层不重录。
- 淡出和测量拆成两个节点。淡出挂在槽宽上，跟着分界线走，不再钉在字形末尾。
- 只有右缘那一条放进离屏缓冲，用透明度渐变把字变透明，背后的颜色可以透出来。带宽不随栏宽变化。
- `:app:compileDebugKotlin` 通过。

## 2026-11-18 08:20

暂时去掉随分界线移动的文字淡出，方便对照帧率。

- 姓名、标题、预览和顶栏标题不再画右缘渐变，也不再做那条离屏淡出。
- 文字仍按固定宽度测量并缓存字形，右缘只按当前槽宽裁切。
- `:app:compileDebugKotlin` 通过。

## 2026-11-18 08:40

对照之后选用右缘淡出，退回整行淡出。

- 120Hz 拖分界线：无淡出平均约 128 帧，右缘淡出约 115 帧，整行淡出约 104 帧。右缘比无淡出平均慢约 1ms，可以接受。
- 当前画法是右缘一条固定宽度的透明带，槽宽变化时不重录整行字形。整行离屏淡出不再使用。
- 代码退回「去掉淡出」的对照提交，恢复右缘淡出。

## 2026-11-18 09:14

审阅 Task 1 账号（`security`、`ui/account`、`ui/settings`，以及 `MailApp` 订阅登录成功的接线），结论写入 `docs/issues/审阅记录.md`。未改业务代码。

- 凭证仍留在加密文件，界面只见 `Account`，IMAP 仍走 `credentialsFor`。没有 P0。
- 根界面订了整份 `LoginUiState`，登录步骤会重组 `MailApp`。取消 Gmail 被 `catch (Exception)` 收成 `Rejected`。
- 设置 VM 里的账号列表只写不读，和 `savedAccounts` 各持一份。`ui.settings` 仍依赖 `ui.account` 的文案和选图。
- P1 等单独点头再改。任务清单未勾。

## 2026-11-18 09:35

按 Task 1 账号审查收口登录状态、取消路径和重复写回。未改任务勾选。

- 登录成功单独放在 `SavedLogin`。`MailApp` 只订这一份来清过期标记，打开登录框不再重组根界面。
- 取消 Gmail 会原样抛出协程取消，迟到的授权结果直接丢掉。Play 任务自己被取消仍记成失败，并收起等待框。
- 写入结果带上账号 id，QQ 和 Gmail 共用按地址写回。写失败先回到改之前的记录。空的 Gmail 令牌不再伪装成授权码缺失。
- 设置页不再自持一份账号列表，也不再额外读一次加密文件。失败文案、选图和密钥显隐离开登录包，QQ 和设置共用。重新登录会带上原来的地址和显示名。授权码不进实例状态。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。

## 2026-11-18 18:40

审阅 Task 2.1 首拉与落库（`data/imap`、`data/mail`、订库 / 挂起 / 回写、`MailApp` 装配），结论写入 `docs/issues/审阅记录.md`。未改业务代码。

- 没有 P0。删除列、冲突更新、历史截断和写锁仍然成立。
- 库 Flow 把整封信换进会话，类型、标签、预览、正文会被清掉。挂起期间旧主题还会盖住库里的新主题。
- 删账号只改加密文件。信头和游标还在，并计入 50 封；同一地址再添加会换成新 id，旧信和新信一起出现。
- P1 等单独点头再改。任务清单未勾。

## 2026-11-18 19:05

按 Task 2.1 审查收了库快照和删账号。未改任务勾选。

- 挂起表只留已读、归档、本机删除。库快照以库行为底，类型、标签、预览、正文从上一份会话抄过来。
- 删账号在首拉同一把锁里删掉信头和游标。最后一个账号先停订库再恢复样例，避免样例信被库推送换掉。
- 两个首拉窗口收成枚举。回写和换信列表按 id 查找。夹名同时认最后一段。未知夹名跳过并记日志。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。`testDebugUnitTest` 仍因工程路径含「项目」在启动测试类时 ClassNotFound，测试没有真正跑起来。

## 2026-11-18 19:53

完成 Task 2.2：增量、前台保活、已读回写。

- 库升到 version 3，信封增加 `messageId`，本机已读未回写记在 `seenPending`。冲突更新仍不覆盖已读、归档、本机删除和这个待回写标记。
- 当前账号或全部邮箱，加上当前板块对应的远程夹，按 UID 只取新信。存档不拉服务器。`UIDVALIDITY` 变化时只重建这一夹，并用 Message-ID 把归档标记套回去，窗口外、UID 没被占用的归档信留在库里。
- 前台对范围内每个账号保持一条 IDLE，盯当前远程夹；正在看存档时改盯收件箱。服务器没有 IDLE，或应用进入后台，改为 15 分钟。后台 WorkManager 只补已经有游标的收件箱，不弹通知。
- 一键已读仍只改当前可见未读，并 `STORE +FLAGS (\Seen)`。归档不移动服务器上的信。Gmail / QQ 的分类夹不进文件夹映射。
- 列表滚动、分界线拖动、遮罩弹簧期间只留最后一份库快照，停下来再应用到列表。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。`testDebugUnitTest` 仍因工程路径含「项目」在启动测试类时 ClassNotFound，测试没有真正跑起来。
- 下一步是第 3 项正文清洗。

## 2026-11-18 20:50

设置里增加「显示 → 收件人头像显示方式」。未改任务勾选。

- 全部邮箱列表右下角的收件账户小圆，可以改成连续同一账户只在第一封显示。中间换成另一个账户后再画。单个账号的列表本来就不画这个小圆。
- 默认仍是每封都显示。偏好写在本机 `ainsmail-display`，不进加密账号文件，切换时不收信。
- 比较用账号 id，放在列表投影里。行缓存换显示方式时整表重算，避免沿用上一份小圆。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。未跑 `testDebugUnitTest`：工程路径含「项目」时测试类仍会 ClassNotFound。

## 2026-11-18 21:10

收件账户小圆增加第三种显示方式。未改任务勾选。

- 「相同账户连续相同寄件人合并展示」：同一收件账户里，连续相同寄件人只在第一封画小圆。寄件人变了，或收件账户变了，都重新画。
- 第二种的文案改成「相同账户连续邮件合并显示」。默认仍是每封都显示。寄件人按列表上的姓名比较。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。未跑 `testDebugUnitTest`：工程路径含「项目」时测试类仍会 ClassNotFound。

## 2026-11-18 21:40

按 Task 2.2 审查收了保活退出和归档 UID。未改任务勾选。

- IDLE 改到单独线程。离开前台或换板块时立刻打断，上一轮连接关掉之后才开始下一轮。槽上的阶段和文件夹对 abort 线程可见。
- 后台同步不再因为一个已删除账号停掉后面的收件箱。已经同步过的范围不再先增量再 IDLE 各连一次。同一条连接只列一次文件夹。
- 窗口外留下的归档和本机删除改用负 UID，避免新代际复用正 UID 时继承归档标记。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。未跑 `testDebugUnitTest`：工程路径含「项目」时测试类仍会 ClassNotFound。

## 2026-11-18 21:50

重写 `DevelopTask.md` 第 3 项「正文与阅读」。未改业务代码。

- 原 Task 3 把清洗落盘和 WebView 阅读平铺成一条。按大约每个任务 2000 行来看，整段大约 3000～4000 行，一次做完验收边界也不清。
- 改为 **3.1 拉取、清洗与落文件**（只取文本部分、安全 HTML、轻量 Markdown、路径与处理时间、优先洗当前这封、洗完再填列表预览）与 **3.2 阅读与单个 WebView**（默认 Markdown、空闲才创建一个 WebView、中途就绪不换当前这封、原版式按钮、分界线松手再画）。
- 外链图片默认占位，加载开关的参数在 3.1，按钮在 3.2。洗完前不补信头预览。删除的撤销仍归第 5 项，分类仍归第 4 项。

## 2026-11-18 22:16

在 `DevelopTask.md` 第 0 项下追加 **0.1 列表时间分隔**。未改业务代码。

- 分桶放在列表投影的 `scanRows`，和收件账户小圆同一次扫描。分隔是另一种列表条目，不进 `Mail` 和归约。
- 档位按顺序只取一档：7 天内「本周」，否则本月「一周前」，否则本年写月份，更早写年份。近 7 天跨月仍归「本周」。
- 手势不绑分隔线，视觉那一节只改线的样子。可与 3.1 并行，排在第 5 项之前。

## 2026-11-18 22:46

完成 Task 0.1 列表时间分隔。

- 分桶是 `timeBucket`：本地日期距今不超过 7 天归「本周」（跨月也一样，未来日期同样归入），否则本月「一周前」，否则本年写月份，更早写年份。`scanRows` 在档位变化时插入 `MailListEntry.TimeDivider`，空档不占一条。
- 列表模型从 `rows: List<MailRowUi>` 改成 `entries`。分隔有自己的 key 和 contentType，不进邮件行的测量和淡出，也不吸顶。多选、已读、删除仍只认邮件 id。同一分钟继续复用整份条目。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。`testDebugUnitTest` 仍因工程路径含「项目」无法启动测试类。

## 2026-11-19 11:44

「本周」改为与今天同一个自然周，不再从今天倒推 7 天。

- 自然周是周一到周日。跨月、跨年的那几天仍归「本周」。本周以外、仍在本月的归「一周前」。
- 列表日期和分桶都用邮件的绝对时刻，再按设备时区换算。手机设为北京时间时，看到的就是北京时间。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。未跑 `testDebugUnitTest`：工程路径含「项目」时测试类仍会 ClassNotFound。

## 2026-11-19 11:53

显示设置增加「一星期内的日期」，并完成 Task 0.2。

- 默认仍是日期：今天显示时分，其余显示月日。相对关系下，今天、昨天用这两个词；同一自然周里更早的显示周一到周日。昨天跨周仍显示「昨天」。
- 周界与列表分隔相同，按设备时区的周一 0:00 到周日结束。偏好写在 `ainsmail-display`，切换时重算行和正文时间，不收信。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。未跑 `testDebugUnitTest`：工程路径含「项目」时测试类仍会 ClassNotFound。

## 2026-11-19 12:13

今天的邮件两种日期模式都只显示时分，不再写「今天」。

- 相对关系从昨天开始：昨天，同一自然周里更早的仍是周一到周日。日期模式从昨天起仍是月日。
- `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。未跑 `testDebugUnitTest`：工程路径含「项目」时测试类仍会 ClassNotFound。

## 2026-11-19 12:50

完成 Task 3.1：拉取、清洗与落文件。

- 库升到 version 4。信封增加正文目录、处理时间和列表预览。冲突更新不覆盖这三列。库里的预览非空时，快照不再用会话里的空预览盖掉。
- IMAP 另开一条连接，只取非附件的 `text/html` 和 `text/plain`，用 BODY.PEEK，不改服务器已读。有 HTML 就用 HTML，否则把纯文本收成简单 HTML。
- Jsoup 洗成安全 HTML，并抽出轻量 Markdown。外链图片默认换成占位，`loadRemoteImages` 留给 3.2。文件先写临时目录，成功再替换。失败留下已有文件和信头。
- 前台一次洗一封。打开还没洗好的信时插到队首，内容区显示「正在整理这封邮件」。洗完用 Markdown 纯文本开头填列表预览，详情直接显示 Markdown。样例信仍走内存正文。
- 删账号和 UIDVALIDITY 重建会清掉对应正文目录。`:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` 通过。清洗、落文件和快照合并的单测在 Gradle 之外跑过。`testDebugUnitTest` 仍因工程路径含「项目」无法启动测试类。
- 下一步是 3.2：默认 Markdown 阅读已经能看，WebView 和原版式按钮还没接。

## 2026-11-19 14:05

在 Task 0 下追加并完成 **0.3 正文长按选择**。

- 详情的主题、寄件人、时间和 Markdown 正文放进 `SelectionContainer`，长按弹出系统的复制和全选。
- 全屏按钮、右下角三颗按钮和「这封邮件不在了」不进选择。列表、分界线和清洗没动。
- `:app:compileDebugKotlin` 通过。

## 2026-11-19 16:00

撤回「详情里 Markdown 链接画成蓝色可点文字」这一版。

- 链接展示和系统浏览器打开仍归 3.2，与 WebView、原版式一起做，避免半成品解析器提前占坑。
- 详情继续把 `body.md` 当纯文本显示。3.2 第一条改回未勾。

