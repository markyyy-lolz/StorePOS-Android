package com.storepos.app.data

import android.content.Context
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth

/**
 * A device-only sign-in preference. No password or tokens are stored here:
 * Supabase Auth owns its secure session lifecycle.
 *
 * When Stay signed in is off, invalidate the locally restored Auth session
 * once per new application process, not after every Activity recreation.
 * This distinction matters because camera barcode scanning may recreate an
 * Activity during orientation/lifecycle changes.
 */
object AppSessionRetention {
    private const val PREFS_NAME = "storepos_sign_in_options"
    private const val KEY_STAY_SIGNED_IN = "stay_signed_in"

    @Volatile
    private var handledThisProcess = false

    internal fun mustClearAtColdStart(staySignedIn: Boolean): Boolean = !staySignedIn

    fun shouldStaySignedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_STAY_SIGNED_IN, true)

    fun setStaySignedIn(context: Context, enabled: Boolean) {
        check(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_STAY_SIGNED_IN, enabled).commit()
        ) { "Could not save the sign-in preference." }
    }

    suspend fun enforceOnColdStart(context: Context) {
        // Avoid signing the cashier out on a barcode-scanner Activity restart.
        synchronized(this) {
            if (handledThisProcess) return
            handledThisProcess = true
        }
        val auth = SupabaseProvider.client.auth
        auth.awaitInitialization()
        if (mustClearAtColdStart(shouldStaySignedIn(context))) {
            // Local-only session clearance: no need for network on restart.
            auth.clearSession()
        }
    }
}
