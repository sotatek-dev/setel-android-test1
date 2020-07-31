package com.example.locationdetect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("GeofenceBroadcas", "detect")
        GeofenceTransitionsJobIntentService.enqueueWork(context, intent)
    }
}