package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class CardGameApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val resourceOptions = try {
                    FirebaseOptions.fromResource(this)
                } catch (_: Exception) {
                    null
                }

                if (resourceOptions != null) {
                    FirebaseApp.initializeApp(this, resourceOptions)
                    Log.d("AuthDebug", "Firebase initialized from resources")
                } else {
                    val fallbackOptions = FirebaseOptions.Builder()
                        .setApplicationId("1:667500831282:android:cardgame")
                        .setProjectId("card-game-project")
                        .setApiKey("AIzaSyCardGameAndroidApiKey")
                        .setDatabaseUrl("https://card-game-project-default-rtdb.firebaseio.com")
                        .build()
                    FirebaseApp.initializeApp(this, fallbackOptions)
                    Log.d("AuthDebug", "Firebase initialized with fallback project options")
                }

                try {
                    com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true)
                } catch (_: Exception) {}
            } else {
                Log.d("AuthDebug", "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.w("AuthDebug", "Firebase initialization notice: ${e.message}")
        }
    }
}
