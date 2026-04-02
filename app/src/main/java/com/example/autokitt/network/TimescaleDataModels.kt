package com.example.autokitt.network

import com.google.gson.annotations.SerializedName

data class SensorDataPayload(
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("session_id") val sessionId: Long,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("date") val date: String,
    @SerializedName("engine_rpm") val engineRpm: Double,
    @SerializedName("vehicle_speed") val vehicleSpeed: Double,
    @SerializedName("throttle") val throttle: Double,
    @SerializedName("engine_load") val engineLoad: Double,
    @SerializedName("coolant_temp") val coolantTemp: Double,
    @SerializedName("intake_temp") val intakeTemp: Double,
    @SerializedName("maf") val maf: Double,
    @SerializedName("fuel_rate") val fuelRate: Double,
    @SerializedName("run_time") val runTime: Double,
    @SerializedName("ltft1") val ltft1: Double,
    @SerializedName("stft1") val stft1: Double,
    @SerializedName("map") val map: Double,
    @SerializedName("fuel_level") val fuelLevel: Double,
    @SerializedName("abs_throttle_b") val absThrottleB: Double,
    @SerializedName("pedal_d") val pedalD: Double,
    @SerializedName("pedal_e") val pedalE: Double,
    @SerializedName("cmd_throttle") val cmdThrottle: Double,
    @SerializedName("equiv_ratio") val equivRatio: Double,
    @SerializedName("baro") val baro: Double,
    @SerializedName("rel_throttle") val relThrottle: Double,
    @SerializedName("timing") val timing: Double,
    @SerializedName("cat_b1s1") val catB1S1: Double,
    @SerializedName("cat_b1s2") val catB1S2: Double,
    @SerializedName("voltage") val voltage: Double,
    @SerializedName("evap_purge") val evapPurge: Double
)

data class AggregatedDataResponse(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("avg_speed") val avgSpeed: Double,
    @SerializedName("avg_rpm") val avgRpm: Double
)
