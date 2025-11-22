package com.pnm.mobileapp.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pnm.mobileapp.data.dao.PendingSlipDao
import com.pnm.mobileapp.data.model.Slip

@Database(
    entities = [Slip::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingSlipDao(): PendingSlipDao
}

