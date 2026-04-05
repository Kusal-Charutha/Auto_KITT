package com.example.autokitt.ml

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DriverBehaviorAnalyzer(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var means = FloatArray(0)
    private var scales = FloatArray(0)
    private var featureNames = listOf<String>()
    
    init {
        try {
            val options = Interpreter.Options()
            interpreter = Interpreter(loadModelFile(), options)
            loadScaler()
            loadFeatureColumns()
        } catch (e: Exception) {
            Log.e("AutoKITT_ML", "Failed to initialize DriverBehaviorAnalyzer: ${e.message}")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("models/driver_behavior_alert_model/driver_behavior_alert_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun loadScaler() {
        val jsonStr = context.assets.open("models/driver_behavior_alert_model/scaler.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val meanArray = jsonObject.getJSONArray("mean")
        val scaleArray = jsonObject.getJSONArray("scale")
        
        means = FloatArray(meanArray.length()) { i -> meanArray.getDouble(i).toFloat() }
        scales = FloatArray(scaleArray.length()) { i -> scaleArray.getDouble(i).toFloat() }
    }

    private fun loadFeatureColumns() {
        val jsonStr = context.assets.open("models/driver_behavior_alert_model/feature_columns.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        featureNames = list
    }

    fun analyze(sensorData: Map<String, Double>): BehaviorResult? {
        if (interpreter == null || means.isEmpty() || featureNames.isEmpty()) return null
        
        // 1. Map raw input to features
        val rawFeatures = FloatArray(13)
        for (i in featureNames.indices) {
            val feat = featureNames[i]
            val value = when (feat) {
                "engine_load" -> sensorData["calcLoad"] ?: 0.0
                "engine_coolant_temp" -> sensorData["coolantTemp"] ?: 0.0
                "intake_manifold_pressure" -> sensorData["map"] ?: 0.0
                "engine_rpm" -> sensorData["rpm"] ?: 0.0
                "vehicle_speed" -> sensorData["speed"] ?: 0.0
                "ignition_timing_advance" -> sensorData["timingAdvance"] ?: 0.0
                "throttle_position" -> sensorData["absThrottle"] ?: 0.0
                "relative_throttle_position" -> sensorData["relThrottle"] ?: 0.0
                "accelerator_pedal_d" -> sensorData["pedalD"] ?: 0.0
                "accelerator_pedal_e" -> sensorData["pedalE"] ?: 0.0
                "throttle_actuator_control" -> sensorData["throttleCmd"] ?: 0.0
                "steering_angle" -> 0.0 // OBD missing it usually
                "steering_angular_velocity" -> 0.0 // OBD missing it usually
                else -> 0.0
            }
            rawFeatures[i] = value.toFloat()
        }

        // 2. Scale
        val inputBuffer = ByteBuffer.allocateDirect(13 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until 13) {
            val scaled = (rawFeatures[i] - means[i]) / scales[i]
            inputBuffer.putFloat(scaled)
        }

        // 3. Inference
        val outputBuffer = ByteBuffer.allocateDirect(1 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        interpreter?.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val probability = outputBuffer.float
        
        val isAggressive = probability > 0.75f
        val rpm = sensorData["rpm"] ?: 0.0
        val reasonsList = mutableListOf<String>()
        var explanation = if (isAggressive) "Aggressive Driving pattern detected." else "Driving pattern is normal."

        if (isAggressive) {
            reasonsList.add("High dynamic variance based on sensors")
            if (rpm > 4000) {
                reasonsList.add("High RPM Engine Stress")
                explanation = "Aggressive Driving: Engine stress detected at ${rpm.toInt()} RPM"
            }
        }
        
        return BehaviorResult(
            isAggressive = isAggressive,
            probability = probability,
            reasons = reasonsList,
            explanationText = explanation,
            jsonPayload = "{ \"prob\": $probability, \"rpm\": $rpm }"
        )
    }

    fun close() {
        interpreter?.close()
    }
}
