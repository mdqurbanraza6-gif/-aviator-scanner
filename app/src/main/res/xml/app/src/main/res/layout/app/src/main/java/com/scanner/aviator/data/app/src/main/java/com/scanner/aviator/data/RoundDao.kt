package com.scanner.aviator.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RoundDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(round: RoundEntity)

    @Query("SELECT * FROM rounds ORDER BY timestamp DESC LIMIT 5000")
    fun getAllRoundsLive(): LiveData<List<RoundEntity>>

    @Query("SELECT * FROM rounds ORDER BY timestamp DESC LIMIT 5000")
    suspend fun getAllRoundsList(): List<RoundEntity>

    @Query("SELECT * FROM rounds ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentRounds(): List<RoundEntity>

    @Query("DELETE FROM rounds WHERE id NOT IN (SELECT id FROM rounds ORDER BY timestamp DESC LIMIT 5000)")
    suspend fun pruneOldRecords()

    @Query("DELETE FROM rounds")
    suspend fun clearAll()
}
