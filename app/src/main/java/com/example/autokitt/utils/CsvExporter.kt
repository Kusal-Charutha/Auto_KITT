package com.example.autokitt.utils

import com.example.autokitt.database.SensorData
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun export(dataList: List<SensorData>, outputStream: OutputStream) {
        val writer = outputStream.bufferedWriter()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        try {
            // Write Header
            writer.write("DeviceID,SessionID,Timestamp,Date,EngineRPM,VehicleSpeed,EngineLoad," +
                    "ThrottlePos,CoolantTemp,IntakeTemp,MAF,FuelRate,EngineRunTime,LTFT1,STFT1," +
                    "IntakeManifoldPressure,FuelTankLevel,AbsoluteThrottleB,PedalD,PedalE," +
                    "CommandedThrottleActuator,FuelAirCommandedEquivRatio,AbsBarometricPressure," +
                    "RelativeThrottlePos,TimingAdvance,CatTempB1S1,CatTempB1S2,ControlModuleVoltage,CommandedEvapPurge\n")

            // Write Data
            for (data in dataList) {
                val dateStr = dateFormat.format(Date(data.timestamp))
                val line = StringBuilder()
                    .append("device_autokitt_primary,")
                    .append("${data.sessionId},")
                    .append("${data.timestamp},")
                    .append("$dateStr,")
                    .append("${data.engineRpm},")
                    .append("${data.vehicleSpeed},")
                    .append("${data.engineLoad},")
                    .append("${data.throttlePos},")
                    .append("${data.coolantTemp},")
                    .append("${data.intakeTemp},")
                    .append("${data.maf},")
                    .append("${data.fuelRate},")
                    .append("${data.engineRunTime},")
                    .append("${data.longTermFuelTrim1},")
                    .append("${data.shortTermFuelTrim1},")
                    .append("${data.intakeManifoldPressure},")
                    .append("${data.fuelTankLevel},")
                    .append("${data.absoluteThrottleB},")
                    .append("${data.pedalD},")
                    .append("${data.pedalE},")
                    .append("${data.commandedThrottleActuator},")
                    .append("${data.fuelAirCommandedEquivRatio},")
                    .append("${data.absBarometricPressure},")
                    .append("${data.relativeThrottlePos},")
                    .append("${data.timingAdvance},")
                    .append("${data.catTempB1S1},")
                    .append("${data.catTempB1S2},")
                    .append("${data.controlModuleVoltage},")
                    .append("${data.commandedEvapPurge}\n")
                    .toString()
                writer.write(line)
            }
        } finally {
            writer.flush()
            writer.close()
        }
    }
}
