package com.example.autokitt.utils

object DtcDictionary {
    private val dict = mapOf(
        "P0100" to "Mass or Volume Air Flow A Circuit",
        "P0101" to "Mass or Volume Air Flow A Circuit Range/Performance",
        "P0102" to "Mass or Volume Air Flow A Circuit Low",
        "P0104" to "Mass or Volume Air Flow A Circuit Intermittent",
        "P0110" to "Intake Air Temperature Sensor 1 Circuit",
        "P0113" to "Intake Air Temperature Sensor 1 Circuit High",
        "P0115" to "Engine Coolant Temperature Sensor 1 Circuit",
        "P0117" to "Engine Coolant Temperature Sensor 1 Circuit Low",
        "P0118" to "Engine Coolant Temperature Sensor 1 Circuit High",
        "P0120" to "Throttle/Pedal Position Sensor A Circuit",
        "P0122" to "Throttle/Pedal Position Sensor A Circuit Low",
        "P0128" to "Coolant Thermostat (Coolant Temperature Below Thermostat Regulating Temperature)",
        "P0130" to "O2 Sensor Circuit (Bank 1 Sensor 1)",
        "P0133" to "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)",
        "P0135" to "O2 Sensor Heater Circuit (Bank 1 Sensor 1)",
        "P0171" to "System Too Lean (Bank 1)",
        "P0172" to "System Too Rich (Bank 1)",
        "P0200" to "Injector Circuit/Open",
        "P0300" to "Random/Multiple Cylinder Misfire Detected",
        "P0301" to "Cylinder 1 Misfire Detected",
        "P0302" to "Cylinder 2 Misfire Detected",
        "P0303" to "Cylinder 3 Misfire Detected",
        "P0304" to "Cylinder 4 Misfire Detected",
        "P0325" to "Knock Sensor 1 Circuit (Bank 1 or Single Sensor)",
        "P0335" to "Crankshaft Position Sensor A Circuit",
        "P0340" to "Camshaft Position Sensor A Circuit (Bank 1 or Single Sensor)",
        "P0400" to "Exhaust Gas Recirculation Flow",
        "P0401" to "Exhaust Gas Recirculation Flow Insufficient Detected",
        "P0420" to "Catalyst System Efficiency Below Threshold (Bank 1)",
        "P0430" to "Catalyst System Efficiency Below Threshold (Bank 2)",
        "P0440" to "Evaporative Emission System",
        "P0442" to "Evaporative Emission System Leak Detected (small leak)",
        "P0455" to "Evaporative Emission System Leak Detected (large leak)",
        "P0500" to "Vehicle Speed Sensor A",
        "P0600" to "Serial Communication Link",
        "P0700" to "Transmission Control System (MIL Request)",
        "U0100" to "Lost Communication With ECM/PCM A"
    )

    fun getDescription(code: String): String {
        return dict[code] ?: "Unknown physical diagnostic code: $code"
    }
}
