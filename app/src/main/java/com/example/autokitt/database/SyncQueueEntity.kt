package com.example.autokitt.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val userName: String,
    val deviceId: String,
    val sessionId: Long,
    val timestamp: Long,
    val date: String,
    
    // Core
    val engineRpm: Double,
    val vehicleSpeed: Double,
    val engineLoad: Double,
    val throttle: Double,
    val coolantTemp: Double,
    
    // Expanded
    val runTime: Double = -1.0,
    val map: Double = -1.0,
    val timing: Double = -1.0,
    val intakeTemp: Double = -1.0,
    val fuelLevel: Double = -1.0,
    val baro: Double = -1.0,
    val voltage: Double = -1.0,
    val equivRatio: Double = -1.0,
    val relThrottle: Double = -1.0,
    val absThrottleB: Double = -1.0,
    val pedalD: Double = -1.0,
    val pedalE: Double = -1.0,
    val cmdThrottle: Double = -1.0,
    val ltft1: Double = -1.0,
    val stft1: Double = -1.0,
    val catB1S1: Double = -1.0,
    val catB1S2: Double = -1.0,
    val evapPurge: Double = -1.0,
    val warmups: Double = -1.0
)
