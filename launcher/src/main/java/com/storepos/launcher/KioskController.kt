package com.storepos.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.storepos.launcher.admin.StorePosDeviceAdminReceiver

object KioskController {
    const val STOREPOS_PACKAGE = "com.storepos.app"

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, StorePosDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    fun isStorePosInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(STOREPOS_PACKAGE) != null

    fun openStorePos(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(STOREPOS_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return true
    }

    fun openHomeSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openSystemSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun applyKiosk(activity: Activity, enabled: Boolean): Boolean {
        val manager = dpm(activity)
        if (!manager.isDeviceOwnerApp(activity.packageName)) return false

        val admin = adminComponent(activity)

        if (enabled) {
            val packages = mutableListOf(activity.packageName, STOREPOS_PACKAGE)
            Intent(Settings.ACTION_SETTINGS)
                .resolveActivity(activity.packageManager)
                ?.packageName
                ?.let(packages::add)

            manager.setLockTaskPackages(admin, packages.distinct().toTypedArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            runCatching { manager.setStatusBarDisabled(admin, true) }
            runCatching { manager.setKeyguardDisabled(admin, true) }

            if (manager.isLockTaskPermitted(activity.packageName)) {
                runCatching { activity.startLockTask() }
            }
        } else {
            runCatching { activity.stopLockTask() }
            runCatching { manager.setStatusBarDisabled(admin, false) }
            runCatching { manager.setKeyguardDisabled(admin, false) }
            runCatching { manager.setLockTaskPackages(admin, emptyArray()) }
        }
        return true
    }
}
