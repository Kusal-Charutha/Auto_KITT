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

                tvTime.text = sdf.format(Date(alert.timestamp))
                tvText.text = alert.explanationText


                // Simple neutral card background for all alerts
                card.setBackgroundResource(R.drawable.bg_card_outline_light_gray)

                llBehaviorHistory.addView(card)
            }
        }
    }
}
