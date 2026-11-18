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
 * 写锁和提前标记放在伴生对象里，界面和后台 Worker 各建一个实例时仍共用。
 */
class MailLocalStore(
    context: Context,
    private val io: CoroutineDispatcher,
    private val db: MailDatabase = MailDatabase.get(context),
) {
    private val dao = db.mailDao()

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

    suspend fun needsInitialSync(accountId: String): Boolean = withContext(io) {
        accountNeedsInitialSync(dao.cursorsFor(accountId))
    }

    suspend fun upsertHeaders(rows: List<MailHeaderEntity>) = writes.withLock {
        withContext(io) {
            dao.upsertHeadersPreservingLocal(rows.map { applyEarly(it) })
        }
    }

    suspend fun replaceFolder(accountId: String, remoteFolder: String, rows: List<MailHeaderEntity>) =
        writes.withLock {
            withContext(io) {
                dao.replaceFolderHeaders(accountId, remoteFolder, rows.map { applyEarly(it) })
            }
        }

    suspend fun headersInFolder(accountId: String, remoteFolder: String): List<MailHeaderEntity> =
        withContext(io) { dao.headersInFolder(accountId, remoteFolder) }

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

    suspend fun pendingSeen(accountId: String): List<MailHeaderEntity> =
        withContext(io) { dao.pendingSeen(accountId) }

    suspend fun clearSeenPending(ids: List<String>) {
        if (ids.isEmpty()) return
        writes.withLock {
            withContext(io) { dao.clearSeenPending(ids) }
        }
    }

    /** 删掉该账号的信头和游标。调用方不要传空列表。 */
    suspend fun deleteAccounts(accountIds: List<String>) {
        if (accountIds.isEmpty()) return
        writes.withLock {
            withContext(io) { dao.deleteAccounts(accountIds) }
        }
    }

    /**
     * 本机已读、归档、删除写回库，不碰服务器，也不写类型和标签。
     * 未读从有到无时记下 [MailHeaderEntity.seenPending]。行还没插入时先留在锁里，插入时带上。
     */
    suspend fun persistLocalMails(mails: List<Mail>) = writes.withLock {
        withContext(io) {
            val writes = ArrayList<LocalMailWrite>(mails.size)
            for (mail in mails) {
                val existing = dao.findHeader(mail.id)
                val pending = seenPendingFor(existing, mail)
                if (existing == null) {
                    early[mail.id] = EarlyLocal(
                        unread = mail.unread,
                        archived = mail.archived,
                        locallyDeleted = mail.locallyDeleted,
                        seenPending = pending,
                    )
                } else {
                    early.remove(mail.id)
                    writes.add(
                        LocalMailWrite(
                            id = mail.id,
                            unread = mail.unread,
                            archived = mail.archived,
                            locallyDeleted = mail.locallyDeleted,
                            seenPending = pending,
                        ),
                    )
                }
            }
            if (writes.isNotEmpty()) dao.updateLocalFlags(writes)
        }
    }

    private fun applyEarly(row: MailHeaderEntity): MailHeaderEntity {
        val held = early.remove(row.id) ?: return row
        return row.copy(
            unread = held.unread,
            archived = held.archived,
            locallyDeleted = held.locallyDeleted,
            seenPending = held.seenPending,
        )
    }

    private companion object {
        const val TAG = "AinsmailSync"
        val writes = Mutex()
        val early = HashMap<String, EarlyLocal>()
    }
}

private data class EarlyLocal(
    val unread: Boolean,
    val archived: Boolean,
    val locallyDeleted: Boolean,
    val seenPending: Boolean,
)

private fun seenPendingFor(existing: MailHeaderEntity?, mail: Mail): Boolean = when {
    existing == null -> !mail.unread
    existing.unread && !mail.unread -> true
    !mail.unread && existing.seenPending -> true
    else -> false
}
