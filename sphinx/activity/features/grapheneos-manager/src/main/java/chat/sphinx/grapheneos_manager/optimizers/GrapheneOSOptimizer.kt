package chat.sphinx.grapheneos_manager.optimizers

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

class GrapheneOSOptimizer(private val context: Context) {

    fun checkBatteryOptimizations() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Log.w("Performance", "App is subject to battery optimizations")
            requestBatteryOptimizationExemption()
        } else {
            Log.d("Performance", "Battery optimization exemption granted")
        }
    }

    @SuppressLint("BatteryLife", "QueryPermissionsNeeded")
    private fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }

    fun checkBackgroundAppLimits() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val isBackgroundRestricted = activityManager.isBackgroundRestricted
            Log.d("Performance", "Background restricted: $isBackgroundRestricted")
        }
    }
}