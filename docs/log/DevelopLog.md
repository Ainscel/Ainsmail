# Ainsmail 开发日志

---
更新日期：2026-11-15
当前分支：`test`（重构后的页面骨架）
需求：`docs/prd/业务需求文档.md`
决策：`docs/issues/框架问题.md`
审阅：`docs/issues/框架审阅.md`


## 现在到哪一步

页面框架和账号接入已经做完。样式精修、动效、IMAP 收信、正文清洗、模型分类都还没开始。

可以接着做收信，不必再推倒骨架。


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
- 已登录账号和凭证：`security/AccountBook.kt`、`security/SecretStore.kt`

当前进度的结构结论以 `docs/issues/框架审阅.md` 为准。已经定下来不做的事以 `docs/issues/框架问题.md` 为准。

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