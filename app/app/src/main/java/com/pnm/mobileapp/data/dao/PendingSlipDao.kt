package com.pnm.mobileapp.data.dao

import androidx.room.*
import com.pnm.mobileapp.data.model.Slip
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSlipDao {
    @Query("SELECT * FROM pending_slips ORDER BY timestamp DESC")
    fun getAllSlips(): Flow<List<Slip>>

    @Query("SELECT * FROM pending_slips WHERE id = :id")
    suspend fun getSlipById(id: Long): Slip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlip(slip: Slip): Long

    @Update
    suspend fun updateSlip(slip: Slip)

    @Delete
    suspend fun deleteSlip(slip: Slip)

    @Query("DELETE FROM pending_slips")
    suspend fun deleteAll()
}

