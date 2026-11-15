package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.getValue
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

    var state by mutableStateOf(project(session, System.currentTimeMillis()))
        private set

    fun onIntent(intent: MailboxIntent): AddTagResult? {
        val outcome = reduce(session, intent, System.nanoTime())
        session = outcome.session
        state = project(session, System.currentTimeMillis())
        return outcome.tagResult
    }

    /** 调用方声明为 `() -> Unit` 时用这个，变更仍然只走 [onIntent]。 */
    fun perform(intent: MailboxIntent) {
        onIntent(intent)
    }

    fun detail(id: String): MailDetailUi? = session.detailOf(id, System.currentTimeMillis())

    fun subjectOf(id: String): String? = session.subjectOf(id)

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
