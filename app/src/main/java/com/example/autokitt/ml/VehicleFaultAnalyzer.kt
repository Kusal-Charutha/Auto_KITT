package com.example.autokitt.ml

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class VehicleFaultAnalyzer(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var means = FloatArray(0)
    private var scales = FloatArray(0)
    private var featureNames = listOf<String>()

    // Heuristic config loaded from JSON assets
    private var faultThresholds = mutableMapOf<String, Double>()   // e.g. "coolant_high" -> 110.0
    private var faultReasonMap = mutableMapOf<String, ReasonInfo>() // e.g. "coolant" -> ReasonInfo(...)
    
    // Simple data class to hold reason config from JSON
    data class ReasonInfo(val sensor: String, val unit: String, val label: String, val advice: String)

    init {
        try {
            val options = Interpreter.Options()
            interpreter = Interpreter(loadModelFile(), options)
            loadScaler()
            loadFeatureColumns()
            loadFaultThresholds()
            loadFaultReasonMap()
        } catch (e: Exception) {
            // Initialization failed
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("models/vehicle_fault_prediction_model/vehicle_fault_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun loadScaler() {
        val jsonStr = context.assets.open("models/vehicle_fault_prediction_model/scaler.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val meanArray = jsonObject.getJSONArray("mean")
        val scaleArray = jsonObject.getJSONArray("scale")
        
        means = FloatArray(meanArray.length()) { i -> meanArray.getDouble(i).toFloat() }
        scales = FloatArray(scaleArray.length()) { i -> scaleArray.getDouble(i).toFloat() }
    }

    private fun loadFeatureColumns() {
        val jsonStr = context.assets.open("models/vehicle_fault_prediction_model/feature_order.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        featureNames = list
    }

    // Load fault detection thresholds from JSON
    private fun loadFaultThresholds() {
        val jsonStr = context.assets.open("models/vehicle_fault_prediction_model/fault_thresholds.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!jsonObject.isNull(key)) {
                faultThresholds[key] = jsonObject.getDouble(key)
            }
        }
    }

    // Load reason mapping (sensor name, unit, label) from JSON
    private fun loadFaultReasonMap() {
        val jsonStr = context.assets.open("models/vehicle_fault_prediction_model/fault_reason_map.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = jsonObject.getJSONObject(key)
            faultReasonMap[key] = ReasonInfo(
                sensor = entry.getString("sensor"),
                unit = entry.getString("unit"),
                label = entry.getString("label"),
                advice = entry.optString("advice", "")
            )
        }
    }

    fun analyze(sensorData: Map<String, Double>): FaultResult? {
        if (interpreter == null || means.isEmpty() || featureNames.isEmpty()) return null
        
        // 1. Map raw input to features
        val rawFeatures = FloatArray(24)
        val rawValuesBySensor = mutableMapOf<String, Double>()  // Track raw values for heuristic checks

        for (i in featureNames.indices) {
            val feat = featureNames[i]
            val value = when (feat) {
                "ENGINE_RUN_TINE" -> sensorData["runTime"] ?: 0.0
                "ENGINE_RPM" -> sensorData["rpm"] ?: 0.0
                "VEHICLE_SPEED" -> sensorData["speed"] ?: 0.0
                "THROTTLE" -> sensorData["absThrottle"] ?: 0.0
                "ENGINE_LOAD" -> sensorData["calcLoad"] ?: 0.0
                "COOLANT_TEMPERATURE" -> sensorData["coolantTemp"] ?: 0.0
                "LONG_TERM_FUEL_TRIM_BANK_1" -> sensorData["ltft1"] ?: 0.0
                "SHORT_TERM_FUEL_TRIM_BANK_1" -> sensorData["stft1"] ?: 0.0
                "INTAKE_MANIFOLD_PRESSURE" -> sensorData["map"] ?: 0.0
                "FUEL_TANK" -> sensorData["fuelLevel"] ?: 0.0
                "ABSOLUTE_THROTTLE_B" -> sensorData["absThrottleB"] ?: 0.0
                "PEDAL_D" -> sensorData["pedalD"] ?: 0.0
                "PEDAL_E" -> sensorData["pedalE"] ?: 0.0
                "COMMANDED_THROTTLE_ACTUATOR" -> sensorData["throttleCmd"] ?: 0.0
                "FUEL_AIR_COMMANDED_EQUIV_RATIO" -> sensorData["equivRatio"] ?: 0.0
                "ABSOLUTE_BAROMETRIC_PRESSURE" -> sensorData["baro"] ?: 0.0
                "RELATIVE_THROTTLE_POSITION" -> sensorData["relThrottle"] ?: 0.0
                "INTAKE_AIR_TEMP" -> sensorData["intakeTemp"] ?: 0.0
                "TIMING_ADVANCE" -> sensorData["timingAdvance"] ?: 0.0
                "CATALYST_TEMPERATURE_BANK1_SENSOR1" -> sensorData["catTempB1S1"] ?: 0.0
                "CATALYST_TEMPERATURE_BANK1_SENSOR2" -> sensorData["catTempB1S2"] ?: 0.0
                "CONTROL_MODULE_VOLTAGE" -> sensorData["voltage"] ?: 0.0
                "COMMANDED_EVAPORATIVE_PURGE" -> sensorData["evapPurge"] ?: 0.0
                "WARM_UPS_SINCE_CODES_CLEARED" -> sensorData["warmups"] ?: 0.0
                else -> 0.0
            }
            rawFeatures[i] = value.toFloat()
            rawValuesBySensor[feat] = value
        }

        // 2. Scale
        val inputBuffer = ByteBuffer.allocateDirect(24 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until 24) {
            val scaled = (rawFeatures[i] - means[i]) / scales[i]
            inputBuffer.putFloat(scaled)
        }

        // 3. Inference
        val outputBuffer = ByteBuffer.allocateDirect(1 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        interpreter?.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val probability = outputBuffer.float
        
        val isFaulty = probability > 0.5f

        // 4. Heuristic reason detection (runs when model predicts a fault)
        val detectedIssues = mutableListOf<String>()

        if (isFaulty) {
            // Check each threshold against current sensor values
            for ((thresholdKey, thresholdValue) in faultThresholds) {
                // Extract base key (e.g. "coolant" from "coolant_high")
                val baseKey = thresholdKey.replace("_high", "").replace("_low", "")
                val reasonInfo = faultReasonMap[baseKey] ?: continue
                val currentValue = rawValuesBySensor[reasonInfo.sensor] ?: continue

                // Skip zero values (sensor not yet polled)
                if (currentValue == 0.0) continue

                val exceeded = when {
                    thresholdKey.endsWith("_high") -> currentValue > thresholdValue
                    thresholdKey.endsWith("_low") -> currentValue < thresholdValue
                    else -> false
                }

                if (exceeded) {
                    val valueStr = if (reasonInfo.unit.isNotEmpty()) {
                        "${String.format("%.1f", currentValue)}${reasonInfo.unit}"
                    } else {
                        String.format("%.2f", currentValue)
                    }
                    detectedIssues.add("${reasonInfo.label} ($valueStr)")
                }
            }
        }

        val status: String
        val severity: String
        val possibleReason: String
        val explanationText: String
        val adviceText: String

        if (isFaulty) {
            // Determine severity layers based on probability
            when {
                probability >= 0.85f -> {
                    status = "Faulty"
                    severity = "CRITICAL"
                }
                probability > 0.7f -> {
                    status = "Requires Maintenance"
                    severity = "WARNING"
                }
                else -> {
                    status = "Good (Monitor)"
                    severity = "INFO"
                }
            }

            if (detectedIssues.isNotEmpty()) {
                possibleReason = detectedIssues.first()
                explanationText = "Vehicle Issue: ${detectedIssues.joinToString(", ")}"
                
                // Collect advice for all detected issues
                val uniqueAdvice = mutableSetOf<String>()
                for (thresholdKey in faultThresholds.keys) {
                    val baseKey = thresholdKey.replace("_high", "").replace("_low", "")
                    val reason = faultReasonMap[baseKey] ?: continue
                    val currentValue = rawValuesBySensor[reason.sensor] ?: continue
                    
                    val exceeded = when {
                        thresholdKey.endsWith("_high") -> currentValue > faultThresholds[thresholdKey]!!
                        thresholdKey.endsWith("_low") -> currentValue < faultThresholds[thresholdKey]!!
                        else -> false
                    }
                    if (exceeded && reason.advice.isNotEmpty()) uniqueAdvice.add(reason.advice)
                }
                adviceText = if (uniqueAdvice.isNotEmpty()) uniqueAdvice.joinToString(" ") else "Consult a vehicle technician for a full diagnostic scan."
            } else {
                possibleReason = "Abnormal sensor pattern detected"
                explanationText = "Vehicle health diagnostics indicate a potential issue based on sensor patterns."
                adviceText = "Perform a manual check of fluid levels and monitor for unusual engine noises."
            }
        } else {
            status = "Good"
            severity = "NONE"
            possibleReason = "No faults detected"
            explanationText = "All systems operating normally."
            adviceText = ""
        }

        return FaultResult(
            status = status,
            probability = probability,
            possibleReason = possibleReason,
            explanationText = explanationText,
            advice = adviceText,
            jsonPayload = "{ \"prob\": $probability, \"issues\": ${detectedIssues.size}, \"severity\": \"$severity\" }"
        )
    }

    fun close() {
        interpreter?.close()
    }
}
