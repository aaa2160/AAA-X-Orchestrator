package com.aaa.orchestrator.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import timber.log.Timber

/**
 * Real-time hardware health and thermal monitor tailored for Samsung Galaxy A30.
 * Automatically halts or throttles automation if temperature exceeds 41°C or battery < 15%.
 */
class HardwareGuard(private val context: Context) {

    data class BatterySnapshot(
        val percent: Int,
        val temperatureCelsius: Float,
        val isCharging: Boolean
    )

    fun checkHardwareStatus(): BatterySnapshot {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 50
        val tempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 32.0f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatterySnapshot(percent, tempCelsius, isCharging)
    }

    fun isSafeToOperate(): Pair<Boolean, String?> {
        val snapshot = checkHardwareStatus()
        if (snapshot.percent < MIN_BATTERY_PERCENT && !snapshot.isCharging) {
            return Pair(false, "Battery low (${snapshot.percent}% < $MIN_BATTERY_PERCENT%). Connect charger to resume.")
        }
        if (snapshot.temperatureCelsius > MAX_THERMAL_TEMP_CELSIUS) {
            return Pair(false, "Thermal throttle active (${snapshot.temperatureCelsius}°C > $MAX_THERMAL_TEMP_CELSIUS°C). Cooling down.")
        }
        return Pair(true, null)
    }

    companion object {
        const val MIN_BATTERY_PERCENT = 15
        const val MAX_THERMAL_TEMP_CELSIUS = 41.0f
    }
}
