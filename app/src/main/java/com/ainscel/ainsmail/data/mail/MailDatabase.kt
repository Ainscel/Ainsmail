package com.ainscel.ainsmail.data.mail

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MailHeaderEntity::class, FolderSyncCursorEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class MailDatabase : RoomDatabase() {
    abstract fun mailDao(): MailDao

    companion object {
        @Volatile
        private var instance: MailDatabase? = null

        fun get(context: Context): MailDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MailDatabase::class.java,
                    "ainsmail-mail.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}

/**
 * 加上本机删除标记。旧行若把服务器夹改成了废件箱，改回主键里的夹名。
 * 账号 id 是 UUID，主键是 `账号:夹名:UID`。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE mail_headers ADD COLUMN locallyDeleted INTEGER NOT NULL DEFAULT 0",
        )
        val repairs = ArrayList<Pair<String, String>>()
        db.query("SELECT id, remoteFolder FROM mail_headers").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val folderIndex = cursor.getColumnIndexOrThrow("remoteFolder")
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val stored = cursor.getString(folderIndex)
                val serverFolder = serverFolderInMailId(id) ?: continue
                if (serverFolder != stored) repairs += id to serverFolder
            }
        }
        for ((id, serverFolder) in repairs) {
            db.execSQL(
                "UPDATE mail_headers SET remoteFolder = ?, locallyDeleted = 1 WHERE id = ?",
                arrayOf(serverFolder, id),
            )
        }
    }
}

/** 信封补上 Message-ID，并记下尚未回写的已读。旧行这两列都是空。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE mail_headers ADD COLUMN messageId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE mail_headers ADD COLUMN seenPending INTEGER NOT NULL DEFAULT 0")
    }
}
