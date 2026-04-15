package com.example.autokitt.utils

import com.example.autokitt.database.SensorData
import java.io.OutputStream
import java.io.PrintWriter

object CsvExporter {

    private val HEADERS = listOf(
        "Timestamp", "Date", "RPM", "Speed", "Load", "Throttle", "Coolant",
        "RunTime", "IntakePressure", "TimingAdvance", "IntakeTemp", "FuelLevel",
        "BaroPressure", "Voltage", "EquivRatio", "RelThrottle", "AbsThrottleB",
        "PedalD", "PedalE", "CmdThrottle", "LTFT1", "STFT1", "CatB1S1", "CatB1S2",
        "EvapPurge", "Warmups"
    )

    fun export(data: List<SensorData>, outputStream: OutputStream) {
        val writer = PrintWriter(outputStream)
        
        // Write Header
        writer.println(HEADERS.joinToString(","))
        
        // Write Data Rows
        data.forEach { row ->
            val values = listOf(
                row.timestamp.toString(),
                row.date,
                row.engineRpm.toString(),
                row.vehicleSpeed.toString(),
                row.engineLoad.toString(),
                row.throttlePos.toString(),
                row.coolantTemp.toString(),
                row.runTime.toString(),
                row.intakePressure.toString(),
                row.timingAdvance.toString(),
                row.intakeTemp.toString(),
                row.fuelLevel.toString(),
                row.baroPressure.toString(),
                row.controlModuleVoltage.toString(),
                row.equivRatio.toString(),
                row.relativeThrottle.toString(),
                row.absoluteThrottleB.toString(),
                row.pedalD.toString(),
                row.pedalE.toString(),
                row.cmdThrottle.toString(),
                row.ltft1.toString(),
                row.stft1.toString(),
                row.catTempB1S1.toString(),
                row.catTempB1S2.toString(),
                row.evapPurge.toString(),
                row.warmups.toString()
            )
            writer.println(values.joinToString(","))
        }
        
        writer.flush()
    }
}
