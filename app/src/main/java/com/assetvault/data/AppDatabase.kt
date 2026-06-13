package com.assetvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.assetvault.data.chat.ChatDao
import com.assetvault.data.chat.ChatMessageEntity
import com.assetvault.data.chat.ChatSessionEntity

/**
 * AppDatabase - Module 3 & Chat
 * Room database storing URI, hex vector, pHash, blockchain status, and Chat history.
 */
@Database(
    entities = [SignatureEntity::class, ChatSessionEntity::class, ChatMessageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun signatureDao(): SignatureDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asset_vault_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}