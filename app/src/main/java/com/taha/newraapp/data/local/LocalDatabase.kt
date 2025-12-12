package com.taha.newraapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.dao.PendingUploadDao
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.data.local.entities.PendingUploadEntity

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, PendingUploadEntity::class],
    version = 4,  // Bumped: added attachment fields to MessageEntity, added PendingUploadEntity
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun pendingUploadDao(): PendingUploadDao
}
