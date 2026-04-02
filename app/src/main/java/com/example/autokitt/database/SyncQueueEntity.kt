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
    val engineRpm: Double,
    val vehicleSpeed: Double,
    val throttle: Double,
    val engineLoad: Double,
    val coolantTemp: Double,
    val intakeTemp: Double,
    val maf: Double,
    val fuelRate: Double,
    val runTime: Double,
    val ltft1: Double,
    val stft1: Double,
    val map: Double,
    val fuelLevel: Double,
    val absThrottleB: Double,
    val pedalD: Double,
    val pedalE: Double,
    val cmdThrottle: Double,
    val equivRatio: Double,
    val baro: Double,
    val relThrottle: Double,
    val timing: Double,
    val catB1S1: Double,
    val catB1S2: Double,
    val voltage: Double,
    val evapPurge: Double
)
