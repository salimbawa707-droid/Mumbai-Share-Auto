package com.mumbaishareauto.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds use Play Integrity for Firebase App Check attestation.
 */
object FirebaseAppCheckInstaller {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context.applicationContext).isEmpty()) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
