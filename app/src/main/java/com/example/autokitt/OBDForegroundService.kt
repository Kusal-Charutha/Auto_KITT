package com.example.autokitt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import android.media.AudioAttributes
import android.net.Uri
import androidx.annotation.RequiresPermission
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.autokitt.database.AppDatabase
import com.example.autokitt.database.SensorData
import com.example.autokitt.ml.DriverBehaviorAnalyzer
import com.example.autokitt.ml.VehicleFaultAnalyzer
import com.example.autokitt.utils.DtcDictionary
import java.util.ArrayList

class OBDForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private lateinit var database: AppDatabase
    private var sessionId: Long = 0L
    private var userEmail: String = "guest"
    private var userName: String = "Guest"
    private var behaviorAnalyzer: DriverBehaviorAnalyzer? = null
    private var faultAnalyzer: VehicleFaultAnalyzer? = null

    // Idle trackers
    private var idleStartTime: Long = 0L
    private var revvingWhileStationaryStartTime: Long = 0L
    private var alertedRevvingImmediate = false
    private var alertedIdle3Min = false

    private var lastFaultAlertTime: Long = 0
    private val FAULT_ALERT_COOLDOWN_MS: Long = 60000 
    
    private var lastDtcPollTime: Long = 0
    private val DTC_POLL_INTERVAL_MS: Long = 60000 
    private val reportedDtcs = mutableSetOf<String>()
    
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    private val sensorDataMap = mutableMapOf<String, Double>()
    
    // Diagnostic sensors queue for round-robin polling
    private val diagPids = listOf(
        "runTime" to "011F",
        "map" to "010B",
        "timingAdvance" to "010E",
        "intakeTemp" to "010F",
        "fuelLevel" to "012F",
        "baro" to "0133",
        "voltage" to "0142",
        "equivRatio" to "0144",
        "relThrottle" to "0145",
        "absThrottleB" to "0147",
        "pedalD" to "0149",
        "pedalE" to "014A",
        "throttleCmd" to "014C",
        "ltft1" to "0107",
        "stft1" to "0106",
        "catTempB1S1" to "013C",
        "catTempB1S2" to "013D",
        "evapPurge" to "012E",
        "warmups" to "0130"
    )
    private var diagIndex = 0

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val CHANNEL_ID = "AutoKITT_Channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_OBD_DATA = "com.example.autokitt.OBD_DATA"
        const val ACTION_OBD_CONNECTED = "com.example.autokitt.OBD_CONNECTED"
        const val ACTION_OBD_DISCONNECTED = "com.example.autokitt.OBD_DISCONNECTED"
        const val ACTION_VEHICLE_INFO_UPDATE = "com.example.autokitt.VEHICLE_INFO_UPDATE"
        const val EXTRA_VEHICLE_NAME = "extra_vehicle_name"

        const val EXTRA_RPM = "extra_rpm"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_LOAD = "extra_load"
        const val EXTRA_TEMP = "extra_temp"
        const val EXTRA_THROTTLE = "extra_throttle"
        
        // Expanded Extras
        const val EXTRA_INTAKE = "extra_intake"
        const val EXTRA_VOLTAGE = "extra_voltage"
        const val EXTRA_STFT = "extra_stft"

        const val ACTION_BEHAVIOR_ALERT = "com.example.autokitt.BEHAVIOR_ALERT"
        const val EXTRA_IS_AGGRESSIVE = "extra_is_aggressive"
        const val EXTRA_BEHAVIOR_PROBABILITY = "extra_behavior_probability"
        const val EXTRA_BEHAVIOR_REASONS = "extra_behavior_reasons"

        const val ACTION_VEHICLE_FAULT_ALERT = "com.example.autokitt.VEHICLE_FAULT_ALERT"
        const val EXTRA_FAULT_STATUS = "extra_fault_status"
        const val EXTRA_FAULT_PROBABILITY = "extra_fault_probability"
        const val EXTRA_FAULT_REASON = "extra_fault_reason"
        const val EXTRA_EXPLANATION_TEXT = "extra_explanation_text"
        
        const val ALERT_CHANNEL_ID = "AutoKITT_Alert_Channel"
        const val SILENT_ALERT_CHANNEL_ID = "AutoKITT_Silent_Alert_Channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null
 
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        
        // Initialize Analyzers
        behaviorAnalyzer = DriverBehaviorAnalyzer(this)
        faultAnalyzer = VehicleFaultAnalyzer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        userEmail = intent?.getStringExtra("user_email") ?: "guest"
        userName = intent?.getStringExtra("user_name") ?: "Guest"

        if (!isRunning && address != null) {
            isRunning = true
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Connecting to OBD-II..."))
            
            serviceScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT) {
                sessionId = System.currentTimeMillis()
                connectAndPoll(address)
            }
        }
        return START_STICKY
    }


    private suspend fun connectAndPoll(address: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(address)

        while (isRunning) {
            try {
                updateNotification("Connecting to $address...")
                bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                
                try {
                    bluetoothSocket?.connect()
                } catch (e: IOException) {
                    bluetoothSocket?.close()
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothSocket?.connect()
                }
                
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                updateNotification("Initializing Protocol...")
                sendCommand("ATZ")
                delay(1000)
                sendCommand("ATE0")
                sendCommand("ATL0")
                sendCommand("ATSP0")
                
                updateNotification("Polling Sensors...")
                sendBroadcast(Intent(ACTION_OBD_CONNECTED))
                
                var loopCount = 0
                while (serviceScope.isActive && bluetoothSocket?.isConnected == true) {
                    try {
                        // -- TIER 1: High Priority (Every loop) --
                        val rpm = pollPIDSafe("010C") 
                        val speed = pollPIDSafe("010D")
                        val load = pollPIDSafe("0104")
                        val throttle = pollPIDSafe("0111")
                        val coolant = pollPIDSafe("0105")

                        sensorDataMap["rpm"] = rpm
                        sensorDataMap["speed"] = speed
                        sensorDataMap["calcLoad"] = load
                        sensorDataMap["absThrottle"] = throttle
                        sensorDataMap["coolantTemp"] = coolant

                        // -- TIER 2: Diagnostic (1 per loop) --
                        val (key, pid) = diagPids[diagIndex]
                        sensorDataMap[key] = pollPIDSafe(pid)
                        diagIndex = (diagIndex + 1) % diagPids.size



                        // UI Broadcast (Legacy support for Dashboard)
                        if (rpm != -1.0 && speed != -1.0) {
                            broadcastData(rpm, speed, load, coolant, throttle)
                        }
                        
                        // Poll Physical DTCs (Every 60s)
                        if (System.currentTimeMillis() - lastDtcPollTime > DTC_POLL_INTERVAL_MS) {
                            lastDtcPollTime = System.currentTimeMillis()
                            val dtcs = pollDTCs()
                            for (code in dtcs) {
                                if (!reportedDtcs.contains(code)) {
                                    reportedDtcs.add(code)
                                    val description = DtcDictionary.getDescription(code)
                                    sendFaultNotification("Fault Detected", "$code: $description")
                                    logAlert("FAULT", "Physical DTC Detected: $code", "{ \"code\": \"$code\" }", "CRITICAL", description)
                                    
                                    sendBroadcast(Intent(ACTION_VEHICLE_FAULT_ALERT).apply {
                                        putExtra(EXTRA_FAULT_STATUS, "Faulty ($code)")
                                        putExtra(EXTRA_FAULT_PROBABILITY, 1.0f)
                                        putExtra(EXTRA_FAULT_REASON, code)
                                        putExtra(EXTRA_EXPLANATION_TEXT, description)
                                    })
                                }
                            }
                        }

                        // Stationary Heuristics (Always evaluate regardless of speed check)
                        processStationaryAlerts(speed, rpm)

                        if (rpm > 0.0) { // Unblocked: Analyse whenever engine is ON
                            val behaviorResult = behaviorAnalyzer?.analyze(sensorDataMap)
                            val faultResult = faultAnalyzer?.analyze(sensorDataMap)
                            
                            if (behaviorResult != null) {
                                val alertIntent = Intent(ACTION_BEHAVIOR_ALERT).apply {
                                    putExtra(EXTRA_IS_AGGRESSIVE, behaviorResult.isAggressive)
                                    putExtra(EXTRA_BEHAVIOR_PROBABILITY, behaviorResult.probability)
                                    putStringArrayListExtra(EXTRA_BEHAVIOR_REASONS, ArrayList(behaviorResult.reasons))
                                    putExtra(EXTRA_EXPLANATION_TEXT, behaviorResult.explanationText)
                                }
                                sendBroadcast(alertIntent)

                                if (behaviorResult.isAggressive) {
                                    sendSystemNotification("Aggressive Driving", behaviorResult.explanationText)
                                    val severity = if (behaviorResult.probability > 0.75) "CRITICAL" else "GOOD"
                                    val advice = behaviorResult.reasons.joinToString(". ")
                                    logAlert("BEHAVIOR", behaviorResult.explanationText, behaviorResult.jsonPayload, severity, advice)
                                }
                            }

                            if (faultResult != null) {
                                sendBroadcast(Intent(ACTION_VEHICLE_FAULT_ALERT).apply {
                                    putExtra(EXTRA_FAULT_STATUS, faultResult.status)
                                    putExtra(EXTRA_FAULT_PROBABILITY, faultResult.probability)
                                    putExtra(EXTRA_FAULT_REASON, faultResult.possibleReason)
                                    putExtra(EXTRA_EXPLANATION_TEXT, faultResult.explanationText)
                                })

                                // Log all fault tiers (INFO, WARNING, CRITICAL) to the database
                                if (faultResult.status != "Good") {
                                    val severity = when {
                                        faultResult.probability >= 0.85f -> "CRITICAL"
                                        faultResult.probability > 0.7f  -> "WARNING"
                                        else                            -> "INFO"
                                    }
                                    logAlert("FAULT", faultResult.explanationText, faultResult.jsonPayload, severity, faultResult.advice)

                                    if (System.currentTimeMillis() - lastFaultAlertTime > FAULT_ALERT_COOLDOWN_MS) {
                                        if (faultResult.probability >= 0.85f) {
                                            // CRITICAL: Loud system notification with sound
                                            sendFaultNotification("Critical Fault Detected", faultResult.possibleReason)
                                        } else {
                                            // WARNING/INFO: Silent notification (no sound)
                                            val title = if (severity == "WARNING") "Vehicle Warning" else "Vehicle Health Notice"
                                            sendSilentFaultNotification(title, faultResult.possibleReason)
                                        }
                                        lastFaultAlertTime = System.currentTimeMillis()
                                    }
                                }
                            }
                        }

                        // Persistence & Sync
                        if (rpm != -1.0) {
                            saveAndSyncData(rpm, speed, load, throttle, coolant)
                        }
                        
                    } catch (e: Exception) {
                        Log.e("AutoKITT", "Polling snap — continuing loop. Error: ${e.message}")
                    }

                    loopCount++
                    delay(300) // Lowered to keep near 1Hz (6 PIDs ~ 600ms + 300ms delay)
                }

            } catch (e: Exception) {
                Log.e("AutoKITT", "OBD-II Communication Fatal Error: ${e.message}", e)
                updateNotification("Disconnected. Retrying in 5s...")
                cleanup()
                delay(5000)
            }
        }
    }

    private fun processStationaryAlerts(speed: Double, rpm: Double) {
        val currentTime = System.currentTimeMillis()

        // Handling Idle Scenarios
        if (speed < 1 ) {
            if (rpm > 1300) {
                if (!alertedRevvingImmediate) {
                    sendSystemNotification("Unnecessary Revving", "Don't push the Accelerator paddle while Idling.")
                    alertedRevvingImmediate = true
                }
            } else {
                alertedRevvingImmediate = false
            }

            if (rpm > 0 && rpm < 1300) {
                if (idleStartTime == 0L) idleStartTime = currentTime
                if (!alertedIdle3Min && (currentTime - idleStartTime) > 180000) {
                    sendSystemNotification("Idle Tip", "Engine has been idling for 3 minutes.")
                    alertedIdle3Min = true
                }
            }
        } else {
            idleStartTime = 0L
            alertedIdle3Min = false
        }
    }

    private suspend fun saveAndSyncData(rpm: Double, speed: Double, load: Double, throttle: Double, coolant: Double) {
        val ts = System.currentTimeMillis()
        val data = SensorData(
            sessionId = sessionId,
            timestamp = ts,
            date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(ts)),
            engineRpm = rpm,
            vehicleSpeed = speed,
            engineLoad = load,
            throttlePos = throttle,
            coolantTemp = coolant
        )
        try {
            withContext(Dispatchers.IO) { database.sensorDataDao().insert(data) }
        } catch (e: Exception) {
            Log.e("AutoKITT", "Failed to save sensor data: ${e.message}")
        }
    }

    private fun logAlert(type: String, text: String, payload: String, severity: String = "INFO", advice: String = "") {
        serviceScope.launch(Dispatchers.IO) {
            try {
                database.alertLogDao().insert(com.example.autokitt.database.AlertLog(
                    timestamp = System.currentTimeMillis(),
                    alertType = type,
                    explanationText = text,
                    jsonPayload = payload,
                    severity = severity,
                    advice = advice
                ))
            } catch (e: Exception) {
                Log.e("AutoKITT", "Failed to log alert: ${e.message}")
            }
        }
    }

    private fun sendCommand(cmd: String) {
        try {
            outputStream?.write((cmd + "\r").toByteArray())
            outputStream?.flush()
            readResponse() 
        } catch (e: Exception) {}
    }
    
    private fun readResponse(): String {
        val buffer = ByteArray(1024)
        val sb = StringBuilder()
        val timeoutMs = 400
        val startTime = System.currentTimeMillis()
        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                 if (inputStream?.available()!! > 0) {
                     val bytes = inputStream?.read(buffer)
                     if (bytes != null && bytes > 0) {
                         val chunk = String(buffer, 0, bytes)
                         sb.append(chunk)
                         if (chunk.contains(">")) break
                     }
                 } else {
                     Thread.sleep(10)
                 }
            }
        } catch (e: Exception) {}
        return sb.toString()
    }

    private fun pollPID(pid: String): Double {
        try {
            outputStream?.write((pid + "\r").toByteArray())
            outputStream?.flush()
            Thread.sleep(60) 
            val response = readResponse()
            return OBDParser.parse(pid, response)
        } catch (e: Exception) {
            return -1.0
        }
    }

    private fun pollPIDSafe(pid: String): Double {
        var value = pollPID(pid)
        if (value == -1.0) value = pollPID(pid) // Quick retry
        return value
    }

    private fun pollDTCs(): List<String> {
        try {
            outputStream?.write(("03\r").toByteArray())
            outputStream?.flush()
            Thread.sleep(100) 
            val response = readResponse()
            return OBDParser.parseDTCs(response)
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun cleanup() {
        sendBroadcast(Intent(ACTION_OBD_DISCONNECTED))
        try { bluetoothSocket?.close() } catch (e: Exception) {}
        bluetoothSocket = null
    }

    override fun onDestroy() {
        isRunning = false
        behaviorAnalyzer?.close()
        faultAnalyzer?.close()
        cleanup()
        serviceScope.cancel() 
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(CHANNEL_ID, "ELM327 Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(serviceChannel)
            
            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical driving and fault alerts"
            }
            manager.createNotificationChannel(alertChannel)

            // Silent channel for WARNING/INFO level fault alerts
            val silentChannel = NotificationChannel(SILENT_ALERT_CHANNEL_ID, "Silent Alerts", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Low priority vehicle health notices"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(silentChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoKITT Telemetry")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun sendFaultNotification(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify("FAULT_ALERT", 2, notification)
    }

    // Silent notification for WARNING/INFO level faults (no sound, no vibration)
    private fun sendSilentFaultNotification(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, SILENT_ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private fun sendSystemNotification(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private fun broadcastData(rpm: Double, speed: Double, load: Double, temp: Double, throttle: Double) {
        val intent = Intent(ACTION_OBD_DATA).apply {
            putExtra(EXTRA_RPM, rpm)
            putExtra(EXTRA_SPEED, speed)
            putExtra(EXTRA_LOAD, load)
            putExtra(EXTRA_TEMP, temp)
            putExtra(EXTRA_THROTTLE, throttle)
            putExtra(EXTRA_INTAKE, sensorDataMap["map"] ?: -1.0) // Map to existing UI field
            putExtra(EXTRA_VOLTAGE, sensorDataMap["voltage"] ?: -1.0)
            putExtra(EXTRA_STFT, sensorDataMap["stft1"] ?: -1.0)
        }
        sendBroadcast(intent)
    }
}
