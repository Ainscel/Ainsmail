package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.TagCatalog

/** 骨架阶段的邮箱。会话在内存里，旋转屏幕还在，进程被杀掉就回到当初传进来的那一份。 */
class MailboxViewModel(
    accounts: List<Account>,
    mails: List<Mail>,
    catalog: TagCatalog,
) : ViewModel() {
    private var session = MailboxSession(
        accounts = accounts,
        mails = mails,
        catalog = catalog,
    )
    private val initial = project(session, System.currentTimeMillis())

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
        if (session != previous) publish(previous)
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
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MailboxViewModel(accounts, mails, catalog) as T
                }
            }
        }
    }
}
