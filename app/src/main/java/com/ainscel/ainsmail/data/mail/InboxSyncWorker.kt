package com.ainscel.ainsmail.data.mail

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.AccountDirectory
import com.ainscel.ainsmail.security.SecretStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers

private const val INBOX_SYNC_WORK = "ainsmail-inbox-incremental"

/** 进程不在前台时，每 15 分钟只补已有游标的收件箱。不弹通知。 */
class InboxSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (MailSyncGate.foreground) return Result.success()
        val secrets = SecretStore(applicationContext)
        val book = AccountBook(applicationContext, secrets)
        book.load()
        val ready = book.directory.value as? AccountDirectory.Ready ?: return Result.success()
        if (ready.accounts.isEmpty()) return Result.success()
        val store = MailLocalStore(applicationContext, Dispatchers.IO)
        val live = MailLiveSync(book, store, Dispatchers.IO)
        live.syncInboxes(ready.accounts.map { it.id })
        return Result.success()
    }
}

fun scheduleInboxSync(context: Context) {
    val request = PeriodicWorkRequestBuilder<InboxSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        INBOX_SYNC_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
