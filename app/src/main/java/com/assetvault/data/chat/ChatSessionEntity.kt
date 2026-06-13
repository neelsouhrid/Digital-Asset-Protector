package com.assetvault.data.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val title: String,
    val lastUpdatedTimestamp: Long
)
