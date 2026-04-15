package com.example.autokitt

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.autokitt.database.AppDatabase
import com.example.autokitt.utils.CsvExporter
import kotlinx.coroutines.*
import java.util.Calendar

class ExportActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private var exportStart: Long = 0
    private var exportEnd: Long = 0
    private var selectedOptionName: String = ""
    
    private lateinit var rgExportOptions: RadioGroup
    private lateinit var btnExportAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)
        
        database = AppDatabase.getDatabase(this)
        
        rgExportOptions = findViewById(R.id.rgExportOptions)
        btnExportAction = findViewById(R.id.btnExportAction)

        btnExportAction.setOnClickListener {
            val selectedId = rgExportOptions.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val radioButton = findViewById<RadioButton>(selectedId)
            selectedOptionName = radioButton.text.toString()
            
            calculateRange(selectedOptionName)
            
            val fileName = "AutoKITT_Export_${selectedOptionName.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
            createDocumentLauncher.launch(fileName)
        }
    }
    

    private fun calculateRange(option: String) {
        val calendar = Calendar.getInstance()
        exportEnd = System.currentTimeMillis()
        
        // Reset time to midnight for cleaner start times
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (option) {
            "Today" -> {
                exportStart = calendar.timeInMillis
            }
            "This Week" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                exportStart = calendar.timeInMillis
            }
            "Last Driving session", "All time" -> {
                 exportStart = 0
            }
        }
    }
    
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { performExport(it) }
    }

    private fun performExport(uri: Uri) {
        Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch Data
                val data = when (selectedOptionName) {
                    "Last Driving session" -> {
                         val lastSessionId = database.sensorDataDao().getLastSessionId()
                         if (lastSessionId != null) {
                             database.sensorDataDao().getSessionData(lastSessionId)
                         } else {
                             emptyList()
                         }
                    }
                    "All time" -> {
                         database.sensorDataDao().getDataBetween(0, System.currentTimeMillis())
                    }
                    else -> {
                         database.sensorDataDao().getDataBetween(exportStart, exportEnd)
                    }
                }

                if (data.isEmpty()) {
                     withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExportActivity, "No data found for selected range", Toast.LENGTH_LONG).show()
                     }
                     return@launch
                }

                // Write to CSV
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    CsvExporter.export(data, outputStream)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExportActivity, "Export Successful!", Toast.LENGTH_LONG).show()
                    finish() // Close activity after successful export
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExportActivity, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
