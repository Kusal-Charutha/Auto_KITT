package com.example.autokitt.ml

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

class VehicleFaultAnalyzer(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val featureOrder = mutableListOf<String>()
    private val scalerMean = mutableMapOf<String, Float>()
    private val scalerScale = mutableMapOf<String, Float>()
    
    // Configurable threshold
    var faultThreshold: Float = 0.5f

    init {
        try {
            // 1. Load Interpreter
            val modelBuffer = loadModelFile("models/vehicle_fault_prediction_model/vehicle_fault_model.tflite")
            interpreter = Interpreter(modelBuffer)

            // 2. Load Feature Order
            val foJson = loadJSONFromAsset("models/vehicle_fault_prediction_model/vehicle_fault_feature_order.json")
            val foArray = JSONArray(foJson)
            for (i in 0 until foArray.length()) {
                featureOrder.add(foArray.getString(i))
            }

            // 3. Load Scaler
            val sJson = loadJSONFromAsset("models/vehicle_fault_prediction_model/vehicle_fault_scaler.json")
            val sObj = JSONObject(sJson)
            val meanArray = sObj.getJSONArray("mean")
            val scaleArray = sObj.getJSONArray("scale")

            for (i in 0 until featureOrder.size) {
                val featureName = featureOrder[i]
                scalerMean[featureName] = meanArray.getDouble(i).toFloat()
                scalerScale[featureName] = scaleArray.getDouble(i).toFloat()
            }

            Log.d("VehicleFault", "Vehicle fault model and metadata loaded successfully.")
        } catch (e: Exception) {
            Log.e("VehicleFault", "Error initializing analyzer", e)
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

    // Map OBD keys to Model Feature Names
    private val sensorToFeatureMap = mapOf(
        "runtime" to "ENGINE_RUN_TINE", // Typo in model file ENGINE_RUN_TINE
        "rpm" to "ENGINE_RPM",
        "speed" to "VEHICLE_SPEED",
        "absThrottle" to "THROTTLE",
        "calcLoad" to "ENGINE_LOAD",
        "coolantTemp" to "COOLANT_TEMPERATURE",
        "ltft1" to "LONG_TERM_FUEL_TRIM_BANK_1",
        "stft1" to "SHORT_TERM_FUEL_TRIM_BANK_1",
        "map" to "INTAKE_MANIFOLD_PRESSURE",
        "fuelLevel" to "FUEL_TANK",
        "absThrottleB" to "ABSOLUTE_THROTTLE_B",
        "pedalD" to "PEDAL_D",
        "pedalE" to "PEDAL_E",
        "throttleCmd" to "COMMANDED_THROTTLE_ACTUATOR",
        "equivRatio" to "FUEL_AIR_COMMANDED_EQUIV_RATIO",
        "baro" to "ABSOLUTE_BAROMETRIC_PRESSURE",
        "relThrottle" to "RELATIVE_THROTTLE_POSITION",
        "intakeTemp" to "INTAKE_AIR_TEMP",
        "timingAdvance" to "TIMING_ADVANCE",
        "catTempB1S1" to "CATALYST_TEMPERATURE_BANK1_SENSOR1",
        "catTempB1S2" to "CATALYST_TEMPERATURE_BANK1_SENSOR2", // Assuming mapped
        "voltage" to "CONTROL_MODULE_VOLTAGE",
        "evapPurge" to "COMMANDED_EVAPORATIVE_PURGE",
        "warmups" to "WARM_UPS_SINCE_CODES_CLEARED"
    )

    fun analyze(sensorData: Map<String, Double>): VehicleDiagnosticResult? {
        if (interpreter == null || featureOrder.isEmpty()) return null

        val unscaledFeatures = mutableMapOf<String, Double>()
        val missingFeaturesList = mutableListOf<String>()

        for (featureName in featureOrder) {
            val sensorKey = sensorToFeatureMap.entries.firstOrNull { it.value == featureName }?.key
            
            val value = if (sensorKey != null && sensorData.containsKey(sensorKey)) {
                val sensorVal = sensorData[sensorKey]!!
                if (sensorVal != -1.0) {
                    sensorVal
                } else {
                    missingFeaturesList.add(featureName)
                    scalerMean[featureName]?.toDouble() ?: 0.0
                }
            } else {
                missingFeaturesList.add(featureName)
                scalerMean[featureName]?.toDouble() ?: 0.0
            }
            unscaledFeatures[featureName] = value
        }

        if (missingFeaturesList.isNotEmpty()) {
            Log.d("VehicleFault", "Missing features (fallback applied): $missingFeaturesList")
        }

        val inputValues = FloatArray(featureOrder.size)
        for (i in featureOrder.indices) {
            val feature = featureOrder[i]
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
            Log.e("VehicleFault", "Inference failed", e)
            return null
        }

        val probability = outputTensor[0][0]
        Log.d("VehicleFault", "Model output probability: $probability")
        
        val isFaulty = probability >= faultThreshold
        val status = if (isFaulty) "Faulty" else "Good"

        val reasons = mutableListOf<String>()
        val evidence = JSONObject()

        if (isFaulty) {
            // Rule-Based Reasoning Layer
            val coolant = sensorData["coolantTemp"] ?: -1.0
            if (coolant > 105.0) {
                reasons.add("High Engine Temperature")
                evidence.put("COOLANT_TEMPERATURE", coolant)
            }

            val voltage = sensorData["voltage"] ?: -1.0
            if (voltage != -1.0 && (voltage < 11.5 || voltage > 15.0)) {
                reasons.add("Electrical System Anomaly")
                evidence.put("CONTROL_MODULE_VOLTAGE", voltage)
            }

            val intakeTemp = sensorData["intakeTemp"] ?: -1.0
            if (intakeTemp > 70.0) {
                reasons.add("Intake Air Temperature High")
                evidence.put("INTAKE_AIR_TEMP", intakeTemp)
            }

            val ltft1 = sensorData["ltft1"] ?: 0.0
            val stft1 = sensorData["stft1"] ?: 0.0
            if (abs(ltft1) > 25.0 || abs(stft1) > 25.0) {
                reasons.add("Fuel Trim Abnormality")
                evidence.put("LTFT1", ltft1)
                evidence.put("STFT1", stft1)
            }

            if (reasons.isEmpty()) {
                reasons.add("Performance inconsistency detected")
            }
        }

        val outputJson = JSONObject()
        outputJson.put("status", status)
        outputJson.put("score", String.format("%.2f", probability).toDouble())
        outputJson.put("reasons", JSONArray(reasons))
        outputJson.put("evidence", evidence)
        
        val explanationText = if (isFaulty) {
            "Possible fault detected: ${reasons.joinToString(", ")}"
        } else {
            "No issues detected."
        }
        
        val reasonString = if (reasons.isNotEmpty()) reasons.joinToString(", ") else "No issues detected"

        return VehicleDiagnosticResult(
            status = status,
            probability = probability,
            possibleReason = reasonString,
            missingFeatures = missingFeaturesList,
            timestamp = System.currentTimeMillis(),
            jsonPayload = outputJson.toString(2),
            explanationText = explanationText
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
