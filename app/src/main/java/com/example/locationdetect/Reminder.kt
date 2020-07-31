package com.example.locationdetect

import com.google.android.gms.maps.model.LatLng
import java.util.*

data class Reminder(val id: String = UUID.randomUUID().toString(),
                    var latLng: LatLng?,
                    var radius: Double?,
                    var wifiName: String =  "")