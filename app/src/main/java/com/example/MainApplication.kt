package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("MainApplication", "FirebaseApp initialized manually.")
            }
        } catch (e: Exception) {
            Log.e("MainApplication", "Failed to initialize FirebaseApp", e)
        }
    }
}
