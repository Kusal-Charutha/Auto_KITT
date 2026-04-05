package com.example.autokitt.network

import com.google.gson.annotations.SerializedName

data class SensorDataPayload(
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("session_id") val sessionId: Long,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("date") val date: String,
    
    // Core
    @SerializedName("engine_rpm") val engineRpm: Double,
    @SerializedName("vehicle_speed") val vehicleSpeed: Double,
    @SerializedName("throttle") val throttle: Double,
    @SerializedName("engine_load") val engineLoad: Double,
    @SerializedName("coolant_temp") val coolantTemp: Double,
    
    // Expanded
    @SerializedName("run_time") val runTime: Double = -1.0,
    @SerializedName("map") val map: Double = -1.0,
    @SerializedName("timing") val timing: Double = -1.0,
    @SerializedName("intake_temp") val intakeTemp: Double = -1.0,
    @SerializedName("fuel_level") val fuelLevel: Double = -1.0,
    @SerializedName("baro") val baro: Double = -1.0,
    @SerializedName("voltage") val voltage: Double = -1.0,
    @SerializedName("equiv_ratio") val equivRatio: Double = -1.0,
    @SerializedName("rel_throttle") val relThrottle: Double = -1.0,
    @SerializedName("abs_throttle_b") val absThrottleB: Double = -1.0,
    @SerializedName("pedal_d") val pedalD: Double = -1.0,
    @SerializedName("pedal_e") val pedalE: Double = -1.0,
    @SerializedName("cmd_throttle") val cmdThrottle: Double = -1.0,
    @SerializedName("ltft1") val ltft1: Double = -1.0,
    @SerializedName("stft1") val stft1: Double = -1.0,
    @SerializedName("cat_b1s1") val catB1S1: Double = -1.0,
    @SerializedName("cat_b1s2") val catB1S2: Double = -1.0,
    @SerializedName("evap_purge") val evapPurge: Double = -1.0,
    @SerializedName("warmups") val warmups: Double = -1.0
)

data class AggregatedDataResponse(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("avg_speed") val avgSpeed: Double,
    @SerializedName("avg_rpm") val avgRpm: Double
)
