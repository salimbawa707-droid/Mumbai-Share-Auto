package com.mumbaishareauto.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the Firebase App Check debug provider.
 * Register its emitted debug token in Firebase Console before enabling App Check enforcement.
 */
object FirebaseAppCheckInstaller {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context.applicationContext).isEmpty()) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
