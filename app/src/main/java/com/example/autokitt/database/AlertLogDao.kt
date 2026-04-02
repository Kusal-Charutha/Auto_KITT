package com.example.autokitt.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AlertLogDao {
    @Insert
    suspend fun insert(alertLog: AlertLog)

    @Query("SELECT * FROM alert_logs WHERE alertType = :type ORDER BY timestamp DESC")
    suspend fun getAlertsByType(type: String): List<AlertLog>

    @Query("SELECT COUNT(*) FROM alert_logs WHERE alertType = 'BEHAVIOR' AND timestamp BETWEEN :start AND :end")
    suspend fun countBehaviorAlerts(start: Long, end: Long): Int

    @Query("DELETE FROM alert_logs WHERE alertType = :type")
    suspend fun deleteAlertsByType(type: String)

    @Query("SELECT * FROM alert_logs WHERE alertType IN ('BEHAVIOR', 'DRIVER_TIP') ORDER BY timestamp DESC")
    suspend fun getDriverAlerts(): List<AlertLog>

    @Query("DELETE FROM alert_logs WHERE alertType IN ('BEHAVIOR', 'DRIVER_TIP')")
    suspend fun deleteDriverAlerts()

    @Query("DELETE FROM alert_logs WHERE timestamp >= :startTime")
    suspend fun deleteAlertsSince(startTime: Long)
}
