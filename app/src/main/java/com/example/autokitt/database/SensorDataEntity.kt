package com.example.autokitt.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val date: String,
    
    // Core 5
    val engineRpm: Double,
    val vehicleSpeed: Double,
    val engineLoad: Double,
    val throttlePos: Double, // absThrottle
    val coolantTemp: Double,
    
    // Expanded Diagnostic (Null default to 0.0 or -1.0)
    val runTime: Double = -1.0,
    val intakePressure: Double = -1.0, // MAP
    val timingAdvance: Double = -1.0,
    val intakeTemp: Double = -1.0,
    val fuelLevel: Double = -1.0,
    val baroPressure: Double = -1.0,
    val controlModuleVoltage: Double = -1.0,
    val equivRatio: Double = -1.0,
    val relativeThrottle: Double = -1.0,
    val ambientTemp: Double = -1.0,
    val absoluteThrottleB: Double = -1.0,
    val pedalD: Double = -1.0,
    val pedalE: Double = -1.0,
    val cmdThrottle: Double = -1.0,
    val ltft1: Double = -1.0,
    val stft1: Double = -1.0,
    val catTempB1S1: Double = -1.0,
    val catTempB1S2: Double = -1.0,
    val evapPurge: Double = -1.0,
    val warmups: Double = -1.0
)
