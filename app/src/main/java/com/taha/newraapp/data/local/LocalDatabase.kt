package com.taha.newraapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity

@Database(
    entities = [MessageEntity::class, ConversationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
