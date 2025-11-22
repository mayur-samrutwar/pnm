package com.pnm.mobileapp.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pnm.mobileapp.data.dao.PendingSlipDao
import com.pnm.mobileapp.data.model.Slip

@Database(
    entities = [Slip::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingSlipDao(): PendingSlipDao
    
    companion object {
        /**
         * Migration from version 2 to 3: Add ethAddress column to pending_slips table
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_slips ADD COLUMN ethAddress TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

