package com.ainscel.ainsmail.ui.mailbox

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.TagCatalog
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.AccountDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 首拉结果里界面需要知道的部分。凭证和异常原文留在数据层。 */
sealed interface MailboxSyncHint {
    data class NeedsReauth(val accountId: String) : MailboxSyncHint
    data object Failed : MailboxSyncHint
}

/**
 * 没有保存过账号时用传入的样例。
 * 一旦加密文件里有账号，会话改成这些账号，邮件改订本地库 Flow，并触发首拉。
 * 本类不引用数据库或 IMAP 类型；观察、同步、回写由组合根传入。
 */
class MailboxViewModel(
    private val sampleAccounts: List<Account>,
    private val sampleMails: List<Mail>,
    catalog: TagCatalog,
    private val book: AccountBook,
    private val observeMails: () -> Flow<List<Mail>>,
    private val syncAccounts: suspend (List<String>) -> List<MailboxSyncHint>,
    private val persistMails: suspend (List<Mail>) -> Unit,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private var showingSamples = true
    private var syncedAccountIds: Set<String> = emptySet()
    /** 已写库、库快照还没跟上的本机修改。避免 Flow 旧值把归档/已读冲掉。 */
    private val pendingLocal = LinkedHashMap<String, Mail>()
    private var session = MailboxSession(
        accounts = sampleAccounts,
        mails = sampleMails,
        catalog = catalog,
    )
    private val initial = project(session, System.currentTimeMillis())
    private var observeJob: Job? = null
    private var syncJob: Job? = null

    init {
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

    /**
     * 在组合里调用。读取 [contentEpoch] 才会在信件变化后重组。
     * 打开哪封信由导航传入，不写入会话。
     */
    fun detail(id: String): MailDetailUi? {
        contentEpoch
        return session.detailOf(id, System.currentTimeMillis())
    }

    /** 在组合里调用。和 [detail] 用同一次订阅。 */
    fun subjectOf(id: String): String? {
        contentEpoch
        return session.subjectOf(id)
    }

    private fun applySaved(saved: List<Account>) {
        if (saved.isEmpty()) {
            if (!showingSamples) {
                showingSamples = true
                syncedAccountIds = emptySet()
                pendingLocal.clear()
                stopRealMailPipeline()
                onIntent(MailboxIntent.RestoreSamples(sampleAccounts, sampleMails))
            }
        } else if (showingSamples) {
            showingSamples = false
            pendingLocal.clear()
            onIntent(MailboxIntent.UseRealAccounts(saved))
            startRealMailPipeline(saved.map { it.id })
        } else {
            onIntent(MailboxIntent.ReplaceAccounts(saved))
            val ids = saved.map { it.id }
            if (ids.toSet() != syncedAccountIds) startRealMailPipeline(ids)
        }
    }

    private fun startRealMailPipeline(accountIds: List<String>) {
        syncedAccountIds = accountIds.toSet()
        if (observeJob == null) {
            observeJob = viewModelScope.launch {
                observeMails().collectLatest { mails ->
                    onIntent(MailboxIntent.ReplaceMails(mergePending(pendingLocal, mails)))
                }
            }
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            syncAccounts(accountIds).forEach { hint ->
                when (hint) {
                    is MailboxSyncHint.NeedsReauth ->
                        onIntent(MailboxIntent.ReportNeedsReauth(hint.accountId))
                    MailboxSyncHint.Failed ->
                        onIntent(MailboxIntent.ReportSyncFailed)
                }
            }
        }
    }

    private fun stopRealMailPipeline() {
        observeJob?.cancel()
        observeJob = null
        syncJob?.cancel()
        syncJob = null
    }

    private fun persistLocal(previous: MailboxSession, next: MailboxSession) {
        if (showingSamples) return
        val changed = next.mails.filter { mail -> previous.mails.find { it.id == mail.id } != mail }
        if (changed.isEmpty()) return
        changed.forEach { pendingLocal[it.id] = it }
        val snapshot = changed.toList()
        viewModelScope.launch(io) {
            try {
                persistMails(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AinsmailSync", "persist_local ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun publish(previous: MailboxSession) {
        val projected = project(session, System.currentTimeMillis(), chrome.accounts)
        if (projected.chrome != chrome) chrome = projected.chrome
        if (projected.list != list) list = projected.list
        val nextSaved = if (showingSamples) emptyList() else session.accounts
        if (nextSaved != savedAccounts) savedAccounts = nextSaved
        if (session.reauthAccountIds != reauthAccountIds) reauthAccountIds = session.reauthAccountIds
        val contentChanged = previous.mails != session.mails ||
            previous.accounts != session.accounts ||
            previous.catalog != session.catalog
        if (contentChanged) contentEpoch++
    }

    companion object {
        fun factory(
            accounts: List<Account>,
            mails: List<Mail>,
            catalog: TagCatalog,
            book: AccountBook,
            observeMails: () -> Flow<List<Mail>>,
            syncAccounts: suspend (List<String>) -> List<MailboxSyncHint>,
            persistMails: suspend (List<Mail>) -> Unit,
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
                        persistMails,
                        io,
                    ) as T
                }
            }
        }
    }
}
