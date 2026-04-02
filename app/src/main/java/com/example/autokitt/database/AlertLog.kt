package com.example.autokitt.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_logs")
data class AlertLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val alertType: String, // "FAULT" or "BEHAVIOR"
    val explanationText: String,
    val jsonPayload: String = ""
)
