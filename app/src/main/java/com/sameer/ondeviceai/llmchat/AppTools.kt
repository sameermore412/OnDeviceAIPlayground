package com.sameer.ondeviceai.llmchat

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.AlarmClock
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.sameer.ondeviceai.AppState
import com.sameer.ondeviceai.DeviceInfo
import com.sameer.ondeviceai.ThemeMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A tool the model called, for showing in the chat. */
data class ToolCallEvent(val name: String, val arguments: String, val result: String)

/**
 * Tools the model can call inside the app. LiteRT-LM builds each tool's schema from the
 * @Tool and @ToolParam annotations and runs the method when the model asks for it.
 * Methods run on LiteRT-LM's worker thread, so they only touch thread-safe state.
 */
class AppTools(private val context: Context) : ToolSet {

    /** Told about every call so the chat can show it. Set by [LlmSession] while a reply streams. */
    @Volatile
    var listener: ((ToolCallEvent) -> Unit)? = null

    @Tool(description = "Get the phone's battery level, charging state, memory and model name.")
    fun getDeviceStatus(): Map<String, Any> {
        val battery = context.getSystemService(BatteryManager::class.java)
        val device = DeviceInfo.read(context)
        val result = mapOf(
            "phone" to device.model,
            "android" to device.androidVersion,
            "batteryPercent" to battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "charging" to battery.isCharging,
            "freeRamGb" to "%.1f".format(device.availableRamGb),
            "totalRamGb" to "%.1f".format(device.totalRamGb),
        )
        return result.also { report("getDeviceStatus", "", it) }
    }

    @Tool(description = "Get the current local date, time and time zone.")
    fun getCurrentDateTime(): String {
        val now = ZonedDateTime.now()
        val result = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")) + " (${now.zone})"
        return result.also { report("getCurrentDateTime", "", it) }
    }

    @Tool(description = "Set a timer that will go off after some time")
    fun setTimer(
        @ToolParam(description = "The time in seconds") seconds: Int,
    ): Map<String, Any> {
        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, "Egg Timer")
            putExtra(AlarmClock.EXTRA_LENGTH, 120) // Duration in seconds (e.g., 2 minutes)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Skip confirmation UI if supported
        }

        if (timerIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(timerIntent)
        }
        val result = mapOf("ok" to true, "seconds" to seconds)
        return result.also { report("setTimer", "seconds=$seconds", it) }
    }

    @Tool(description = "Turn the phone's flashlight (torch) on or off.")
    fun setFlashlight(
        @ToolParam(description = "true to turn the flashlight on, false to turn it off") on: Boolean,
    ): Map<String, Any> {
        val result = try {
            val cameras = context.getSystemService(CameraManager::class.java)
            val cameraId = cameras.cameraIdList.firstOrNull { id ->
                cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                mapOf("ok" to false, "error" to "This phone has no flashlight")
            } else {
                cameras.setTorchMode(cameraId, on)
                mapOf("ok" to true, "flashlight" to if (on) "on" else "off")
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to (e.message ?: "Couldn't change the flashlight"))
        }
        return result.also { report("setFlashlight", "on=$on", it) }
    }

    @Tool(description = "Change this app's color theme.")
    fun setAppTheme(
        @ToolParam(description = "One of: light, dark, system") mode: String,
    ): Map<String, Any> {
        val themeMode = ThemeMode.entries.firstOrNull { it.name.equals(mode.trim(), ignoreCase = true) }
        val result = if (themeMode == null) {
            mapOf("ok" to false, "error" to "Unknown theme '$mode'. Use light, dark or system.")
        } else {
            AppState.setThemeMode(themeMode)
            mapOf("ok" to true, "theme" to themeMode.name.lowercase())
        }
        return result.also { report("setAppTheme", "mode=$mode", it) }
    }

    @Tool(description = "Save a short note for the user.")
    fun addNote(
        @ToolParam(description = "The note text") text: String,
    ): Map<String, Any> {
        val note = AppState.addNote(text)
        val result = mapOf("ok" to true, "saved" to note.text, "totalNotes" to AppState.notes.value.size)
        return result.also { report("addNote", "text=\"$text\"", it) }
    }

    @Tool(description = "List the notes the user has saved.")
    fun listNotes(): List<String> {
        val result = AppState.notes.value.map { it.text }
        return result.also { report("listNotes", "", it) }
    }

    @Tool(description = "Search the web and get the top results with titles, links and short snippets.")
    fun webSearch(
        @ToolParam(description = "What to search for") query: String,
    ): Map<String, Any> {
        val result = try {
            val search = WebSearch.search(query)
            mapOf(
                "source" to search.source,
                "results" to search.results.map { mapOf("title" to it.title, "url" to it.url, "snippet" to it.snippet) },
            )
        } catch (e: Exception) {
            mapOf("error" to "Search failed: ${e.message}")
        }
        return result.also { report("webSearch", "query=\"$query\"", it) }
    }

    private fun report(name: String, arguments: String, result: Any) {
        listener?.invoke(ToolCallEvent(name, arguments, result.toString()))
    }
}
