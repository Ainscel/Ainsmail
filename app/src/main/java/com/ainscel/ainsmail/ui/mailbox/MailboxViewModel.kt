package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ainscel.ainsmail.data.mail.MailFirstSync
import com.ainscel.ainsmail.data.mail.MailLocalStore
import com.ainscel.ainsmail.data.mail.FirstSyncOutcome
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.TagCatalog
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.AccountDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 没有保存过账号时用传入的样例。
 * 一旦加密文件里有账号，会话改成这些账号，邮件改订本地库 Flow，并触发首拉。
 */
class MailboxViewModel(
    private val sampleAccounts: List<Account>,
    private val sampleMails: List<Mail>,
    catalog: TagCatalog,
    private val book: AccountBook,
    private val mailStore: MailLocalStore,
    private val firstSync: MailFirstSync,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private var showingSamples = true
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

    /** 正文只订阅信件本身的变化，筛选和多选不带动正文重组。 */
    private var contentEpoch by mutableIntStateOf(0)

    fun onIntent(intent: MailboxIntent): AddTagResult? {
        val outcome = reduce(session, intent, System.nanoTime())
        val previous = session
        session = outcome.session
        if (session != previous) {
            publish(previous)
            persistIfNeeded(previous, session)
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
                stopRealMailPipeline()
                onIntent(MailboxIntent.RestoreSamples(sampleAccounts, sampleMails))
            }
        } else if (showingSamples) {
            showingSamples = false
            onIntent(MailboxIntent.UseRealAccounts(saved))
            startRealMailPipeline(saved.map { it.id })
        } else {
            onIntent(MailboxIntent.ReplaceAccounts(saved))
            startRealMailPipeline(saved.map { it.id })
        }
    }

    private fun startRealMailPipeline(accountIds: List<String>) {
        if (observeJob == null) {
            observeJob = viewModelScope.launch {
                mailStore.observeMails().collectLatest { mails ->
                    onIntent(MailboxIntent.ReplaceMails(mails))
                }
            }
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val outcomes = firstSync.syncAll(accountIds)
            outcomes.forEach { outcome ->
                when (outcome) {
                    FirstSyncOutcome.Ok -> Unit
                    is FirstSyncOutcome.NeedsReauth ->
                        onIntent(MailboxIntent.ReportNeedsReauth(outcome.accountId))
                    is FirstSyncOutcome.Failed ->
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

    private fun persistIfNeeded(previous: MailboxSession, next: MailboxSession) {
        if (showingSamples || previous.mails === next.mails || previous.mails == next.mails) return
        val changed = next.mails.filter { mail ->
            previous.mails.find { it.id == mail.id } != mail
        }
        if (changed.isEmpty()) return
        viewModelScope.launch(io) {
            mailStore.persistLocalMails(changed)
        }
    }

    private fun publish(previous: MailboxSession) {
        val projected = project(session, System.currentTimeMillis(), chrome.accounts)
        if (projected.chrome != chrome) chrome = projected.chrome
        if (projected.list != list) list = projected.list
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
            mailStore: MailLocalStore,
            firstSync: MailFirstSync,
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
                        mailStore,
                        firstSync,
                        io,
                    ) as T
                }
            }
        }
    }
}
