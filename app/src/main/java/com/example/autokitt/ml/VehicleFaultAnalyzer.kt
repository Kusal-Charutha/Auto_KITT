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

class VehicleFaultAnalyzer(private val context: Context) {
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
            Log.e("AutoKITT_ML", "Failed to initialize VehicleFaultAnalyzer: ${e.message}")
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

    fun analyze(sensorData: Map<String, Double>): FaultResult? {
        if (interpreter == null || means.isEmpty() || featureNames.isEmpty()) return null
        
        // 1. Map raw input to features
        val rawFeatures = FloatArray(24)
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
        
        val status = if (probability > 0.5f) "Faulty" else "Good"
        
        return FaultResult(
            status = status,
            probability = probability,
            possibleReason = if (status == "Faulty") "General Fault Indicator" else "No faults detected",
            explanationText = if (status == "Faulty") "Vehicle health diagnostics indicate a potential systemic fault." else "All systems operating normally.",
            jsonPayload = "{ \"prob\": $probability }"
        )
    }

    fun close() {
        interpreter?.close()
    }
}
