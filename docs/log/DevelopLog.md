# Ainsmail 开发日志

---
更新日期：2026-11-15
当前分支：`test`（重构后的页面骨架）

## 现在到哪一步

页面框架、账号接入、Task 2.1 首拉落库已做完。下一步是 2.2 增量 / IDLE / 已读回写。正文清洗、模型分类、手势与视觉还没开始。


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

1. IMAP 同步、本地库、增量 / IDLE
2. 正文清洗落本地文件，列表不再读内存 `Mail.body`
3. DeepSeek 分类和标签池衰减
4. 需求第 7 节手势：列表横滑开遮罩、长按多选、左右滑快捷操作
5. 样式、动效、流光等高完成度视觉

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