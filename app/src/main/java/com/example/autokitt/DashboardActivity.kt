package com.example.autokitt

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import android.content.res.ColorStateList
import android.view.View
import android.media.MediaPlayer

class DashboardActivity : ComponentActivity() {

    private lateinit var tvRpm: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvLoad: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var viewStatusDot: View
    private lateinit var tvDrivingStatus: TextView
    // private late init var tvVehicleHealth: TextView
    private lateinit var tvVehicleModel: TextView
    
    private var lastAlertTime: Long = 0L

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                OBDForegroundService.ACTION_OBD_DATA -> {
                    val rpm = intent.getDoubleExtra(OBDForegroundService.EXTRA_RPM, 0.0)
                    val speed = intent.getDoubleExtra(OBDForegroundService.EXTRA_SPEED, 0.0)
                    val load = intent.getDoubleExtra(OBDForegroundService.EXTRA_LOAD, 0.0)
                    val temp = intent.getDoubleExtra(OBDForegroundService.EXTRA_TEMP, 0.0)

                    tvRpm.text = String.format("%.0f", rpm)
                    tvSpeed.text = String.format("%.0f", speed)
                    tvLoad.text = String.format("%.1f%%", load)
                    tvTemp.text = String.format("%.0f", temp)
                    tvStatus.text = "Connected - Streaming Data"
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success_green))
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success_green))
                }
                OBDForegroundService.ACTION_OBD_CONNECTED -> {
                    tvStatus.text = "Connected!"
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.blue_primary))
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blue_primary))
                }
                OBDForegroundService.ACTION_OBD_DISCONNECTED -> {
                    tvStatus.text = "Disconnected"
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_red))
                }
                OBDForegroundService.ACTION_BEHAVIOR_ALERT -> {
                    val isAggressive = intent.getBooleanExtra(OBDForegroundService.EXTRA_IS_AGGRESSIVE, false)
                    val probability = intent.getFloatExtra(OBDForegroundService.EXTRA_BEHAVIOR_PROBABILITY, 0.0f)

                    if (isAggressive) {
                        //tvDrivingStatus.text = String.format("Aggressive (%.2f)", probability)
                        tvDrivingStatus.text = String.format("Aggressive (%.2f%%)", (100 - (probability * 100)))
                        tvDrivingStatus.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAlertTime > 5000) {
                            try {
                                val mediaPlayer = MediaPlayer.create(context, R.raw.alert_beep)
                                mediaPlayer?.start()
                                mediaPlayer?.setOnCompletionListener { mp -> mp.release() }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            lastAlertTime = currentTime
                        }

                        val explanation = intent.getStringExtra(OBDForegroundService.EXTRA_EXPLANATION_TEXT)
                        if (explanation != null) {
                            Snackbar.make(findViewById(android.R.id.content), "⚠️ $explanation", Snackbar.LENGTH_LONG)
                                .setBackgroundTint(ContextCompat.getColor(context, R.color.danger_red))
                                .show()
                        }
                    } else {
                        //tvDrivingStatus.text = String.format("Good (%.2f)", probability)
                        tvDrivingStatus.text = String.format("Good (%.2f%%)", (100 - (probability * 100)))
                        tvDrivingStatus.setTextColor(ContextCompat.getColor(context, R.color.success_green))
                    }
                }
                OBDForegroundService.ACTION_VEHICLE_FAULT_ALERT -> {
                    val status = intent.getStringExtra(OBDForegroundService.EXTRA_FAULT_STATUS) ?: "Good"
                    val explanation = intent.getStringExtra(OBDForegroundService.EXTRA_EXPLANATION_TEXT)

                    // tvVehicleHealth.text = status
                    if (status == "Good") {
                        // tvVehicleHealth.setTextColor(ContextCompat.getColor(context, R.color.success_green))
                    } else {
                        val color = if (status == "Faulty") R.color.danger_red else R.color.warning_yellow
                        // tvVehicleHealth.setTextColor(ContextCompat.getColor(context, color))

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAlertTime > 5000) {
                            try {
                                val mediaPlayer = MediaPlayer.create(context, R.raw.alert_beep)
                                mediaPlayer?.start()
                                mediaPlayer?.setOnCompletionListener { mp -> mp.release() }
                            } catch (e: Exception) { e.printStackTrace() }
                            lastAlertTime = currentTime
                        }

                        if (explanation != null) {
                            Snackbar.make(findViewById(android.R.id.content), "🚨 $explanation", Snackbar.LENGTH_LONG)
                                .setBackgroundTint(ContextCompat.getColor(context, color))
                                .show()
                        }
                    }
                }
                OBDForegroundService.ACTION_VEHICLE_INFO_UPDATE -> {
                    val vehicleName = intent.getStringExtra(OBDForegroundService.EXTRA_VEHICLE_NAME)
                    if (!vehicleName.isNullOrEmpty()) {
                        tvVehicleModel.text = "Vehicle: $vehicleName"
                        tvVehicleModel.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvRpm = findViewById(R.id.tvRpm)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvLoad = findViewById(R.id.tvLoad)
        tvTemp = findViewById(R.id.tvTemp)
        tvStatus = findViewById(R.id.tvStatus)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvDrivingStatus = findViewById(R.id.tvDrivingStatus)
        // tvVehicleHealth = findViewById(R.id.tvVehicleHealth)
        tvVehicleModel = findViewById(R.id.tvVehicleModel)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(OBDForegroundService.ACTION_OBD_DATA)
            addAction(OBDForegroundService.ACTION_OBD_CONNECTED)
            addAction(OBDForegroundService.ACTION_OBD_DISCONNECTED)
            addAction(OBDForegroundService.ACTION_BEHAVIOR_ALERT)
            addAction(OBDForegroundService.ACTION_VEHICLE_FAULT_ALERT)
            addAction(OBDForegroundService.ACTION_VEHICLE_INFO_UPDATE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dataReceiver, filter, 2) // 2 = RECEIVER_NOT_EXPORTED
        } else {
            registerReceiver(dataReceiver, filter)
        }

    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(dataReceiver)
    }
}
