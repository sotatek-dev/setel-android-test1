package com.example.locationdetect

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.material.snackbar.Snackbar
import com.jflavio1.wificonnector.WifiConnector
import com.jflavio1.wificonnector.interfaces.ConnectionResultListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.suspendCoroutine


class MainActivity : BaseActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    companion object {
        public const val MY_LOCATION_REQUEST_CODE = 329
        private const val NEW_REMINDER_REQUEST_CODE = 330
        private const val EXTRA_LAT_LNG = "EXTRA_LAT_LNG"

        fun newIntent(context: Context, latLng: LatLng?): Intent {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(EXTRA_LAT_LNG, latLng)
            return intent
        }
    }

    private var map: GoogleMap? = null

    private lateinit var locationManager: LocationManager
    private val fusedLocationProviderClient by lazy {
        FusedLocationProviderClient(this@MainActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        newReminder.visibility = View.GONE
        currentLocation.visibility = View.GONE
        newReminder.setOnClickListener {
            map?.run {
                val intent = NewReminderActivity.newIntent(
                    this@MainActivity,
                    cameraPosition.target,
                    cameraPosition.zoom
                )
                startActivityForResult(intent, NEW_REMINDER_REQUEST_CODE)
            }
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                MY_LOCATION_REQUEST_CODE
            )
        }
        registerWifiReceiver()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NEW_REMINDER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            showReminders()
            val reminder = getRepository().getLast()
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(reminder?.latLng, 15f))
            Snackbar.make(main, R.string.reminder_added_success, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_LOCATION_REQUEST_CODE) {
            onMapAndPermissionReady()
        }
    }

    private fun onMapAndPermissionReady() {
        if (map != null
            && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            map?.isMyLocationEnabled = true
            newReminder.visibility = View.VISIBLE
            currentLocation.visibility = View.VISIBLE

            currentLocation.setOnClickListener {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fusedLocationProviderClient.lastLocation.addOnSuccessListener {
                        it ?: return@addOnSuccessListener
                        val latLng = LatLng(it.latitude, it.longitude)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }

            }

            showReminders()

            centerCamera()
        }
    }

    private fun centerCamera() {
        if (intent.extras != null && intent.extras!!.containsKey(EXTRA_LAT_LNG)) {
            val latLng = intent.extras!!.get(EXTRA_LAT_LNG) as LatLng?
            if (latLng != null)
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun showReminders() {
        map?.run {
            clear()
            for (reminder in getRepository().getAll()) {
                showReminderInMap(this@MainActivity, this, reminder)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.run {
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false
            setOnMarkerClickListener(this@MainActivity)
        }

        onMapAndPermissionReady()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val reminder = getRepository().get(marker.tag as String)

        if (reminder != null) {
            showReminderRemoveAlert(reminder)
        }

        return true
    }

    private fun showReminderRemoveAlert(reminder: Reminder) {
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.run {
            setMessage(getString(R.string.reminder_removal_alert))
            setButton(
                AlertDialog.BUTTON_POSITIVE,
                getString(R.string.reminder_removal_alert_positive)
            ) { dialog, _ ->
                removeReminder(reminder)
                dialog.dismiss()
            }
            setButton(
                AlertDialog.BUTTON_NEGATIVE,
                getString(R.string.reminder_removal_alert_negative)
            ) { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    private fun removeReminder(reminder: Reminder) {
        getRepository().remove(
            reminder,
            success = {
                showReminders()
                Snackbar.make(main, R.string.reminder_removed_success, Snackbar.LENGTH_LONG).show()
            },
            failure = {
                Snackbar.make(main, it, Snackbar.LENGTH_LONG).show()
            })
    }

    private suspend fun getCurrentSSID() = suspendCoroutine<String?> {
        val wifiConnector = WifiConnector(this@MainActivity)
        wifiConnector.registerWifiConnectionListener(object : ConnectionResultListener {
            override fun errorConnect(codeReason: Int) {
                Log.d("WifiConnector", "errorConnect")
            }

            override fun successfulConnect(SSID: String?) {
            }

            override fun onStateChange(supplicantState: SupplicantState?) {

            }

        })
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            lifecycleScope.launch(Dispatchers.IO) {
                val wifiConnector = WifiConnector(this@MainActivity)
                wifiConnector.registerWifiConnectionListener(object : ConnectionResultListener {
                    override fun errorConnect(codeReason: Int) {
                        Log.d("WifiConnector", "errorConnect")
                    }

                    override fun successfulConnect(currentSSID: String?) {
                        val ssid = currentSSID?.replace("\"", "")
                        Log.d("Main", "wifi change $ssid")
                        if (getRepository().getAll().isNotEmpty()) {
                            val reminder = getRepository().getAll().first()
                            val latLng = getRepository().getAll().first().latLng
                            if (p1?.action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                                if (getRepository().getAll().map { it.wifiName }
                                        .contains(ssid)) {
                                    sendNotification(
                                        this@MainActivity,
                                        "Device is Inside",
                                        latLng!!
                                    )
                                } else {
                                    detectOutSide(reminder)
                                }
                            }
                        }
                    }

                    override fun onStateChange(supplicantState: SupplicantState?) {
                        Log.d("Main", "onStateChange $supplicantState")
                        val connManager =
                            this@MainActivity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        if (getRepository().getAll().isNotEmpty()) {
                            val reminder = getRepository().getAll().first()
                            if (connManager.connectionInfo.networkId == -1) {
                                detectOutSide(reminder)
                            }
                        }

                    }
                })
            }
        }
    }

    private fun detectOutSide(reminder: Reminder) {
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener {
                it ?: return@addOnSuccessListener
                val latLng = LatLng(it.latitude, it.longitude)
                val locationReminder = Location("B")
                locationReminder.latitude = latLng.latitude
                locationReminder.longitude = latLng.longitude
                val distance = distanceLocation(it, locationReminder)
                if (distance > reminder.radius?.toFloat()!!) {
                    sendNotification(
                        this@MainActivity,
                        "Device is Outside",
                        latLng!!
                    )
                }
            }
        }
    }

    private fun distanceLocation(locationA: Location, locationB: Location): Float {
        return locationA.distanceTo(locationB)
    }

    private fun registerWifiReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(wifiReceiver, intentFilter)
    }

    private fun unregisterWifiReceiver() {
        unregisterReceiver(wifiReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterWifiReceiver()
    }
}
