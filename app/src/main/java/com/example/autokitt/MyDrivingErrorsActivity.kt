package com.example.autokitt

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.autokitt.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.Date
import java.util.Locale

class MyDrivingErrorsActivity : AppCompatActivity() {

    private lateinit var llBehaviorHistory: LinearLayout
    private lateinit var db: AppDatabase
    
    private val behaviorUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OBDForegroundService.ACTION_BEHAVIOR_ALERT) {
                // To prevent reloading for "Good" events, check if it was aggressive
                val isAggressive = intent.getBooleanExtra(OBDForegroundService.EXTRA_IS_AGGRESSIVE, false)
                if (isAggressive) {
                    loadBehaviorHistory()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_driving_errors)

        llBehaviorHistory = findViewById(R.id.llBehaviorHistory)
        db = AppDatabase.getDatabase(this)

        val btnClearDrivingErrors = findViewById<android.widget.Button>(R.id.btnClearDrivingErrors)
        btnClearDrivingErrors.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.alertLogDao().deleteDriverAlerts()
                }
                loadBehaviorHistory()
            }
        }

        loadBehaviorHistory()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(OBDForegroundService.ACTION_BEHAVIOR_ALERT)
        ContextCompat.registerReceiver(
            this,
            behaviorUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loadBehaviorHistory()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(behaviorUpdateReceiver)
    }

    private fun loadBehaviorHistory() {
        lifecycleScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                db.alertLogDao().getDriverAlerts()
            }
            llBehaviorHistory.removeAllViews()

            if (alerts.isEmpty()) {
                val emptyTv = TextView(this@MyDrivingErrorsActivity)
                emptyTv.text = "No driving alerts recorded yet. Keep it up!"
                emptyTv.setTextColor(ContextCompat.getColor(this@MyDrivingErrorsActivity, android.R.color.darker_gray))
                emptyTv.textSize = 14f
                llBehaviorHistory.addView(emptyTv)
                return@launch
            }

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

            alerts.forEach { alert ->
                val card = layoutInflater.inflate(R.layout.item_alert_log, llBehaviorHistory, false)
                val tvTime: TextView = card.findViewById(R.id.tvAlertTime)
                val tvText: TextView = card.findViewById(R.id.tvAlertExplanation)
                val tvAdvice: TextView = card.findViewById(R.id.tvAlertAdvice)
                val tvSeverity: TextView = card.findViewById(R.id.tvAlertSeverity)
                val viewSeverityDot: android.view.View = card.findViewById(R.id.viewSeverityDot)

                tvTime.text = sdf.format(Date(alert.timestamp))
                tvText.text = alert.explanationText

                // Color-code by severity
                val severityColor = when (alert.severity) {
                    "CRITICAL" -> R.color.danger_red
                    "WARNING" -> R.color.warning_yellow
                    "INFO" -> R.color.info_blue
                    else -> R.color.text_secondary
                }
                viewSeverityDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@MyDrivingErrorsActivity, severityColor)
                )

                // Show severity label for BEHAVIOR alerts (not DRIVER_TIP)
                if (alert.alertType == "BEHAVIOR" && alert.severity.isNotBlank()) {
                    tvSeverity.text = alert.severity
                    tvSeverity.setTextColor(ContextCompat.getColor(this@MyDrivingErrorsActivity, severityColor))
                    tvSeverity.visibility = android.view.View.VISIBLE
                }

                // Show advice if available
                if (alert.advice.isNotBlank()) {
                    tvAdvice.text = "\uD83D\uDCA1 ${alert.advice}"
                    tvAdvice.visibility = android.view.View.VISIBLE
                }

                // Severity-based card background
                val bgRes = when (alert.severity) {
                    "CRITICAL" -> R.drawable.bg_card_outline_red
                    "WARNING" -> R.drawable.bg_card_outline_yellow
                    "INFO" -> R.drawable.bg_card_outline_green
                    else -> R.drawable.bg_card_outline_light_gray
                }
                card.setBackgroundResource(bgRes)

                llBehaviorHistory.addView(card)
            }
        }
    }
}
