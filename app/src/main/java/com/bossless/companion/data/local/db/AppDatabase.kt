package com.bossless.companion.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bossless.companion.data.local.db.JobDetailDao
import com.bossless.companion.data.local.db.JobDetailEntity
import com.bossless.companion.data.local.db.TimeEntryDao
import com.bossless.companion.data.local.db.TimeEntryEntity

@Database(
    entities = [
        JobEntity::class,
        NotificationEntity::class,
        JobDetailEntity::class,
        TimeEntryEntity::class,
        JobDocumentEntity::class,
        PendingUserLocationEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun notificationDao(): NotificationDao
    abstract fun jobDetailDao(): JobDetailDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun jobDocumentDao(): JobDocumentDao
    abstract fun pendingUserLocationDao(): PendingUserLocationDao
}
