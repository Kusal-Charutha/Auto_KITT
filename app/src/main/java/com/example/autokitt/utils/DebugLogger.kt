package com.example.autokitt.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val LOG_FILE_NAME = "debug_log.txt"

    /**
     * Appends a log message to the internal debug_log.txt file.
     * REMOVE before final production release.
     */
    fun log(context: Context, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val outputStream = FileOutputStream(logFile, true)
            outputStream.write(logEntry.toByteArray())
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun clear(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
