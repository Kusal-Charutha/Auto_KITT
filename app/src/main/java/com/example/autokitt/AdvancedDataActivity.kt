package com.example.autokitt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.ArrayList

class AdvancedDataActivity : AppCompatActivity() {

    private lateinit var chartRpm: LineChart
    private lateinit var chartLoad: LineChart
    private lateinit var chartCoolant: LineChart
    private lateinit var chartIntake: LineChart
    private lateinit var chartVoltage: LineChart
    private lateinit var chartStft: LineChart

    private val obdDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OBDForegroundService.ACTION_OBD_DATA) {
                val rpm = intent.getDoubleExtra(OBDForegroundService.EXTRA_RPM, -1.0)
                val load = intent.getDoubleExtra(OBDForegroundService.EXTRA_LOAD, -1.0)
                val coolant = intent.getDoubleExtra(OBDForegroundService.EXTRA_TEMP, -1.0)
                val intake = intent.getDoubleExtra(OBDForegroundService.EXTRA_INTAKE, -1.0)
                val voltage = intent.getDoubleExtra(OBDForegroundService.EXTRA_VOLTAGE, -1.0)
                val stft = intent.getDoubleExtra(OBDForegroundService.EXTRA_STFT, -1.0)

                if (rpm != -1.0) addEntry(chartRpm, rpm.toFloat())
                if (load != -1.0) addEntry(chartLoad, load.toFloat())
                if (coolant != -1.0) addEntry(chartCoolant, coolant.toFloat())
                if (intake != -1.0) addEntry(chartIntake, intake.toFloat())
                if (voltage != -1.0) addEntry(chartVoltage, voltage.toFloat())
                if (stft != -1.0) addEntry(chartStft, stft.toFloat())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_data)

        chartRpm = findViewById(R.id.chartRpm)
        chartLoad = findViewById(R.id.chartLoad)
        chartCoolant = findViewById(R.id.chartCoolant)
        chartIntake = findViewById(R.id.chartIntake)
        chartVoltage = findViewById(R.id.chartVoltage)
        chartStft = findViewById(R.id.chartStft)

        setupChart(chartRpm, "RPM", Color.BLUE)
        setupChart(chartLoad, "Load %", Color.MAGENTA)
        setupChart(chartCoolant, "Coolant Temp (C)", Color.RED)
        setupChart(chartIntake, "Intake Temp (C)", Color.CYAN)
        setupChart(chartVoltage, "Voltage (V)", Color.GREEN)
        setupChart(chartStft, "STFT %", Color.BLUE)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(OBDForegroundService.ACTION_OBD_DATA)
        ContextCompat.registerReceiver(
            this,
            obdDataReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(obdDataReceiver)
    }

    private fun setupChart(chart: LineChart, label: String, color: Int) {
        val dataSet = LineDataSet(ArrayList<Entry>(), label)
        dataSet.color = color
        dataSet.valueTextColor = Color.BLACK
        dataSet.lineWidth = 2f
        dataSet.setDrawCircles(true)
        dataSet.setCircleColor(color)
        dataSet.circleRadius = 4f
        dataSet.mode = LineDataSet.Mode.LINEAR

        val lineData = LineData(dataSet)
        chart.data = lineData
        
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisRight.isEnabled = false
        
        chart.invalidate()
    }

    private fun addEntry(chart: LineChart, value: Float) {
        val data = chart.data
        if (data != null) {
            var set = data.getDataSetByIndex(0)
            if (set == null) {
                set = LineDataSet(null, "Data")
                data.addDataSet(set)
            }
            data.addEntry(Entry(set.entryCount.toFloat(), value), 0)
            data.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.setVisibleXRangeMaximum(50f)
            chart.moveViewToX(data.entryCount.toFloat())
        }
    }
}
