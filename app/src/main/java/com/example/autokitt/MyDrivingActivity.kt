package com.example.autokitt

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.autokitt.database.AppDatabase
import java.util.Calendar
import java.util.Locale

class MyDrivingActivity : AppCompatActivity() {

    private lateinit var btnLastTrip: Button
    private lateinit var btnToday: Button
    private lateinit var btnAllTime: Button
    
    private lateinit var tvScaleTitle: TextView
    private lateinit var vScalePointer: android.view.View
    private lateinit var db: AppDatabase
    
    // Stats views
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvIdleTime: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvHealthRate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_driving)

        btnLastTrip = findViewById(R.id.btnLastTrip)
        btnToday = findViewById(R.id.btnToday)
        btnAllTime = findViewById(R.id.btnAllTime)
        
        tvScaleTitle = findViewById(R.id.tvScaleTitle)
        vScalePointer = findViewById(R.id.vScalePointer)
        db = AppDatabase.getDatabase(this)

        tvTotalDistance = findViewById(R.id.tvTotalDistance)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvIdleTime = findViewById(R.id.tvIdleTime)
        tvMaxSpeed = findViewById(R.id.tvMaxSpeed)
        tvAvgSpeed = findViewById(R.id.tvAvgSpeed)
        tvHealthRate = findViewById(R.id.tvHealthRate)

        btnLastTrip.setOnClickListener { updateTab("LastTrip") }
        btnToday.setOnClickListener { updateTab("Today") }
        btnAllTime.setOnClickListener { updateTab("AllTime") }
        
        val btnDrivingErrors = findViewById<Button>(R.id.btnDrivingErrors)
        btnDrivingErrors.setOnClickListener {
            startActivity(Intent(this, MyDrivingErrorsActivity::class.java))
        }
        
        // Default
        updateTab("LastTrip")
    }

    private fun updateTab(tab: String) {
        val transparent = Color.TRANSPARENT
        val black = Color.BLACK
        val white = Color.WHITE
        val blue = ContextCompat.getColor(this, R.color.blue_primary)

        btnLastTrip.backgroundTintList = ColorStateList.valueOf(transparent)
        btnLastTrip.setTextColor(black)
        
        btnToday.backgroundTintList = ColorStateList.valueOf(transparent)
        btnToday.setTextColor(black)
        
        btnAllTime.backgroundTintList = ColorStateList.valueOf(transparent)
        btnAllTime.setTextColor(black)

        // Highlight selected
        when(tab) {
            "LastTrip" -> {
                btnLastTrip.background = ContextCompat.getDrawable(this, R.drawable.bg_button_white_pill)
                btnLastTrip.backgroundTintList = ColorStateList.valueOf(white)
                btnLastTrip.setTextColor(black) // In design it's white bg, black text
            }
            "Today" -> {
                btnToday.background = ContextCompat.getDrawable(this, R.drawable.bg_button_white_pill)
                btnToday.backgroundTintList = ColorStateList.valueOf(white)
                btnToday.setTextColor(black)
            }
            "AllTime" -> {
                btnAllTime.background = ContextCompat.getDrawable(this, R.drawable.bg_button_white_pill)
                btnAllTime.backgroundTintList = ColorStateList.valueOf(white)
                btnAllTime.setTextColor(black)
            }
        }
        
        calculateHealthScore(tab)
    }

    private fun calculateHealthScore(tab: String) {
        lifecycleScope.launch {
            tvScaleTitle.text = "Driving Health Score: Calculating..."
            tvTotalDistance.text = "..."
            tvTotalTime.text = "..."
            tvIdleTime.text = "..."
            tvMaxSpeed.text = "..."
            tvAvgSpeed.text = "..."
            
            var startTime: Long = 0
            val endTime = System.currentTimeMillis()
            var isLastTrip = false
            var lastSessionId: Long? = null
            
            withContext(Dispatchers.IO) {
                when(tab) {
                    "LastTrip" -> {
                        isLastTrip = true
                        lastSessionId = db.sensorDataDao().getLastSessionId()
                        if (lastSessionId != null) {
                            val start = db.sensorDataDao().getSessionStartTime(lastSessionId!!)
                            startTime = start ?: 0
                        }
                    }
                    "Today" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startTime = calendar.timeInMillis
                    }
                    "AllTime" -> {
                        startTime = 0
                    }
                }
                
                // Use Total Connected Time for "Total Time" rather than active engine ticks
                val connectedTimeMs = if (isLastTrip && lastSessionId != null) {
                    db.sensorDataDao().getTotalConnectedTimeForSession(lastSessionId!!) ?: 0L
                } else {
                    db.sensorDataDao().getTotalConnectedTime(startTime, endTime) ?: 0L
                }
                val totalDrivingTimeSecs = (connectedTimeMs / 1000).toInt()
                
                val idleSeconds = if (isLastTrip && lastSessionId != null) {
                    db.sensorDataDao().countIdleTimeForSession(lastSessionId!!)
                } else {
                    db.sensorDataDao().countIdleTime(startTime, endTime)
                }

                val maxSpeed = if (isLastTrip && lastSessionId != null) {
                    db.sensorDataDao().getMaxSpeedForSession(lastSessionId!!) ?: 0f
                } else {
                    db.sensorDataDao().getMaxSpeed(startTime, endTime) ?: 0f
                }

                val avgSpeed = if (isLastTrip && lastSessionId != null) {
                    db.sensorDataDao().getAvgSpeedForSession(lastSessionId!!) ?: 0f
                } else {
                    db.sensorDataDao().getAvgSpeed(startTime, endTime) ?: 0f
                }
                
                // Derived Math: Distance = (Time in Hours) * AvgSpeed
                // Using total connected time or actual moving time? Let's keep moving time for distance.
                val movingSeconds = if (isLastTrip && lastSessionId != null) {
                    db.sensorDataDao().countDrivingTimeForSession(lastSessionId!!)
                } else {
                    db.sensorDataDao().countDrivingTime(startTime, endTime)
                }
                val drivingHours = movingSeconds / 3600.0
                val totalDistanceKm = drivingHours * avgSpeed
                
                // New Health score Logic: 
                // totalDetections = "total aggressive and good detection" (every time ML ran)
                val totalDetections = if (isLastTrip && lastSessionId != null) {
                    db.sensorDataDao().countTotalDetectionsForSession(lastSessionId!!)
                } else {
                    db.sensorDataDao().countTotalDetections(startTime, endTime)
                }
                
                // aggressiveAlerts = "total aggressive detections"
                val aggressiveAlerts = db.alertLogDao().countBehaviorAlerts(startTime, endTime)
                
                withContext(Dispatchers.Main) {
                    // Inject real mathematics to our view
                    tvTotalDistance.text = String.format(Locale.getDefault(), "%.1f km", totalDistanceKm)
                    tvTotalTime.text = formatSeconds(totalDrivingTimeSecs) // Updated
                    tvIdleTime.text = formatSeconds(idleSeconds)
                    tvMaxSpeed.text = String.format(Locale.getDefault(), "%.0f km/h", maxSpeed)
                    tvAvgSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", avgSpeed)

                    if (totalDetections <= 0) {
                        tvScaleTitle.text = "Driving Health Score: Insufficient Data"
                        tvHealthRate.text = "N/A"
                        // Center pointer
                        vScalePointer.layoutParams = (vScalePointer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).apply {
                            horizontalBias = 0.5f
                        }
                    } else {
                        // Healthy Driving Rate = (Total - Aggressive) / Total
                        val goodDetections = (totalDetections - aggressiveAlerts).coerceAtLeast(0)
                        val healthyRatio = goodDetections.toFloat() / totalDetections.toFloat()
                        val healthyPercentage = (healthyRatio * 100).toInt()
                        
                        tvScaleTitle.text = "Driving Health Score"
                        tvHealthRate.text = "$healthyPercentage%"
                        
                        // Set pointer location on scale
                        vScalePointer.layoutParams = (vScalePointer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).apply {
                            horizontalBias = healthyRatio
                        }
                    }
                }
            }
        }
    }
    
    private fun formatSeconds(totalSeconds: Int): String {
        if (totalSeconds == 0) return "0m"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
