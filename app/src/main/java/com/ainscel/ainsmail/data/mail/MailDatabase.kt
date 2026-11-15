package com.ainscel.ainsmail.data.mail

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MailHeaderEntity::class, FolderSyncCursorEntity::class],
    version = 1,
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
                ).build().also { instance = it }
            }
        }
    }
}
