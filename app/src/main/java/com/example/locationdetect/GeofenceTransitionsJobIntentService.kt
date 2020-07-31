package com.example.locationdetect

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceTransitionsJobIntentService : JobIntentService() {

    companion object {
        private const val LOG_TAG = "GeoTrIntentService"

        private const val JOB_ID = 573

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java, JOB_ID,
                intent
            )
        }
    }

    override fun onHandleWork(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceErrorMessages.getErrorString(
                this,
                geofencingEvent.errorCode
            )
            Log.e(LOG_TAG, errorMessage)
            return
        }

        handleEvent(geofencingEvent)
    }

    private fun handleEvent(event: GeofencingEvent) {
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d(LOG_TAG, "inside transition")
            val reminder = getFirstReminder(event.triggeringGeofences)
            val wifiName = reminder?.wifiName
            val latLng = reminder?.latLng
            if (wifiName != null && latLng != null) {
                val currentWifiSsid: String = getCurrentSsid(this).replace("\"", "")
                Log.d(LOG_TAG, "current ssid $currentWifiSsid, saved ssid $wifiName")
                sendNotification(this, "Device is Inside", latLng)
            }
        } else if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val reminder = getFirstReminder(event.triggeringGeofences)
            val latLng = reminder?.latLng
            val wifiName: String = reminder?.wifiName ?: ""
            val currentWifiSsid: String = getCurrentSsid(this).replace("\"", "")
            Log.d(LOG_TAG, "current ssid $currentWifiSsid, saved ssid $wifiName")
            if (wifiName != currentWifiSsid) {
                Log.d(LOG_TAG, "is outside")
                if (latLng != null) {
                    sendNotification(this, "Device is Outside", latLng)
                }
            }
        }
    }

    private fun getFirstReminder(triggeringGeofences: List<Geofence>): Reminder? {
        val firstGeofence = triggeringGeofences[0]
        return (application as DetectLocationApp).getRepository().get(firstGeofence.requestId)
    }
}