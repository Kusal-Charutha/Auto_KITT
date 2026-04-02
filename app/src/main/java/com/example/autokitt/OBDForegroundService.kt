package com.example.autokitt

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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.autokitt.database.AppDatabase
import com.example.autokitt.database.SensorData
import com.example.autokitt.database.SyncQueueEntry
import com.example.autokitt.network.SyncManager
import com.example.autokitt.ml.DriverBehaviorAnalyzer
import com.example.autokitt.ml.VehicleFaultAnalyzer

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
    private var syncManager: SyncManager? = null
    private var behaviorAnalyzer: DriverBehaviorAnalyzer? = null
    private var faultAnalyzer: VehicleFaultAnalyzer? = null

    // --- Idle Heuristic Trackers ---
    private var idleStartTime: Long = 0L
    private var revvingWhileStationaryStartTime: Long = 0L
    private var alertedRevvingImmediate = false
    private var alertedRevving1Min = false
    private var alertedIdle3Min = false
    private var alertedIdle5Min = false

    private var lastFaultAlertTime: Long = 0
    private val FAULT_ALERT_COOLDOWN_MS: Long = 60000 // 1 minute cooldown

    // Standard SPP UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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
        const val EXTRA_INTAKE = "extra_intake"
        const val EXTRA_VOLTAGE = "extra_voltage"
        const val EXTRA_STFT = "extra_stft"

        const val ACTION_BEHAVIOR_ALERT = "com.example.autokitt.BEHAVIOR_ALERT"
        const val EXTRA_IS_AGGRESSIVE = "extra_is_aggressive"
        const val EXTRA_BEHAVIOR_REASONS = "extra_behavior_reasons"

        const val ACTION_VEHICLE_FAULT_ALERT = "com.example.autokitt.VEHICLE_FAULT_ALERT"
        const val EXTRA_FAULT_STATUS = "extra_fault_status"
        const val EXTRA_FAULT_PROBABILITY = "extra_fault_probability"
        const val EXTRA_FAULT_REASON = "extra_fault_reason"
        const val EXTRA_EXPLANATION_TEXT = "extra_explanation_text"
        
        const val ALERT_CHANNEL_ID = "AutoKITT_Alert_Channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        database = AppDatabase.getDatabase(this)
        behaviorAnalyzer = DriverBehaviorAnalyzer(this)
        faultAnalyzer = VehicleFaultAnalyzer(this)

        // Read logged-in user info for TimescaleDB association
        val prefs = getSharedPreferences("AutoKITT_Prefs", Context.MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "guest") ?: "guest"
        userName = prefs.getString("user_name", "Guest") ?: "Guest"

        // Start the offline-first sync manager
        syncManager = SyncManager(this)
        syncManager?.start()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (deviceAddress == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Connecting to OBD..."))
        
            if (!isRunning) {
            isRunning = true
            sessionId = System.currentTimeMillis() // Generate new session ID
            serviceScope.launch {
                connectAndPoll(deviceAddress)
            }
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun CoroutineScope.connectAndPoll(address: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(address)

        while (isRunning) {
            try {
                updateNotification("Connecting to ${device.name}...")
                Log.d("AutoKITT", "BT_CONNECT: Attempting secure socket...")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                try {
                    bluetoothSocket?.connect()
                    Log.d("AutoKITT", "BT_CONNECT: Secure connection successful")
                } catch (e: IOException) {
                    Log.w("AutoKITT", "BT_CONNECT: Secure connection failed, trying insecure fallback...")
                    bluetoothSocket?.close()
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothSocket?.connect()
                    Log.d("AutoKITT", "BT_CONNECT: Insecure connection successful")
                }
                
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                updateNotification("Initializing ELM327...")
                Log.d("AutoKITT", "BT_INIT: Starting AT commands...")
                sendCommand("ATZ") // Reset
                delay(1000)
                sendCommand("ATE0") // Echo Off
                sendCommand("ATL0") // Linefeeds Off
                sendCommand("ATSP0") // Auto Protocol
                Log.d("AutoKITT", "BT_INIT: Initialization complete")
                
                // Fetch VIN with timeout to avoid blocking main loop
                updateNotification("Fetching Vehicle Info...")
                Log.d("AutoKITT", "BT_INIT: Fetching VIN...")
                outputStream?.write(("0902\r").toByteArray())
                outputStream?.flush()
                
                // Read with a reasonable timeout for VIN
                val vinResponse = withContext(Dispatchers.IO) {
                    var result: String? = null
                    val vinStartTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - vinStartTime < 2000) { // 2s max for VIN
                        val raw = readResponse()
                        if (raw.contains(">")) {
                            result = raw
                            break
                        }
                        delay(200)
                    }
                    result ?: ""
                }
                val vin = if (vinResponse.isNotEmpty()) OBDParser.parseVIN(vinResponse) else null
                
                if (vin != null) {
                    // Start async call to decode VIN
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val url = URL("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/$vin?format=json")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(responseStr)
                            val results = json.getJSONArray("Results").getJSONObject(0)
                            val make = results.optString("Make", "")
                            val model = results.optString("Model", "")
                            val year = results.optString("ModelYear", "")
                            
                            if (make.isNotEmpty()) {
                                val vehicleName = "$year $make $model".trim()
                                val intent = Intent(ACTION_VEHICLE_INFO_UPDATE).apply {
                                    putExtra(EXTRA_VEHICLE_NAME, vehicleName)
                                }
                                sendBroadcast(intent)
                                Log.d("AutoKITT", "Decoded VIN: $vehicleName")
                            }
                        } catch (e: Exception) {
                            Log.e("AutoKITT", "Failed to decode VIN", e)
                        }
                    }
                }

                updateNotification("Polling Data...")
                sendBroadcast(Intent(ACTION_OBD_CONNECTED))
                
                // Polling Loop
                // Polling Optimization
                var loopCount = 0
                
                // Initialize "Slow" variables with default or -1
                var intake = -1.0
                var maf = -1.0
                var fuelRate = -1.0
                var runtime = -1.0
                var ltft1 = -1.0
                var stft1 = -1.0
                var map = -1.0
                var fuelLevel = -1.0
                var absThrottleB = -1.0
                var pedalD = -1.0
                var pedalE = -1.0
                var cmdThrottle = -1.0
                var equivRatio = -1.0
                var baro = -1.0
                var relThrottle = -1.0
                var timing = -1.0
                var catB1S1 = -1.0
                var catB1S2 = -1.0
                var voltage = -1.0
                var evaporation = -1.0
                
                while (isActive && bluetoothSocket?.isConnected == true) {
                    // --- FAST SENSORS (Every Loop) ---
                    // Using pollPIDSafe to retry once on failure
                    val rpm = pollPIDSafe("010C") 
                    val speed = pollPIDSafe("010D")
                    val load = pollPIDSafe("0104")
                    val throttle = pollPIDSafe("0111")
                    val coolant = pollPIDSafe("0105")
                    
                    // --- SLOW SENSORS (Round Robin) ---
                    // Interleave slow sensors to avoid blocking fast data updates
                    when (loopCount % 20) {
                        0 -> intake = pollPID("010F").takeIf { it != -1.0 } ?: intake
                        1 -> maf = pollPID("0110").takeIf { it != -1.0 } ?: maf
                        2 -> fuelRate = pollPID("015E").takeIf { it != -1.0 } ?: fuelRate
                        3 -> runtime = pollPID("011F").takeIf { it != -1.0 } ?: runtime
                        4 -> ltft1 = pollPID("0107").takeIf { it != -1.0 } ?: ltft1
                        5 -> stft1 = pollPID("0106").takeIf { it != -1.0 } ?: stft1
                        6 -> map = pollPID("010B").takeIf { it != -1.0 } ?: map
                        7 -> fuelLevel = pollPID("012F").takeIf { it != -1.0 } ?: fuelLevel
                        8 -> absThrottleB = pollPID("0147").takeIf { it != -1.0 } ?: absThrottleB
                        9 -> pedalD = pollPID("0149").takeIf { it != -1.0 } ?: pedalD
                        10 -> pedalE = pollPID("014A").takeIf { it != -1.0 } ?: pedalE
                        11 -> cmdThrottle = pollPID("014C").takeIf { it != -1.0 } ?: cmdThrottle
                        12 -> equivRatio = pollPID("0144").takeIf { it != -1.0 } ?: equivRatio
                        13 -> baro = pollPID("0133").takeIf { it != -1.0 } ?: baro
                        14 -> relThrottle = pollPID("0145").takeIf { it != -1.0 } ?: relThrottle
                        15 -> timing = pollPID("010E").takeIf { it != -1.0 } ?: timing
                        16 -> catB1S1 = pollPID("013C").takeIf { it != -1.0 } ?: catB1S1
                        17 -> catB1S2 = pollPID("013D").takeIf { it != -1.0 } ?: catB1S2
                        18 -> voltage = pollPID("0142").takeIf { it != -1.0 } ?: voltage
                        19 -> evaporation = pollPID("012E").takeIf { it != -1.0 } ?: evaporation
                    }

                    // Log only fast ones to reduce spam, or intermittent
                    if (loopCount % 10 == 0) Log.d("AutoKITT", "RPM: $rpm, Speed: $speed, Load: $load")

                    // Broadcast data to Dashboard UI
                    if (rpm != -1.0 && speed != -1.0) {
                        broadcastData(rpm, speed, load, coolant, intake, voltage, stft1)
                    }

                    // --- DRIVER BEHAVIOR ANALYSIS ---
                    // Run inference every 5 loops to reduce overhead and allow slow features to update
                    if (loopCount % 5 == 0 && rpm != -1.0) {
                        val sensorMap = mapOf(
                            "rpm" to rpm,
                            "speed" to speed,
                            "calcLoad" to load,
                            "throttleCmd" to cmdThrottle,
                            "coolantTemp" to coolant,
                            "intakeTemp" to intake,
                            "maf" to maf,
                            "fuelRate" to fuelRate,
                            "runtime" to runtime,
                            "ltft1" to ltft1,
                            "stft1" to stft1,
                            "map" to map,
                            "fuelLevel" to fuelLevel,
                            "absThrottleB" to absThrottleB,
                            "pedalD" to pedalD,
                            "pedalE" to pedalE,
                            "equivRatio" to equivRatio,
                            "baro" to baro,
                            "relThrottle" to relThrottle,
                            "timingAdvance" to timing,
                            "catTempB1S1" to catB1S1,
                            "catTempB1S2" to catB1S2,
                            "catTemp" to catB1S1, // Legacy key
                            "controlModuleVoltage" to voltage,
                            "evapPurge" to evaporation,
                            "absThrottle" to throttle,
                            "warmups" to -1.0,
                            "clrDistance" to -1.0
                        )

                        if (speed > 1) {
                            val result = behaviorAnalyzer?.analyze(sensorMap)
                            if (result != null) {
                                val intent = Intent(ACTION_BEHAVIOR_ALERT).apply {
                                    putExtra(EXTRA_IS_AGGRESSIVE, result.isAggressive)
                                    // Use native model reasoning
                                    if (result.isAggressive) {
                                        putExtra(EXTRA_EXPLANATION_TEXT, result.explanationText)
                                        Log.d("DriverBehavior", "JSON: ${result.jsonPayload}")
                                        
                                        val log = com.example.autokitt.database.AlertLog(
                                            timestamp = System.currentTimeMillis(),
                                            alertType = "BEHAVIOR",
                                            explanationText = result.explanationText,
                                            jsonPayload = result.jsonPayload
                                        )
                                        serviceScope.launch(Dispatchers.IO) { database.alertLogDao().insert(log) }
                                    }
                                    putStringArrayListExtra(EXTRA_BEHAVIOR_REASONS, ArrayList(result.reasons))
                                }
                                sendBroadcast(intent)
                            }
                        } else {
                            // Force reset to 'Good' when vehicle is idling or coasting to prevent "Aggressive" sticking
                            val intent = Intent(ACTION_BEHAVIOR_ALERT).apply {
                                putExtra(EXTRA_IS_AGGRESSIVE, false)
                                putStringArrayListExtra(EXTRA_BEHAVIOR_REASONS, ArrayList())
                            }
                            sendBroadcast(intent)
                        }

                        // --- IDLE & REVVING HEURISTICS ---
                        val currentTime = System.currentTimeMillis()
                        if (speed < 1 && rpm > 0) {
                            // 1. Revving While Stationary (Heuristic)
                            if (rpm > 1300) {
                                if (revvingWhileStationaryStartTime == 0L) {
                                    revvingWhileStationaryStartTime = currentTime
                                }
                                
                                // Immediate Alert
                                if (!alertedRevvingImmediate) {
                                    sendSystemNotification("Unnecessary Acceleration Detected : ", "Do not accelerate while idling.")
                                    alertedRevvingImmediate = true
                                }
                                
                            } else {
                                revvingWhileStationaryStartTime = 0L
                                alertedRevvingImmediate = false
                                alertedRevving1Min = false
                            }

                            // 2. Excessive Idling (Heuristic)
                            if (rpm > 0 && rpm < 1200) { // Normal idle range
                                if (idleStartTime == 0L) {
                                    idleStartTime = currentTime
                                }
                                
                                val idleDurationMs = currentTime - idleStartTime
                                
                                // 2 Minute Alert
                                if (!alertedIdle3Min && idleDurationMs > 120000) {
                                    sendSystemNotification("Idle Tip : ", "Vehicle has been idling for 2 minutes. Consider turning off the engine to save fuel.")
                                    alertedIdle3Min = true
                                }

                                // 3.5 Minute Alert
                                if (!alertedIdle3Min && idleDurationMs > 210000) {
                                    sendSystemNotification("Idle Reminder : ", "Vehicle has been idling for more than 3 minutes. Consider turning off the engine to save fuel.")
                                    alertedIdle3Min = true
                                }
                                
                                // 5 Minute Alert
                                if (!alertedIdle5Min && idleDurationMs > 300000) {
                                    sendSystemNotification("Excessive Idle : ", "5-minute idle limit reached. Excessive fuel consumption detected.")
                                    alertedIdle5Min = true
                                }
                            }
                        }


                        // --- VEHICLE FAULT DETECTION ---
                        val faultResult = faultAnalyzer?.analyze(sensorMap)
                        if (faultResult != null) {
                            val faultIntent = Intent(ACTION_VEHICLE_FAULT_ALERT).apply {
                                putExtra(EXTRA_FAULT_STATUS, faultResult.status)
                                putExtra(EXTRA_FAULT_PROBABILITY, faultResult.probability)
                                putExtra(EXTRA_FAULT_REASON, faultResult.possibleReason)
                                
                                // Use native model reasoning
                                if (faultResult.status == "Faulty" || faultResult.status == "Requires Maintenance") {
                                    putExtra(EXTRA_EXPLANATION_TEXT, faultResult.explanationText)
                                    Log.d("VehicleFault", "JSON: ${faultResult.jsonPayload}")

                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastFaultAlertTime > FAULT_ALERT_COOLDOWN_MS) {
                                        sendFaultNotification(faultResult.possibleReason)
                                        lastFaultAlertTime = currentTime
                                    }

                                    val log = com.example.autokitt.database.AlertLog(
                                        timestamp = currentTime,
                                        alertType = "FAULT",
                                        explanationText = faultResult.explanationText,
                                        jsonPayload = faultResult.jsonPayload
                                    )
                                    launch { database.alertLogDao().insert(log) }
                                }
                            }
                            sendBroadcast(faultIntent)
                        }
                    }
                    
                    // Save to Database
                    // We save every loop to get high resolution fast data
                    // Slow data is repeated from previous fetch
                    if (rpm != -1.0) { // Only save if engine is responding at least
                        val currentTime = System.currentTimeMillis()
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val dateStr = sdf.format(java.util.Date(currentTime))
                        
                        val data = SensorData(
                            sessionId = sessionId,
                            timestamp = currentTime,
                            date = dateStr,
                            engineRpm = rpm,
                            vehicleSpeed = speed,
                            engineLoad = load,
                            throttlePos = throttle,
                            coolantTemp = coolant,
                            intakeTemp = intake,
                            maf = maf,
                            fuelRate = fuelRate,
                            engineRunTime = runtime,
                            longTermFuelTrim1 = ltft1,
                            shortTermFuelTrim1 = stft1,
                            intakeManifoldPressure = map,
                            fuelTankLevel = fuelLevel,
                            absoluteThrottleB = absThrottleB,
                            pedalD = pedalD,
                            pedalE = pedalE,
                            commandedThrottleActuator = cmdThrottle,
                            fuelAirCommandedEquivRatio = equivRatio,
                            absBarometricPressure = baro,
                            relativeThrottlePos = relThrottle,
                            timingAdvance = timing,
                            catTempB1S1 = catB1S1,
                            catTempB1S2 = catB1S2,
                            controlModuleVoltage = voltage,
                            commandedEvapPurge = evaporation
                        )
                        
                        // Run persistence in background to avoid blocking the polling loop
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                database.sensorDataDao().insert(data)
                                
                                // Queue for cloud sync (offline-first)
                                val syncEntry = SyncQueueEntry(
                                    userId = userEmail,
                                    userName = userName,
                                    deviceId = "device_autokitt_primary",
                                    sessionId = sessionId,
                                    timestamp = currentTime,
                                    date = dateStr,
                                    engineRpm = rpm,
                                    vehicleSpeed = speed,
                                    throttle = throttle,
                                    engineLoad = load,
                                    coolantTemp = coolant,
                                    intakeTemp = intake,
                                    maf = maf,
                                    fuelRate = fuelRate,
                                    runTime = runtime,
                                    ltft1 = ltft1,
                                    stft1 = stft1,
                                    map = map,
                                    fuelLevel = fuelLevel,
                                    absThrottleB = absThrottleB,
                                    pedalD = pedalD,
                                    pedalE = pedalE,
                                    cmdThrottle = cmdThrottle,
                                    equivRatio = equivRatio,
                                    baro = baro,
                                    relThrottle = relThrottle,
                                    timing = timing,
                                    catB1S1 = catB1S1,
                                    catB1S2 = catB1S2,
                                    voltage = voltage,
                                    evapPurge = evaporation
                                )
                                database.syncQueueDao().insert(syncEntry)
                            } catch (e: Exception) {
                                Log.e("AutoKITT", "Failed to persist loop data", e)
                            }
                        }
                    }
                    
                    loopCount++
                }

            } catch (e: IOException) {
                Log.e("AutoKITT", "Connection failed", e)
                updateNotification("Connection lost. Retrying in 5s...")
                cleanup()
                delay(5000) // Retry delay
            }
        }
    }

    private fun sendCommand(cmd: String) {
        try {
            outputStream?.write((cmd + "\r").toByteArray())
            outputStream?.flush()
            // Read response (basic implementation)
           readResponse() 
        } catch (e: Exception) {
            Log.e("AutoKITT", "Send failed", e)
        }
    }
    
    // Reads until '>' prompt or timeout
    private fun readResponse(): String {
        val buffer = ByteArray(1024)
        val sb = StringBuilder()
        val timeoutMs = 400 // Reduced from 1000ms to keep the loop snappy
        val startTime = System.currentTimeMillis()

        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                 if (inputStream?.available()!! > 0) {
                     val bytes = inputStream?.read(buffer)
                     if (bytes != null && bytes > 0) {
                         val chunk = String(buffer, 0, bytes)
                         sb.append(chunk)
                         if (chunk.contains(">")) { // Found prompt!
                             break
                         }
                     }
                 } else {
                     Thread.sleep(10) // Slightly longer sleep to reduce CPU usage
                 }
            }
        } catch (e: Exception) {
            Log.e("AutoKITT", "Read failed", e)
        }
        
        val result = sb.toString()
        // Log.d("AutoKITT", "Raw Read: $result") // Uncomment for deep debugging if needed
        return result
    }

    private fun pollPID(pid: String): Double {
        try {
            outputStream?.write((pid + "\r").toByteArray())
            outputStream?.flush()
            // Add small delay to allow ELM to process before we check available()
            Thread.sleep(50) 
            val response = readResponse()
            
            // Debug log to see what's happening
            if (response.isEmpty()) {
                 Log.w("AutoKITT", "No response for PID: $pid")
            } else if (!response.contains("41")) {
                 // Log.d("AutoKITT", "PID $pid Raw: $response")
            }
            
            return OBDParser.parse(pid, response)
        } catch (e: Exception) {
            return -1.0
        }
    }

    private fun pollPIDSafe(pid: String): Double {
        var value = pollPID(pid)
        if (value == -1.0) {
            // Retry once immediately
            value = pollPID(pid)
        }
        return value
    }



    private fun cleanup() {
        sendBroadcast(Intent(ACTION_OBD_DISCONNECTED))
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {}
        bluetoothSocket = null
    }

    override fun onDestroy() {
        isRunning = false
        syncManager?.stop()
        behaviorAnalyzer?.close()
        faultAnalyzer?.close()
        cleanup()

        val prefs = getSharedPreferences("AutoKITT_Prefs", Context.MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest_mode", false)
        if (isGuest && sessionId > 0) {
            Log.d("AutoKITT", "Guest session ended. Purging recorded data for sessionId: $sessionId")
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    database.sensorDataDao().deleteBySessionId(sessionId)
                    database.alertLogDao().deleteAlertsSince(sessionId)
                } catch (e: Exception) {
                    Log.e("AutoKITT", "Failed to purge guest data", e)
                }
            }
        }

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Low priority channel for ongoing foreground service
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "OBD Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)
            
            // High priority channel for alerts with custom sound
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Predictive Fault Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.alert_beep}")
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoKITT OBD Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Use default icon for POC
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendFaultNotification(reason: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.alert_beep}")
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Predictive Fault Detected")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .build()
            
        manager.notify("FAULT_ALERT", 2, notification)
    }

    private fun sendSystemNotification(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.alert_beep}")
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .build()
            
        manager.notify(title.hashCode(), notification)

        // Also persist to DB for My Driving Errors screen
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val log = com.example.autokitt.database.AlertLog(
                timestamp = System.currentTimeMillis(),
                alertType = "DRIVER_TIP",
                explanationText = "$title: $message",
                jsonPayload = ""
            )
            database.alertLogDao().insert(log)
        }
    }

    private fun broadcastData(rpm: Double, speed: Double, load: Double, temp: Double, intake: Double, voltage: Double, stft: Double) {
        val intent = Intent(ACTION_OBD_DATA).apply {
            putExtra(EXTRA_RPM, rpm)
            putExtra(EXTRA_SPEED, speed)
            putExtra(EXTRA_LOAD, load)
            putExtra(EXTRA_TEMP, temp)
            putExtra(EXTRA_INTAKE, intake)
            putExtra(EXTRA_VOLTAGE, voltage)
            putExtra(EXTRA_STFT, stft)
        }
        sendBroadcast(intent)
    }
}
