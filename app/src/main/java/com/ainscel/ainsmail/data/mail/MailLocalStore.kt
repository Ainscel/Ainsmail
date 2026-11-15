package com.ainscel.ainsmail.data.mail

import android.content.Context
import com.ainscel.ainsmail.model.Mail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    fun observeMails(): Flow<List<Mail>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toMail() } }
            .flowOn(io)

    suspend fun countAll(): Int = withContext(io) { dao.countAll() }

    suspend fun upsertHeaders(rows: List<MailHeaderEntity>) = withContext(io) {
        dao.upsertHeadersPreservingLocal(rows)
    }

    suspend fun cursor(accountId: String, remoteFolder: String): FolderSyncCursorEntity? =
        withContext(io) { dao.cursor(accountId, remoteFolder) }

    suspend fun upsertCursor(cursor: FolderSyncCursorEntity) = withContext(io) {
        dao.upsertCursor(cursor)
    }

    /** 本机意图（已读 / 归档 / 删除挪夹 / 标签）写回库，不碰服务器。 */
    suspend fun persistLocalMails(mails: List<Mail>) = withContext(io) {
        mails.forEach { mail ->
            dao.updateLocalFlags(
                id = mail.id,
                unread = mail.unread,
                archived = mail.archived,
                remoteFolder = mail.remoteFolder.name,
                typeName = mail.type?.name,
                tagNamesCsv = mail.tagNames.joinToString("\u001f"),
            )
        }
    }
}
