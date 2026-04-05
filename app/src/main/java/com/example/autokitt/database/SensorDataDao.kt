package com.example.autokitt.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDataDao {
    @Insert
    suspend fun insert(data: SensorData)

    @Query("SELECT * FROM sensor_data WHERE sessionId = :sessionId")
    suspend fun getSessionData(sessionId: Long): List<SensorData>

    @Query("SELECT * FROM sensor_data WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getDataBetween(start: Long, end: Long): List<SensorData>

    @Query("DELETE FROM sensor_data")
    suspend fun clearAll()

    @Query("DELETE FROM sensor_data WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)
    
    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm > 1000 AND timestamp BETWEEN :start AND :end")
    suspend fun countActiveDrivingTicks(start: Long, end: Long): Int

    @Query("SELECT MAX(sessionId) FROM sensor_data")
    suspend fun getLastSessionId(): Long?

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm > 1000 AND sessionId = :sessionId")
    suspend fun countActiveDrivingTicksForSession(sessionId: Long): Int
    
    @Query("SELECT MIN(timestamp) FROM sensor_data WHERE sessionId = :sessionId")
    suspend fun getSessionStartTime(sessionId: Long): Long?

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm > 950 AND timestamp BETWEEN :start AND :end")
    suspend fun countDrivingTime(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm <= 950 AND timestamp BETWEEN :start AND :end")
    suspend fun countIdleTime(start: Long, end: Long): Int

    @Query("SELECT MAX(vehicleSpeed) FROM sensor_data WHERE timestamp BETWEEN :start AND :end")
    suspend fun getMaxSpeed(start: Long, end: Long): Float?

    @Query("SELECT AVG(vehicleSpeed) FROM sensor_data WHERE timestamp BETWEEN :start AND :end AND vehicleSpeed > 0")
    suspend fun getAvgSpeed(start: Long, end: Long): Float?

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm > 950 AND sessionId = :sessionId")
    suspend fun countDrivingTimeForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm <= 950 AND sessionId = :sessionId")
    suspend fun countIdleTimeForSession(sessionId: Long): Int

    @Query("SELECT MAX(vehicleSpeed) FROM sensor_data WHERE sessionId = :sessionId")
    suspend fun getMaxSpeedForSession(sessionId: Long): Float?

    @Query("SELECT AVG(vehicleSpeed) FROM sensor_data WHERE sessionId = :sessionId AND vehicleSpeed > 0")
    suspend fun getAvgSpeedForSession(sessionId: Long): Float?

    @Query("SELECT SUM(max_ts - min_ts) FROM (SELECT MAX(timestamp) as max_ts, MIN(timestamp) as min_ts FROM sensor_data WHERE timestamp BETWEEN :start AND :end GROUP BY sessionId)")
    suspend fun getTotalConnectedTime(start: Long, end: Long): Long?

    @Query("SELECT MAX(timestamp) - MIN(timestamp) FROM sensor_data WHERE sessionId = :sessionId")
    suspend fun getTotalConnectedTimeForSession(sessionId: Long): Long?

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm > 0 AND timestamp BETWEEN :start AND :end")
    suspend fun countTotalDetections(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM sensor_data WHERE engineRpm > 0 AND sessionId = :sessionId")
    suspend fun countTotalDetectionsForSession(sessionId: Long): Int
}
