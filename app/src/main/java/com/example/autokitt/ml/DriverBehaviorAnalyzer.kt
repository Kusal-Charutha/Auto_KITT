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

    // Data-driven reason configuration loaded from model assets
    private var reasonThresholds = mutableMapOf<String, Double>()   // e.g. "rpm_high" -> 3000.0
    private var reasonFeatureMap = mutableMapOf<String, String>()   // e.g. "rpm" -> "engine_rpm"
    
    init {
        try {
            val options = Interpreter.Options()
            interpreter = Interpreter(loadModelFile(), options)
            loadScaler()
            loadFeatureColumns()
            loadReasonThresholds()
            loadReasonFeatureMap()
            Log.i("AutoKITT_ML", "DriverBehaviorAnalyzer initialized with ${reasonThresholds.size} threshold entries")
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

    private fun loadReasonThresholds() {
        val jsonStr = context.assets.open("models/driver_behavior_alert_model/reason_thresholds.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!jsonObject.isNull(key)) {
                reasonThresholds[key] = jsonObject.getDouble(key)
            }
        }
    }

    private fun loadReasonFeatureMap() {
        val jsonStr = context.assets.open("models/driver_behavior_alert_model/reason_feature_map.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!jsonObject.isNull(key)) {
                reasonFeatureMap[key] = jsonObject.getString(key)
            }
        }
    }

    // Convert a threshold key like "rpm_high" to a human-readable label like "High RPM"
    private fun formatReasonLabel(thresholdKey: String): String {
        // Strip the "_high" / "_low" suffix to get the base name
        val baseName = thresholdKey.replace("_high", "").replace("_low", "")
        val qualifier = when {
            thresholdKey.endsWith("_high") -> "High"
            thresholdKey.endsWith("_low") -> "Low"
            else -> ""
        }
        val readableName = baseName.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return "$qualifier $readableName".trim()
    }

    fun analyze(sensorData: Map<String, Double>): BehaviorResult? {
        if (interpreter == null || means.isEmpty() || featureNames.isEmpty()) return null
        
        // 1. Map raw input to features
        val numFeatures = featureNames.size
        val rawFeatures = FloatArray(numFeatures)
        val rawValuesByFeatureName = mutableMapOf<String, Double>()

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
            rawValuesByFeatureName[feat] = value
        }

        // 2. Scale
        val inputBuffer = ByteBuffer.allocateDirect(numFeatures * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until numFeatures) {
            val scaled = if (scales[i] != 0f) (rawFeatures[i] - means[i]) / scales[i] else 0f
            inputBuffer.putFloat(scaled)
        }

        // 3. Inference
        val outputBuffer = ByteBuffer.allocateDirect(1 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        interpreter?.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val probability = outputBuffer.float
        
        val isAggressive = (probability == 1.0f)
        val reasonsList = mutableListOf<String>()
        var explanation = if (isAggressive) "Aggressive Driving pattern detected." else "Driving pattern is normal."

        if (isAggressive) {
            // Data-driven threshold checks from reason_thresholds.json
            for ((thresholdKey, thresholdValue) in reasonThresholds) {
                // e.g. thresholdKey = "rpm_high", strip suffix to get base key "rpm"
                val baseKey = thresholdKey.replace("_high", "").replace("_low", "")
                // Look up the feature column name from reason_feature_map.json
                val featureName = reasonFeatureMap[baseKey] ?: continue
                val currentValue = rawValuesByFeatureName[featureName] ?: continue

                val exceeded = when {
                    thresholdKey.endsWith("_high") -> currentValue > thresholdValue
                    thresholdKey.endsWith("_low") -> currentValue < thresholdValue
                    else -> false
                }

                if (exceeded) {
                    val label = formatReasonLabel(thresholdKey)
                    reasonsList.add(label)
                }
            }

            // Fallback: if no specific threshold was exceeded, add a generic reason
            if (reasonsList.isEmpty()) {
                reasonsList.add("High dynamic variance based on sensors")
            }

            // Build a human-readable explanation from the exceeded thresholds
            explanation = buildExplanation(rawValuesByFeatureName, reasonsList)
        }
        
        val rpm = sensorData["rpm"] ?: 0.0
        return BehaviorResult(
            isAggressive = isAggressive,
            probability = probability,
            reasons = reasonsList,
            explanationText = explanation,
            jsonPayload = "{ \"prob\": $probability, \"rpm\": $rpm }"
        )
    }

    // Build a human-readable explanation string from the reasons list
    private fun buildExplanation(
        rawValues: Map<String, Double>,
        reasons: List<String>
    ): String {
        // If only a generic reason was found, return a simple message
        if (reasons.size == 1 && reasons[0].startsWith("High dynamic")) {
            return "Aggressive driving pattern detected based on multiple sensor data."
        }

        // Map specific sensor names to short user-friendly descriptions
        val details = mutableListOf<String>()
        val rpm = rawValues["engine_rpm"] ?: 0.0
        val speed = rawValues["vehicle_speed"] ?: 0.0
        val throttle = rawValues["throttle_position"] ?: 0.0
        val load = rawValues["engine_load"] ?: 0.0

        for (reason in reasons) {
            when {
                reason.contains("Rpm", ignoreCase = true) ->
                    details.add("High RPM Detected as (${rpm.toInt()}) \n⚡ Driving Tip: Control you acceleration pattern.")
                reason.contains("Speed", ignoreCase = true) ->
                    details.add("Over Speeding (${speed.toInt()} km/h) \n⚡ Driving Tip: Manage proper speed to save fuel.")
                reason.contains("Throttle", ignoreCase = true) && details.none { it.contains("Throttle") } ->
                    details.add("Excessive Throttle (${throttle.toInt()}%)  \n⚡ Driving Tip: Control your gas paddle to increase the fuel efficiency.")
                reason.contains("Load", ignoreCase = true) ->
                    details.add("High Engine Load (${load.toInt()}%) \n⚡ Driving Tip: Reduce rapid acceleration or high AC usage.")
                reason.contains("Pedal", ignoreCase = true) && details.none { it.contains("Pedal") } ->
                    details.add("Heavy Acceleration  \n⚡ Driving Tip: Do not accelerate too fast.")
            }
        }

        // If no specific details were found from the reasons, use a fallback
        if (details.isEmpty()) {
            return "Aggressive driving pattern detected."
        }

        return "Aggressive Driving: ${details.joinToString(", ")}"
    }

    fun close() {
        interpreter?.close()
    }
}
