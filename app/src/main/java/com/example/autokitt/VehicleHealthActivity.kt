package com.example.autokitt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.autokitt.database.AppDatabase
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VehicleHealthActivity : AppCompatActivity() {

    private lateinit var tvRecordedFaults: TextView
    private lateinit var llFaultHistory: LinearLayout
    private lateinit var db: AppDatabase
    private val faultUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OBDForegroundService.ACTION_VEHICLE_FAULT_ALERT) {
                loadFaultHistory()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_health)
        
        tvRecordedFaults = findViewById(R.id.tvRecordedFaults)
        llFaultHistory = findViewById(R.id.llFaultHistory)
        
        db = AppDatabase.getDatabase(this)

        val btnAdvanced = findViewById<Button>(R.id.btnAdvancedData)
        btnAdvanced.setOnClickListener {
            val intent = Intent(this, AdvancedDataActivity::class.java)
            startActivity(intent)
        }

        val btnClearFaults = findViewById<Button>(R.id.btnClearFaults)
        btnClearFaults.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.alertLogDao().deleteAlertsByType("FAULT")
                }
                loadFaultHistory()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(OBDForegroundService.ACTION_VEHICLE_FAULT_ALERT)
        ContextCompat.registerReceiver(
            this,
            faultUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loadFaultHistory()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(faultUpdateReceiver)
    }



    private fun loadFaultHistory() {
        lifecycleScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                db.alertLogDao().getAlertsByType("FAULT")
            }
            llFaultHistory.removeAllViews()
            
            withContext(Dispatchers.Main) {
                tvRecordedFaults.text = "Recorded Faults : ${alerts.size}"
            }
            
            if (alerts.isEmpty()) {
                val emptyTv = TextView(this@VehicleHealthActivity)
                emptyTv.text = "No historical faults recorded yet."
                emptyTv.setTextColor(ContextCompat.getColor(this@VehicleHealthActivity, android.R.color.darker_gray))
                emptyTv.textSize = 14f
                llFaultHistory.addView(emptyTv)
                return@launch
            }

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

            alerts.forEach { alert ->
                val card = layoutInflater.inflate(R.layout.item_alert_log, llFaultHistory, false)
                val tvTime: TextView = card.findViewById(R.id.tvAlertTime)
                val tvText: TextView = card.findViewById(R.id.tvAlertExplanation)
                val tvAdvice: TextView = card.findViewById(R.id.tvAlertAdvice)
                val tvSeverity: TextView = card.findViewById(R.id.tvAlertSeverity)
                val viewSeverityDot: View = card.findViewById(R.id.viewSeverityDot)

                tvTime.text = sdf.format(Date(alert.timestamp))
                tvText.text = alert.explanationText

                // Show severity indicator
                val severityColor = when (alert.severity) {
                    "CRITICAL" -> R.color.danger_red
                    "WARNING" -> R.color.warning_yellow
                    "INFO" -> R.color.info_blue
                    else -> R.color.warning_yellow
                }
                viewSeverityDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@VehicleHealthActivity, severityColor)
                )
                tvSeverity.text = alert.severity
                tvSeverity.setTextColor(ContextCompat.getColor(this@VehicleHealthActivity, severityColor))
                tvSeverity.visibility = View.VISIBLE

                // Show advice if available
                if (alert.advice.isNotBlank()) {
                    tvAdvice.text = "\uD83D\uDCA1 ${alert.advice}"
                    tvAdvice.visibility = View.VISIBLE
                }

                // Severity-based card background
                val bgRes = when (alert.severity) {
                    "CRITICAL" -> R.drawable.bg_card_outline_red
                    "WARNING" -> R.drawable.bg_card_outline_yellow
                    "INFO" -> R.drawable.bg_card_outline_green
                    else -> R.drawable.bg_card_outline_yellow
                }
                card.setBackgroundResource(bgRes)

                llFaultHistory.addView(card)
            }
        }
    }
}
