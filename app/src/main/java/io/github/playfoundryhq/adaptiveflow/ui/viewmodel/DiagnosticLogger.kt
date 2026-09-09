package io.github.playfoundryhq.adaptiveflow.ui.viewmodel

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

object DiagnosticLogger {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
        val stackTrace: String? = null
    ) {
        fun format(): String {
            val sb = StringBuilder()
            sb.append("[$timestamp] $level/$tag: $message")
            if (!stackTrace.isNullOrEmpty()) {
                sb.append("\n").append(stackTrace)
            }
            return sb.toString()
        }
    }

    private val _inAppLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val inAppLogs = _inAppLogs.asStateFlow()

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        // Send to standard Android Logcat
        when (level.uppercase()) {
            "D" -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            "I" -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            "W" -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            "E" -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            else -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
        }

        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, tag, message, stackTrace)

        synchronized(this) {
            val currentList = _inAppLogs.value.toMutableList()
            currentList.add(entry)
            // Limit to last 200 logs to avoid memory bloat
            if (currentList.size > 200) {
                currentList.removeAt(0)
            }
            _inAppLogs.value = currentList
        }
    }

    fun v(tag: String, message: String) = log("V", tag, message)
    fun v(tag: String, message: String, throwable: Throwable?) = log("V", tag, message, throwable)

    fun d(tag: String, message: String) = log("D", tag, message)
    fun d(tag: String, message: String, throwable: Throwable?) = log("D", tag, message, throwable)

    fun i(tag: String, message: String) = log("I", tag, message)
    fun i(tag: String, message: String, throwable: Throwable?) = log("I", tag, message, throwable)

    fun w(tag: String, message: String) = log("W", tag, message)
    fun w(tag: String, message: String, throwable: Throwable?) = log("W", tag, message, throwable)
    fun w(tag: String, throwable: Throwable) = log("W", tag, throwable.message ?: "", throwable)

    fun e(tag: String, message: String) = log("E", tag, message)
    fun e(tag: String, message: String, throwable: Throwable?) = log("E", tag, message, throwable)

    fun clear() {
        _inAppLogs.value = emptyList()
    }

    fun getLogcatLogs(): List<String> {
        if (!io.github.playfoundryhq.adaptiveflow.BuildConfig.DEBUG) {
            return listOf("System logcat is only available in debug builds. Use the in-app log tab.")
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:I"))
            val reader = process.inputStream.bufferedReader()
            val lines = mutableListOf<String>()
            var line: String? = reader.readLine()
            while (line != null) {
                lines.add(line)
                line = reader.readLine()
            }
            // filter for app or viewmodel logs or keep last 300
            val appFiltered = lines.filter { 
                it.contains("StudyViewModel") || 
                it.contains("MainActivity") || 
                it.contains("DiagnosticLogger") || 
                it.contains("io.github.playfoundryhq.adaptiveflow")
            }
            if (appFiltered.isNotEmpty()) {
                if (appFiltered.size > 300) appFiltered.takeLast(300) else appFiltered
            } else {
                if (lines.size > 300) lines.takeLast(300) else lines
            }
        } catch (e: Exception) {
            listOf("Failed to read logcat: ${e.message}")
        }
    }
}
