package com.sameer.ondeviceai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** What the phone has to work with; mostly decides how large a model it can run. */
data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val totalRamGb: Double,
    val availableRamGb: Double,
    val cpuCores: Int,
) {
    companion object {
        fun read(context: Context): DeviceInfo {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val bytesPerGb = 1024.0 * 1024 * 1024
            return DeviceInfo(
                model = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                totalRamGb = memory.totalMem / bytesPerGb,
                availableRamGb = memory.availMem / bytesPerGb,
                cpuCores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }
}
