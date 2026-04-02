package com.example.autokitt.ml

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DriverBehaviorAnalyzer(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val featureColumns = mutableListOf<String>()
    private val scalerMean = mutableMapOf<String, Float>()
    private val scalerScale = mutableMapOf<String, Float>()

    private val adviceMap = mapOf(
        "Harsh acceleration" to "Accelerate more smoothly",
        "Aggressive steering" to "Avoid sudden steering changes",
        "Over-speed tendency" to "Maintain a steadier and safer speed",
        "High engine strain" to "Reduce throttle and engine load"
    )

    init {
        try {
            // 1. Load Interpreter
            val modelBuffer = loadModelFile("models/driver_behavior_alert_model/driver_behavior_model.tflite")
            interpreter = Interpreter(modelBuffer)

            // 2. Load Feature Columns
            val fcJson = loadJSONFromAsset("models/driver_behavior_alert_model/driver_behavior_feature_order.json")
            val fcArray = JSONArray(fcJson)
            for (i in 0 until fcArray.length()) {
                featureColumns.add(fcArray.getString(i))
            }

            // 3. Load Scaler Params
            val spJson = loadJSONFromAsset("models/driver_behavior_alert_model/driver_behavior_scaler.json")
            val spObj = JSONObject(spJson)
            val meanArray = spObj.getJSONArray("mean")
            val scaleArray = spObj.getJSONArray("scale")
            
            for (i in 0 until featureColumns.size) {
                val feature = featureColumns[i]
                scalerMean[feature] = meanArray.getDouble(i).toFloat()
                scalerScale[feature] = scaleArray.getDouble(i).toFloat()
            }

            Log.d("DriverBehavior", "Model and metadata loaded successfully.")
        } catch (e: Exception) {
            Log.e("DriverBehavior", "Error initializing analyzer", e)
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadJSONFromAsset(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    data class BehaviorResult(
        val isAggressive: Boolean,
        val probability: Float,
        val reasons: List<String>,
        val jsonPayload: String,
        val explanationText: String
    )

    private val sensorToFeatureMap = mapOf(
        "voltage" to "battery_voltage",
        "calcLoad" to "engine_load",
        "coolantTemp" to "engine_coolant_temp",
        "map" to "intake_manifold_pressure",
        "rpm" to "engine_rpm",
        "speed" to "vehicle_speed",
        "timingAdvance" to "ignition_timing_advance",
        "intakeTemp" to "intake_air_temp",
        "absThrottle" to "throttle_position",
        "controlModuleVoltage" to "control_module_voltage",
        "absLoad" to "absolute_load",
        "relThrottle" to "relative_throttle_position",
        "absThrottleB" to "throttle_position_b",
        "pedalD" to "accelerator_pedal_d",
        "pedalE" to "accelerator_pedal_e",
        "throttleCmd" to "throttle_actuator_control",
        "steeringAngle" to "steering_angle",
        "steeringRate" to "steering_angular_velocity",
        "stft1" to "short_term_fuel_trim_bank_1",
        "ltft1" to "long_term_fuel_trim_bank_1"
    )

    fun analyze(sensorData: Map<String, Double>): BehaviorResult? {
        if (interpreter == null || featureColumns.isEmpty()) return null

        val unscaledFeatures = mutableMapOf<String, Double>()
        for (featureName in featureColumns) {
            val sensorKey = sensorToFeatureMap.entries.firstOrNull { it.value == featureName }?.key
            val value = if (sensorKey != null && sensorData.containsKey(sensorKey)) {
                val sensorVal = sensorData[sensorKey]!!
                if (sensorVal != -1.0) sensorVal else (scalerMean[featureName]?.toDouble() ?: 0.0)
            } else {
                scalerMean[featureName]?.toDouble() ?: 0.0
            }
            unscaledFeatures[featureName] = value
        }

        val inputValues = FloatArray(featureColumns.size)
        for (i in featureColumns.indices) {
            val feature = featureColumns[i]
            val value = unscaledFeatures[feature] ?: 0.0
            val mean = scalerMean[feature] ?: 0f
            val scale = scalerScale[feature] ?: 1f
            val scaled = if (scale != 0f) ((value - mean) / scale).toFloat() else 0f
            inputValues[i] = scaled
        }

        val inputTensor = arrayOf(inputValues)
        val outputTensor = Array(1) { FloatArray(1) }

        try {
            interpreter?.run(inputTensor, outputTensor)
        } catch (e: Exception) {
            Log.e("DriverBehavior", "Inference failed", e)
            return null
        }

        val probability = outputTensor[0][0]
        val isAggressive = probability > 0.5f

        val reasons = mutableListOf<String>()
        val reasonScoresObj = JSONObject()
        
        if (isAggressive) {
            // Heuristic Reasoning for Aggressive Behavior
            val rpm = sensorData["rpm"] ?: 0.0
            if (rpm > 4000.0) {
                reasons.add("High engine strain")
            }

            val speed = sensorData["speed"] ?: 0.0
            if (speed > 110.0) {
                reasons.add("Over-speed tendency")
            }

            val throttle = sensorData["absThrottle"] ?: 0.0
            if (throttle > 80.0) {
                reasons.add("Harsh acceleration")
            }

            // Note: Steering heuristics would require time-series delta, which we don't have in this single-point analyze call.
            // But we can add a fallback if no heuristics trigger.
            if (reasons.isEmpty()) {
                reasons.add("Irregular driving pattern")
            }
        }

        val status = if (isAggressive) "Aggressive" else "Normal"
        val outputJson = JSONObject()
        outputJson.put("status", status)
        outputJson.put("score", String.format("%.2f", probability).toDouble())
        outputJson.put("reasons", JSONArray(reasons))
        
        val explanationText = if (isAggressive) {
            val adviceStr = reasons.mapNotNull { adviceMap[it] }.joinToString(". ")
            "Aggressive driving detected: ${reasons.joinToString(", ")}. $adviceStr"
        } else {
            "Driving smoothly."
        }

        return BehaviorResult(isAggressive, probability, reasons, outputJson.toString(2), explanationText)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
