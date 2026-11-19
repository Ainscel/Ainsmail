package com.ainscel.ainsmail.data.mail

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** 本机回写只带这三列。类型和标签留给分类任务。 */
data class LocalMailWrite(
    val id: String,
    val unread: Boolean,
    val archived: Boolean,
    val locallyDeleted: Boolean,
    val seenPending: Boolean,
)

@Dao
interface MailDao {
    @Query("SELECT * FROM mail_headers ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<MailHeaderEntity>>

    @Query("SELECT COUNT(*) FROM mail_headers")
    suspend fun countAll(): Int

    @Query(
        """
        INSERT INTO mail_headers (
            id, accountId, remoteFolder, uid, uidValidity,
            fromName, fromAddress, toCsv, ccCsv, subject, timeMillis,
            unread, hasAttachment, archived, locallyDeleted, typeName, tagNamesCsv,
            messageId, seenPending
        ) VALUES (
            :id, :accountId, :remoteFolder, :uid, :uidValidity,
            :fromName, :fromAddress, :toCsv, :ccCsv, :subject, :timeMillis,
            :unread, :hasAttachment, :archived, :locallyDeleted, :typeName, :tagNamesCsv,
            :messageId, :seenPending
        )
        ON CONFLICT(id) DO UPDATE SET
            accountId = excluded.accountId,
            remoteFolder = excluded.remoteFolder,
            uid = excluded.uid,
            uidValidity = excluded.uidValidity,
            fromName = excluded.fromName,
            fromAddress = excluded.fromAddress,
            toCsv = excluded.toCsv,
            ccCsv = excluded.ccCsv,
            subject = excluded.subject,
            timeMillis = excluded.timeMillis,
            hasAttachment = excluded.hasAttachment,
            messageId = excluded.messageId
        """,
    )
    /** 只更新信封。正文路径、处理时间和预览留在原行，同步不能把已洗好的结果清掉。 */
    suspend fun upsertEnvelope(
        id: String,
        accountId: String,
        remoteFolder: String,
        uid: Long,
        uidValidity: Long,
        fromName: String,
        fromAddress: String,
        toCsv: String,
        ccCsv: String,
        subject: String,
        timeMillis: Long,
        unread: Boolean,
        hasAttachment: Boolean,
        archived: Boolean,
        locallyDeleted: Boolean,
        typeName: String?,
        tagNamesCsv: String,
        messageId: String,
        seenPending: Boolean,
    )

    @Transaction
    suspend fun upsertHeadersPreservingLocal(incoming: List<MailHeaderEntity>) {
        for (row in incoming) {
            upsertEnvelope(
                id = row.id,
                accountId = row.accountId,
                remoteFolder = row.remoteFolder,
                uid = row.uid,
                uidValidity = row.uidValidity,
                fromName = row.fromName,
                fromAddress = row.fromAddress,
                toCsv = row.toCsv,
                ccCsv = row.ccCsv,
                subject = row.subject,
                timeMillis = row.timeMillis,
                unread = row.unread,
                hasAttachment = row.hasAttachment,
                archived = row.archived,
                locallyDeleted = row.locallyDeleted,
                typeName = row.typeName,
                tagNamesCsv = row.tagNamesCsv,
                messageId = row.messageId,
                seenPending = row.seenPending,
            )
        }
    }

    @Query(
        """
        UPDATE mail_headers
        SET unread = :unread, archived = :archived, locallyDeleted = :locallyDeleted,
            seenPending = :seenPending
        WHERE id = :id
        """,
    )
    suspend fun updateOneLocalFlags(
        id: String,
        unread: Boolean,
        archived: Boolean,
        locallyDeleted: Boolean,
        seenPending: Boolean,
    )

    @Transaction
    suspend fun updateLocalFlags(rows: List<LocalMailWrite>) {
        for (row in rows) {
            updateOneLocalFlags(
                row.id,
                row.unread,
                row.archived,
                row.locallyDeleted,
                row.seenPending,
            )
        }
    }

    @Query(
        """
        SELECT * FROM folder_sync_cursors
        WHERE accountId = :accountId AND remoteFolder = :remoteFolder
        LIMIT 1
        """,
    )
    suspend fun cursor(accountId: String, remoteFolder: String): FolderSyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: FolderSyncCursorEntity)

    /** 只标完成，不改最高 UID。没拉到的夹因此不会被当成还要补 30 天。 */
    @Query(
        """
        UPDATE folder_sync_cursors
        SET historySyncDone = 1
        WHERE accountId IN (:accountIds) AND historySyncDone = 0
        """,
    )
    suspend fun markHistoryDone(accountIds: List<String>)

    @Query("SELECT * FROM mail_headers WHERE id = :id LIMIT 1")
    suspend fun findHeader(id: String): MailHeaderEntity?

    @Query(
        """
        SELECT * FROM mail_headers
        WHERE accountId = :accountId AND remoteFolder = :remoteFolder
        """,
    )
    suspend fun headersInFolder(accountId: String, remoteFolder: String): List<MailHeaderEntity>

    @Query(
        """
        DELETE FROM mail_headers
        WHERE accountId = :accountId AND remoteFolder = :remoteFolder
        """,
    )
    suspend fun deleteFolderHeaders(accountId: String, remoteFolder: String)

    @Transaction
    suspend fun replaceFolderHeaders(
        accountId: String,
        remoteFolder: String,
        rows: List<MailHeaderEntity>,
    ) {
        deleteFolderHeaders(accountId, remoteFolder)
        upsertHeadersPreservingLocal(rows)
    }

    @Query(
        """
        SELECT * FROM mail_headers
        WHERE accountId = :accountId AND seenPending = 1
        """,
    )
    suspend fun pendingSeen(accountId: String): List<MailHeaderEntity>

    @Query("UPDATE mail_headers SET seenPending = 0 WHERE id IN (:ids)")
    suspend fun clearSeenPending(ids: List<String>)

    @Query("SELECT * FROM folder_sync_cursors WHERE accountId = :accountId")
    suspend fun cursorsFor(accountId: String): List<FolderSyncCursorEntity>

    @Query("DELETE FROM mail_headers WHERE accountId IN (:accountIds)")
    suspend fun deleteHeaders(accountIds: List<String>)

    @Query(
        """
        UPDATE mail_headers
        SET bodyRelativePath = :relativePath, bodyProcessedAt = :processedAt, preview = :preview
        WHERE id = :id
        """,
    )
    suspend fun markBodyProcessed(id: String, relativePath: String, processedAt: Long, preview: String)

    @Query(
        """
        SELECT id FROM mail_headers
        WHERE bodyProcessedAt IS NULL
        ORDER BY timeMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun unprocessedIds(limit: Int): List<String>

    @Query("DELETE FROM folder_sync_cursors WHERE accountId IN (:accountIds)")
    suspend fun deleteCursors(accountIds: List<String>)

    @Transaction
    suspend fun deleteAccounts(accountIds: List<String>) {
        deleteHeaders(accountIds)
        deleteCursors(accountIds)
    }
}
