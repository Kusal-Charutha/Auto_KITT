package com.example.autokitt.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.autokitt.database.AppDatabase
import com.example.autokitt.database.SyncQueueEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SyncManager(private val context: Context) {

    private val TAG = "SyncManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val database = AppDatabase.getDatabase(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isSyncing = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available — triggering sync")
            triggerSync()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Also try an immediate sync in case we're already online
        if (isOnline()) {
            triggerSync()
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Callback already unregistered", e)
        }
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun triggerSync() {
        if (isSyncing) return
        scope.launch {
            isSyncing = true
            try {
                syncLoop()
            } finally {
                isSyncing = false
            }
        }
    }

    private suspend fun syncLoop() {
        val dao = database.syncQueueDao()
        val api = ApiClient.instance

        while (true) {
            val batch = dao.getUnsynced(50)
            if (batch.isEmpty()) {
                Log.d(TAG, "Sync queue empty — done")
                break
            }

            Log.d(TAG, "Syncing batch of ${batch.size} records...")

            val payloads = batch.map { it.toPayload() }

            try {
                val response = api.postSensorDataBatch(payloads)
                if (response.isSuccessful) {
                    val ids = batch.map { it.id }
                    dao.deleteByIds(ids)
                    Log.d(TAG, "Synced and deleted ${ids.size} records")
                } else {
                    Log.e(TAG, "Batch upload failed: HTTP ${response.code()}")
                    break // Stop retrying on server error, will retry on next network event
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed (will retry when online)", e)
                break
            }
        }

        val remaining = dao.getPendingCount()
        if (remaining > 0) {
            Log.d(TAG, "$remaining records still pending in sync queue")
        }
    }

    private fun SyncQueueEntry.toPayload(): SensorDataPayload {
        return SensorDataPayload(
            userId = userId,
            userName = userName,
            deviceId = deviceId,
            sessionId = sessionId,
            timestamp = timestamp,
            date = date,
            engineRpm = engineRpm,
            vehicleSpeed = vehicleSpeed,
            throttle = throttle,
            engineLoad = engineLoad,
            coolantTemp = coolantTemp,
            intakeTemp = intakeTemp,
            maf = maf,
            fuelRate = fuelRate,
            runTime = runTime,
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
            evapPurge = evapPurge
        )
    }
}
