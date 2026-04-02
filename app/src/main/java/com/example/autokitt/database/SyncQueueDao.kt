package com.example.autokitt.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntry)

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 50): List<SyncQueueEntry>

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingCount(): Int
}
