package com.ainscel.ainsmail.ui.mailbox

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ainscel.ainsmail.data.prefs.DisplayPreferences
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.AccountBadgeStyle
import com.ainscel.ainsmail.model.WeekDateStyle
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagCatalog
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.AccountDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 首拉结果里界面需要知道的部分。凭证和异常原文留在数据层。 */
sealed interface MailboxSyncHint {
    val accountId: String
    data class NeedsReauth(override val accountId: String) : MailboxSyncHint
    data class Failed(override val accountId: String) : MailboxSyncHint
}

/** 正文清洗由组合根接上。本类不引用数据库和 IMAP。 */
class BodyWash(
    val attach: (CoroutineScope) -> Unit,
    val resume: (onNeedsReauth: (String) -> Unit) -> Unit,
    val pause: () -> Unit,
    val stop: () -> Unit,
    val prioritize: (String) -> Unit,
    val readMarkdown: suspend (String) -> String?,
)

/** 首拉结果。失败的账号不在 [readyIds] 里，这一轮不进入保活。 */
data class InitialSyncResult(
    val hints: List<MailboxSyncHint>,
    val readyIds: List<String>,
)

/**
 * 没有保存过账号时用传入的样例。
 * 一旦加密文件里有账号，会话改成这些账号，邮件改订本地库 Flow，并触发首拉。
 * 本类不引用数据库或 IMAP 类型；观察、同步、回写、按账号清库由组合根传入。
 */
class MailboxViewModel(
    private val sampleAccounts: List<Account>,
    private val sampleMails: List<Mail>,
    catalog: TagCatalog,
    private val book: AccountBook,
    private val observeMails: () -> Flow<List<Mail>>,
    private val syncAccounts: suspend (List<String>) -> InitialSyncResult,
    private val watchScope: suspend (List<String>, Section, (MailboxSyncHint) -> Unit) -> Unit,
    private val pushSeen: suspend (List<String>) -> Unit,
    private val persistMails: suspend (List<Mail>) -> Unit,
    private val forgetAccounts: suspend (List<String>) -> Unit,
    private val bodyWash: BodyWash,
    private val display: DisplayPreferences,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private var showingSamples = true
    private var syncedAccountIds: Set<String> = emptySet()
    /** 已写库、库快照还没跟上的三个本机标记。 */
    private val pendingLocal = LinkedHashMap<String, LocalFlags>()
    /** 清库还没结束时，不要另开一轮首拉。 */
    private var forgetting = false
    private var forgetGeneration = 0
    private var foreground = false
    private var online = false
    private var snapshotPaused = false
    private var heldSnapshot: List<Mail>? = null
    private val initiallySynced = HashSet<String>()
    private var session = MailboxSession(
        accounts = sampleAccounts,
        mails = sampleMails,
        catalog = catalog,
    )
    private val initialNow = System.currentTimeMillis()
    /** 行上的时间文字按这一分钟生成。同一分钟里内容没变就不再格式化。 */
    private var rowMinute = initialNow / 60_000L
    /** 收件账户小圆的重复方式。和会话无关，换它时不能沿用上一份行。 */
    private var badgeStyle: AccountBadgeStyle = display.accountBadge.value
    /** 本周日期的写法。换它时同样不能沿用上一份行。 */
    private var weekDateStyle by mutableStateOf(display.weekDateStyle.value)
    private val initial = project(
        session,
        initialNow,
        badgeStyle = badgeStyle,
        weekDate = weekDateStyle,
    )
    private var observeJob: Job? = null
    private var watchJob: Job? = null
    private var openJob: Job? = null
    private var openedId by mutableStateOf<String?>(null)
    private var openedReading by mutableStateOf<DetailReading>(DetailReading.Waiting)
    /** 上一轮保活退出并关掉连接之后，下一轮才开始。 */
    private val watchLock = Mutex()

    init {
        bodyWash.attach(viewModelScope)
        viewModelScope.launch {
            display.accountBadge.collect { style ->
                if (style == badgeStyle) return@collect
                badgeStyle = style
                publish(session, restyleRows = true)
            }
        }
        viewModelScope.launch {
            display.weekDateStyle.collect { style ->
                if (style == weekDateStyle) return@collect
                weekDateStyle = style
                publish(session, restyleRows = true)
            }
        }
        viewModelScope.launch {
            book.directory.collect { directory ->
                if (directory is AccountDirectory.Ready) applySaved(directory.accounts)
            }
        }
        viewModelScope.launch(io) { book.load() }
    }

    var chrome by mutableStateOf(initial.chrome)
        private set

    var list by mutableStateOf(initial.list)
        private set

    /** 列表标题和胶囊只用这一份。不订 [chrome]，提示和账号变化不会重组列表。 */
    var place by mutableStateOf(MailboxPlace(session.scope, session.section))
        private set

    /** 加密文件里的账号。样例不放进来，设置页和侧栏因此不会各持一份。 */
    var savedAccounts by mutableStateOf<List<Account>>(emptyList())
        private set

    var reauthAccountIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 正文只订阅信件本身的变化，筛选和多选不带动正文重组。 */
    private var contentEpoch by mutableIntStateOf(0)

    fun onIntent(intent: MailboxIntent): AddTagResult? {
        val outcome = reduce(session, intent, System.nanoTime())
        val previous = session
        session = outcome.session
        if (session != previous) {
            publish(previous)
            if (intent.writesLocalMail()) persistLocal(previous, session)
            if (intent is MailboxIntent.ClearReauth && !showingSamples) {
                startRealMailPipeline(session.accounts.map { it.id })
            }
        }
        return outcome.tagResult
    }

    /** 调用方声明为 `() -> Unit` 时用这个，变更仍然只走 [onIntent]。 */
    fun perform(intent: MailboxIntent) {
        onIntent(intent)
    }

    /** 进程进入前台或离开。离开时停掉 IDLE。 */
    fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        if (foreground) resumeBody() else bodyWash.pause()
        restartWatch()
    }

    /** 默认网络有没有连通。没网时不刷新。 */
    fun setOnline(online: Boolean) {
        if (this.online == online) return
        this.online = online
        restartWatch()
    }

    /**
     * 列表在滚、分界线在拖、遮罩在动时先攒着库快照。
     * 停下来再应用最后一份，避免刷新挤进当前这一帧。
     */
    fun setSnapshotPaused(paused: Boolean) {
        if (snapshotPaused == paused) return
        snapshotPaused = paused
        if (paused) return
        val step = nextSnapshot(paused = false, held = heldSnapshot, incoming = null)
        heldSnapshot = step.held
        val apply = step.apply ?: return
        if (!showingSamples) applySnapshot(apply)
    }

    /**
     * 在组合里调用。读取 [contentEpoch] 才会在信件变化后重组。
     * 打开哪封信由导航传入，不写入会话。
     */
    fun detail(id: String): MailDetailUi? {
        contentEpoch
        val structural = session.detailOf(id, System.currentTimeMillis(), weekDateStyle) ?: return null
        if (showingSamples) return structural
        val reading = if (openedId == id) openedReading else DetailReading.Waiting
        return structural.copy(reading = reading)
    }

    /**
     * 打开一封真信。已经洗好就读 Markdown；还没有就把这一封插到队首，内容区保持等待。
     */
    fun onMailOpened(id: String) {
        if (showingSamples) return
        if (openedId == id && (openJob?.isActive == true || openedReading is DetailReading.Markdown)) return
        openedId = id
        openedReading = DetailReading.Waiting
        openJob?.cancel()
        openJob = viewModelScope.launch {
            val ready = session.mails.find { it.id == id }?.bodyReady == true
            if (!ready) {
                bodyWash.prioritize(id)
                snapshotFlow {
                    contentEpoch
                    session.mails.find { it.id == id }?.bodyReady == true
                }.first { it }
            }
            if (openedId != id || !currentCoroutineContext().isActive) return@launch
            val text = try {
                bodyWash.readMarkdown(id).orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AinsmailBody", "read id=$id ${error.javaClass.simpleName}")
                return@launch
            }
            if (openedId != id) return@launch
            openedReading = DetailReading.Markdown(text)
        }
    }

    /** 在组合里调用。和 [detail] 用同一次订阅。 */
    fun subjectOf(id: String): String? {
        contentEpoch
        return session.subjectOf(id)
    }

    private fun applySaved(saved: List<Account>) {
        val removed = if (showingSamples) {
            emptyList()
        } else {
            val nextIds = saved.map { it.id }.toSet()
            session.accounts.map { it.id }.filter { it !in nextIds }
        }
        when {
            saved.isEmpty() -> {
                if (!showingSamples) {
                showingSamples = true
                syncedAccountIds = emptySet()
                initiallySynced.clear()
                heldSnapshot = null
                pendingLocal.clear()
                stopRealMailPipeline()
                    onIntent(MailboxIntent.RestoreSamples(sampleAccounts, sampleMails))
                }
            }
            showingSamples -> {
                showingSamples = false
                pendingLocal.clear()
                onIntent(MailboxIntent.UseRealAccounts(saved))
                startRealMailPipeline(saved.map { it.id })
            }
            else -> {
                onIntent(MailboxIntent.ReplaceAccounts(saved))
                if (removed.isEmpty()) {
                    val ids = saved.map { it.id }
                    if (ids.toSet() != syncedAccountIds) startRealMailPipeline(ids)
                }
            }
        }
        if (removed.isNotEmpty()) forgetRemoved(removed, saved.map { it.id })
    }

    private fun forgetRemoved(removed: List<String>, nextIds: List<String>) {
        val prefixes = removed.map { "$it:" }
        pendingLocal.keys.removeAll { id -> prefixes.any { prefix -> id.startsWith(prefix) } }
        removed.forEach { initiallySynced.remove(it) }
        val generation = ++forgetGeneration
        forgetting = true
        watchJob?.cancel()
        watchJob = null
        syncedAccountIds = nextIds.toSet()
        viewModelScope.launch {
            try {
                forgetAccounts(removed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AinsmailSync", "forget_accounts ${error.javaClass.simpleName}: ${error.message}")
            } finally {
                if (generation == forgetGeneration) forgetting = false
            }
            if (generation == forgetGeneration && !showingSamples) {
                startRealMailPipeline(session.accounts.map { it.id })
            }
        }
    }

    private fun startRealMailPipeline(accountIds: List<String>) {
        syncedAccountIds = accountIds.toSet()
        if (forgetting) return
        if (observeJob == null) {
            observeJob = viewModelScope.launch {
                observeMails().collectLatest { mails ->
                    if (showingSamples) return@collectLatest
                    onDatabaseMails(mails)
                }
            }
        }
        if (foreground) resumeBody()
        restartWatch()
    }

    private fun stopRealMailPipeline() {
        observeJob?.cancel()
        observeJob = null
        watchJob?.cancel()
        watchJob = null
        openJob?.cancel()
        openJob = null
        bodyWash.stop()
    }

    private fun resumeBody() {
        if (showingSamples || !foreground) return
        bodyWash.resume { accountId ->
            viewModelScope.launch { showHints(listOf(MailboxSyncHint.NeedsReauth(accountId))) }
        }
    }

    private fun restartWatch() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            watchLock.withLock {
                if (showingSamples || forgetting || !foreground) return@withLock
                val ids = scopeIds()
                val section = session.section
                val unchecked = ids.filter { it !in initiallySynced }
                if (unchecked.isNotEmpty()) {
                    if (!online) return@withLock
                    val result = syncAccounts(unchecked)
                    showHints(result.hints)
                    initiallySynced.addAll(result.readyIds)
                }
                if (!foreground || !online || showingSamples || !currentCoroutineContext().isActive) return@withLock
                val ready = ids.filter { it in initiallySynced }
                if (ready.isEmpty()) return@withLock
                watchScope(ready, section) { hint ->
                    viewModelScope.launch { showHints(listOf(hint)) }
                }
            }
        }
    }

    private fun scopeIds(): List<String> = when (val scope = session.scope) {
        MailboxScope.All -> session.accounts.map { it.id }
        is MailboxScope.Account -> listOf(scope.id)
    }

    private fun showHints(hints: List<MailboxSyncHint>) {
        for (hint in hints) {
            when (hint) {
                is MailboxSyncHint.NeedsReauth ->
                    onIntent(MailboxIntent.ReportNeedsReauth(hint.accountId))
                is MailboxSyncHint.Failed ->
                    onIntent(MailboxIntent.ReportSyncFailed)
            }
        }
    }

    private fun onDatabaseMails(mails: List<Mail>) {
        val step = nextSnapshot(snapshotPaused, heldSnapshot, mails)
        heldSnapshot = step.held
        val apply = step.apply ?: return
        applySnapshot(apply)
    }

    private fun applySnapshot(mails: List<Mail>) {
        onIntent(
            MailboxIntent.ReplaceMails(
                mergeSnapshot(pendingLocal, mails, session.mails),
            ),
        )
    }

    private fun persistLocal(previous: MailboxSession, next: MailboxSession) {
        if (showingSamples) return
        val previousById = HashMap<String, Mail>(previous.mails.size)
        for (mail in previous.mails) previousById.putIfAbsent(mail.id, mail)
        val changed = ArrayList<Mail>()
        for (mail in next.mails) {
            if (previousById[mail.id] != mail) changed.add(mail)
        }
        if (changed.isEmpty()) return
        for (mail in changed) pendingLocal[mail.id] = mail.localFlags()
        val readAccounts = ArrayList<String>()
        for (mail in changed) {
            val prior = previousById[mail.id] ?: continue
            if (prior.unread && !mail.unread) readAccounts.add(mail.accountId)
        }
        viewModelScope.launch(io) {
            try {
                persistMails(changed)
                if (readAccounts.isNotEmpty()) pushSeen(readAccounts.distinct())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AinsmailSync", "persist_local ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun publish(previous: MailboxSession, restyleRows: Boolean = false) {
        val now = System.currentTimeMillis()
        val reuse = if (!restyleRows && rowsReusable(previous, session, rowMinute, now)) {
            ListReuse(list.entries, chrome.sectionMailIds, list.tagGroups)
        } else {
            null
        }
        rowMinute = now / 60_000L
        val projected = project(session, now, chrome.accounts, reuse, badgeStyle, weekDateStyle)
        if (projected.chrome != chrome) chrome = projected.chrome
        if (projected.list != list) list = projected.list
        val nextPlace = MailboxPlace(session.scope, session.section)
        val placeChanged = nextPlace != place
        if (placeChanged) place = nextPlace
        val nextSaved = if (showingSamples) emptyList() else session.accounts
        if (nextSaved != savedAccounts) savedAccounts = nextSaved
        if (session.reauthAccountIds != reauthAccountIds) reauthAccountIds = session.reauthAccountIds
        val contentChanged = previous.mails != session.mails ||
            previous.accounts != session.accounts ||
            previous.catalog != session.catalog
        if (contentChanged) contentEpoch++
        if (placeChanged && !showingSamples) restartWatch()
    }

    companion object {
        fun factory(
            accounts: List<Account>,
            mails: List<Mail>,
            catalog: TagCatalog,
            book: AccountBook,
            observeMails: () -> Flow<List<Mail>>,
            syncAccounts: suspend (List<String>) -> InitialSyncResult,
            watchScope: suspend (List<String>, Section, (MailboxSyncHint) -> Unit) -> Unit,
            pushSeen: suspend (List<String>) -> Unit,
            persistMails: suspend (List<Mail>) -> Unit,
            forgetAccounts: suspend (List<String>) -> Unit,
            bodyWash: BodyWash,
            display: DisplayPreferences,
            io: CoroutineDispatcher,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MailboxViewModel(
                        accounts,
                        mails,
                        catalog,
                        book,
                        observeMails,
                        syncAccounts,
                        watchScope,
                        pushSeen,
                        persistMails,
                        forgetAccounts,
                        bodyWash,
                        display,
                        io,
                    ) as T
                }
            }
        }
    }
}
