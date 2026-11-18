package com.ainscel.ainsmail.data.mail

import android.content.Context
import android.util.Log
import com.ainscel.ainsmail.model.Mail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 邮件头本地 SSOT。外部数据先入库，上层只订这个 Flow。
 * 不做空 Repository 接口；同步层直接依赖本类与 [MailDao]。
 */
class MailLocalStore(
    context: Context,
    private val io: CoroutineDispatcher,
    private val db: MailDatabase = MailDatabase.get(context),
) {
    private val dao = db.mailDao()
    private val writes = Mutex()

    fun observeMails(): Flow<List<Mail>> =
        dao.observeAll()
            .map { rows ->
                val mails = ArrayList<Mail>(rows.size)
                for (row in rows) {
                    val mail = row.toMail()
                    if (mail == null) {
                        Log.w(TAG, "skip_header id=${row.id} folder=${row.remoteFolder}")
                    } else {
                        mails.add(mail)
                    }
                }
                mails
            }
            .flowOn(io)

    suspend fun countAll(): Int = withContext(io) { dao.countAll() }

    suspend fun upsertHeaders(rows: List<MailHeaderEntity>) = writes.withLock {
        withContext(io) { dao.upsertHeadersPreservingLocal(rows) }
    }

    suspend fun cursor(accountId: String, remoteFolder: String): FolderSyncCursorEntity? =
        withContext(io) { dao.cursor(accountId, remoteFolder) }

    suspend fun upsertCursor(cursor: FolderSyncCursorEntity) = writes.withLock {
        withContext(io) { dao.upsertCursor(cursor) }
    }

    suspend fun markHistoryDone(accountIds: List<String>) {
        if (accountIds.isEmpty()) return
        writes.withLock {
            withContext(io) { dao.markHistoryDone(accountIds) }
        }
    }

    /** 删掉该账号的信头和游标。调用方不要传空列表。 */
    suspend fun deleteAccounts(accountIds: List<String>) {
        if (accountIds.isEmpty()) return
        writes.withLock {
            withContext(io) { dao.deleteAccounts(accountIds) }
        }
    }

    /** 本机已读、归档、删除写回库，不碰服务器，也不写类型和标签。 */
    suspend fun persistLocalMails(mails: List<Mail>) = writes.withLock {
        withContext(io) {
            dao.updateLocalFlags(
                mails.map { mail ->
                    LocalMailWrite(
                        id = mail.id,
                        unread = mail.unread,
                        archived = mail.archived,
                        locallyDeleted = mail.locallyDeleted,
                    )
                },
            )
        }
    }

    private companion object {
        const val TAG = "AinsmailSync"
    }
}
