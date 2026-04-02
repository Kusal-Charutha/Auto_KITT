package com.example.autokitt.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val date: String,
    val engineRpm: Double,
    val vehicleSpeed: Double,
    val engineLoad: Double,
    val throttlePos: Double,
    val coolantTemp: Double,
    val intakeTemp: Double,
    val maf: Double,
    val fuelRate: Double,
    val engineRunTime: Double,
    
    // New Fields
    val longTermFuelTrim1: Double,
    val shortTermFuelTrim1: Double,
    val intakeManifoldPressure: Double,
    val fuelTankLevel: Double,
    val absoluteThrottleB: Double,
    val pedalD: Double,
    val pedalE: Double,
    val commandedThrottleActuator: Double,
    val fuelAirCommandedEquivRatio: Double,
    val absBarometricPressure: Double,
    val relativeThrottlePos: Double,
    val timingAdvance: Double,
    val catTempB1S1: Double,
    val catTempB1S2: Double,
    val controlModuleVoltage: Double,
    val commandedEvapPurge: Double
)
