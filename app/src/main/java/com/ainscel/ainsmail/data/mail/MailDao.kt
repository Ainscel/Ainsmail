package com.ainscel.ainsmail.data.mail

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {
    @Query("SELECT * FROM mail_headers ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<MailHeaderEntity>>

    @Query("SELECT COUNT(*) FROM mail_headers")
    suspend fun countAll(): Int

    @Query("SELECT * FROM mail_headers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): MailHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(rows: List<MailHeaderEntity>): List<Long>

    @Update
    suspend fun update(rows: List<MailHeaderEntity>)

    @Query(
        """
        UPDATE mail_headers
        SET unread = :unread, archived = :archived, remoteFolder = :remoteFolder,
            typeName = :typeName, tagNamesCsv = :tagNamesCsv
        WHERE id = :id
        """,
    )
    suspend fun updateLocalFlags(
        id: String,
        unread: Boolean,
        archived: Boolean,
        remoteFolder: String,
        typeName: String?,
        tagNamesCsv: String,
    )

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

    @Transaction
    suspend fun upsertHeadersPreservingLocal(incoming: List<MailHeaderEntity>) {
        if (incoming.isEmpty()) return
        val inserted = insertIgnore(incoming)
        val toUpdate = ArrayList<MailHeaderEntity>()
        incoming.forEachIndexed { index, row ->
            if (inserted[index] == -1L) {
                val existing = findById(row.id) ?: return@forEachIndexed
                // 同步失败不得抹本地归档；已读本阶段以库内为准，新拉只补信封字段
                toUpdate += row.copy(
                    unread = existing.unread,
                    archived = existing.archived,
                    typeName = existing.typeName,
                    tagNamesCsv = existing.tagNamesCsv,
                )
            }
        }
        if (toUpdate.isNotEmpty()) update(toUpdate)
    }
}
