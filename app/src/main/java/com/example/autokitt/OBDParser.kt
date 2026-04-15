package com.example.autokitt

object OBDParser {
    fun parse(cmd: String, rawResponse: String): Double {
        // Basic cleanup: remove spaces, newlines, and characters like '>'
        val cleaned = rawResponse.replace("\\s".toRegex(), "").replace(">", "").trim()

        // Check for common error responses or no data
        if (cleaned.contains("NODATA") || cleaned.contains("STOPPED") || cleaned.contains("ERROR") || cleaned.contains("UNABLE")) {
             return -1.0 
        }

        // If the response doesn't start with 41 (response to 01), it might be an error or NO DATE
        // Also check if it's empty
        if (!cleaned.startsWith("41") || cleaned.length < 4) {
             if (cleaned.isNotEmpty()) {
                 android.util.Log.w("AutoKITT", "Ignored unexpected response for $cmd: $rawResponse")
             }
             return -1.0
        }

        // Parse based on PID
        return when (cmd) {
            "010C" -> parseRPM(cleaned)
            "010D" -> parseSpeed(cleaned)
            "0104" -> parseLoad(cleaned)
            "0111" -> parseThrottle(cleaned)
            "0105" -> parseTemp(cleaned) // Coolant
            "010F" -> parseTemp(cleaned) // Intake
            "0110" -> parseMAF(cleaned)
            "015E" -> parseFuelRate(cleaned)
            "011F" -> parseRunTime(cleaned)
            "0106" -> parseTrim(cleaned) // STFT
            "0107" -> parseTrim(cleaned) // LTFT
            "010B" -> parsePressure(cleaned) // MAP
            "012F" -> parsePercent(cleaned) // Fuel Level
            "0147" -> parsePercent(cleaned) // Abs Throttle B
            "0149" -> parsePercent(cleaned) // Pedal D
            "014A" -> parsePercent(cleaned) // Pedal E
            "014C" -> parsePercent(cleaned) // Cmd Throttle Actuator
            "0144" -> parseEquivRatio(cleaned) // Equiv Ratio
            "0133" -> parsePressure(cleaned) // Barometric
            "0145" -> parsePercent(cleaned) // Rel Throttle Pos
            "010E" -> parseTiming(cleaned) // Timing Advance
            "013C" -> parseCatTemp(cleaned) // Cat B1S1
            "013D" -> parseCatTemp(cleaned) // Cat B1S2
            "0142" -> parseVoltage(cleaned) // Voltage
            "012E" -> parsePercent(cleaned) // Evap Purge
            "0130" -> parseCount(cleaned)   // Warmups
            else -> -1.0
        }
    }

    /**
     * RPM (01 0C)
     * Response: 41 0C A B
     * Formula: ((A * 256) + B) / 4
     */
    private fun parseRPM(hex: String): Double {
        // hex string "410CAB..." 
        // 0-1: 41
        // 2-3: 0C
        // 4-5: A
        // 6-7: B
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return ((a * 256) + b) / 4.0
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }

    /**
     * Speed (01 0D)
     * Response: 41 0D A
     * Formula: A (km/h)
     */
    private fun parseSpeed(hex: String): Double {
        // hex string "410DA..."
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return a.toDouble()
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }

    /**
     * Engine Load (01 04)
     * Response: 41 04 A
     * Formula: A / 2.55 (percent)
     */
    private fun parseLoad(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return a / 2.55
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }


    /**
     * Throttle Position (01 11)
     * Response: 41 11 A
     * Formula: A * 100 / 255
     */
    private fun parseThrottle(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return (a * 100.0) / 255.0
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }

    /**
     * Coolant Temperature (01 05)
     * Response: 41 05 A
     * Formula: A - 40
     */
    private fun parseTemp(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return (a - 40).toDouble()
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }

    /**
     * MAF Air Flow Rate (01 10)
     * Response: 41 10 A B
     * Formula: ((A * 256) + B) / 100
     */
    private fun parseMAF(hex: String): Double {
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return ((a * 256) + b) / 100.0
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }

    /**
     * Fuel Rate (01 5E)
     * Response: 41 5E A B
     * Formula: ((A * 256) + B) / 20
     */
    private fun parseFuelRate(hex: String): Double {
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return ((a * 256) + b) / 20.0
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }

    /**
     * Engine Run Time (01 1F)
     * Response: 41 1F A B
     * Formula: (A * 256) + B
     */
    private fun parseRunTime(hex: String): Double {
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return ((a * 256) + b).toDouble()
        } catch (e: NumberFormatException) {
            return 0.0
        }
    }
    /**
     * Fuel Trim (01 06, 01 07)
     * Formula: (A-128) * 100/128
     */
    private fun parseTrim(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return (a - 128) * 100.0 / 128.0
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Pressure (01 0B, 01 33) - MAP, Barometric
     * Formula: A (kPa)
     */
    private fun parsePressure(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return a.toDouble()
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Percentage (01 2F, 01 47, 01 49, 01 4A, 01 4C, 01 45, 01 2E)
     * Formula: 100 * A / 255
     */
    private fun parsePercent(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return (a * 100.0) / 255.0
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Fuel/Air Commanded Equiv Ratio (01 44)
     * Formula: 2/65536 * (256*A + B)
     */
    private fun parseEquivRatio(hex: String): Double {
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return 2.0 * ((a * 256) + b) / 65536.0
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Timing Advance (01 0E)
     * Formula: (A/2) - 64
     */
    private fun parseTiming(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return (a / 2.0) - 64.0
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Catalyst Temp (01 3C, 01 3D)
     * Formula: ((A*256)+B)/10 - 40
     */
    private fun parseCatTemp(hex: String): Double {
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return (((a * 256) + b) / 10.0) - 40.0
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Control Module Voltage (01 42)
     * Formula: ((A*256)+B)/1000
     */
    private fun parseVoltage(hex: String): Double {
        if (hex.length < 8) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            val b = hex.substring(6, 8).toInt(16)
            return ((a * 256) + b) / 1000.0
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Count (01 30)
     * Formula: A
     */
    private fun parseCount(hex: String): Double {
        if (hex.length < 6) return 0.0
        try {
            val a = hex.substring(4, 6).toInt(16)
            return a.toDouble()
        } catch (e: NumberFormatException) { return 0.0 }
    }

    /**
     * Parse Vehicle Identification Number (09 02)
     * Robust algorithm: extracts all 2-char hex sequences, converts to ASCII,
     * and maps exactly the first 17-character alphanumeric sequence.
     */
    fun parseVIN(rawResponse: String): String? {
        val cleaned = rawResponse.replace(">", "").replace("\r", " ").replace("\n", " ").trim()
        val parts = cleaned.split(" ").filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' } }
        
        val asciiString = parts.mapNotNull { 
            val b = it.toIntOrNull(16)
            if (b != null && b in 32..126) b.toChar() else null
        }.joinToString("")
        
        val regex = Regex("[A-HJ-NPR-Z0-9]{17}", RegexOption.IGNORE_CASE)
        val match = regex.find(asciiString)
        return match?.value?.uppercase()
    }

    /**
     * Parses Mode 03 response (DTCs).
     * Decodes the standard SAE J2012 format bitmask into a String list (e.g. ["P0300"]).
     */
    fun parseDTCs(rawResponse: String): List<String> {
        val dtcList = mutableListOf<String>()
        val lines = rawResponse.split("\r", "\n").map { it.replace("\\s".toRegex(), "").replace(">", "").trim() }
        
        var hexPayload = ""
        var started = false
        for (line in lines) {
            if (line.contains("NODATA") || line.contains("ERROR")) return emptyList()
            var cleanLine = line.replace("^[0-9A-F]:".toRegex(), "")
            if (cleanLine.startsWith("43")) {
                cleanLine = cleanLine.substring(2)
                started = true
            }
            if (started) {
                hexPayload += cleanLine
            }
        }

        val validHex = hexPayload.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
        for (i in 0 until validHex.length - 3 step 4) {
            val A_hex = validHex.substring(i, i + 2)
            val B_hex = validHex.substring(i + 2, i + 4)
            if (A_hex == "00" && B_hex == "00") continue

            val aOrNull = A_hex.toIntOrNull(16)
            val bOrNull = B_hex.toIntOrNull(16)
            if (aOrNull == null || bOrNull == null) continue

            val prefixLookup = arrayOf("P", "C", "B", "U")
            val prefix = prefixLookup[(aOrNull shr 6) and 0b11]

            val digit1 = ((aOrNull shr 4) and 0b11).toString()
            val digit2 = (aOrNull and 0b1111).toString(16).uppercase()
            val digit3 = ((bOrNull shr 4) and 0b1111).toString(16).uppercase()
            val digit4 = (bOrNull and 0b1111).toString(16).uppercase()

            dtcList.add(prefix + digit1 + digit2 + digit3 + digit4)
        }
        return dtcList.distinct()
    }
}
