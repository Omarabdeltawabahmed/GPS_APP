package com.example.satalite

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvOnlineLocation: TextView
    private lateinit var tvOfflineLocation: TextView
    private lateinit var tvComparison: TextView
    private lateinit var tvSatellites: TextView

    private var currentOnlineLocation: Location? = null
    private var lastKnownOfflineLocation: Location? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
    val locationListener = LocationListener { location ->
        currentOnlineLocation = location
        updateOnlineLocationUI()
        performComparison()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOnlineLocation = findViewById(R.id.tvOnlineLocation)
        tvOfflineLocation = findViewById(R.id.tvOfflineLocation)
        tvComparison = findViewById(R.id.tvComparison)
        tvSatellites = findViewById(R.id.tvSatellites)
        val btnPermissions: Button = findViewById(R.id.btnRequestPermissions)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnPermissions.setOnClickListener {
            requestLocationPermissions()
        }

        if (checkPermissions()) {
            startGnssAndLocationUpdates()
        } else {
            requestLocationPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGnssAndLocationUpdates()
            } else {
                Toast.makeText(this, "Permissions are required to run the app", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startGnssAndLocationUpdates() {
        registerGnssCallback()
        getLastKnownOfflineLocation()
        requestOnlineLocationUpdates()
    }

    private fun registerGnssCallback() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val gnssStatusCallback = object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                updateSatelliteInfo(status)
            }
        }
        locationManager.registerGnssStatusCallback(gnssStatusCallback, null)
    }

    private fun updateSatelliteInfo(status: android.location.GnssStatus) {
        val satelliteCount = status.satelliteCount
        val sb = StringBuilder()
        sb.append("Total satellites: $satelliteCount\n")
        sb.append("----------------------------\n")
        for (i in 0 until satelliteCount) {
            val constellation = status.getConstellationType(i)
            val constellationName = when (constellation) {
                android.location.GnssStatus.CONSTELLATION_GPS -> "GPS"
                android.location.GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                android.location.GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
                android.location.GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                else -> "Other"
            }
            val svid = status.getSvid(i)
            val snr = status.getCn0DbHz(i)
            val usedInFix = status.usedInFix(i)
            sb.append("$constellationName - ID $svid - Signal: ${snr} dBHz - ${if (usedInFix) "Used ✓" else "Not used ✗"}\n")
        }
        runOnUiThread {
            tvSatellites.text = sb.toString()
        }
    }

    private fun getLastKnownOfflineLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            lastKnownOfflineLocation = location
            updateOfflineLocationUI()
            performComparison()
        }.addOnFailureListener {
            runOnUiThread {
                tvOfflineLocation.text = "Failed to get stored location"
            }
        }
    }

    private fun updateOfflineLocationUI() {
        runOnUiThread {
            if (lastKnownOfflineLocation != null) {
                tvOfflineLocation.text = String.format(
                    "Lat: %.6f, Lon: %.6f\nAccuracy: %.1f m\nTime: %s",
                    lastKnownOfflineLocation!!.latitude,
                    lastKnownOfflineLocation!!.longitude,
                    lastKnownOfflineLocation!!.accuracy,
                    android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", lastKnownOfflineLocation!!.time)
                )
            } else {
                tvOfflineLocation.text = "No stored location"
            }
        }
    }

    private fun requestOnlineLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            1f,
            locationListener,
            null
        )
    }

    private fun updateOnlineLocationUI() {
        runOnUiThread {
            if (currentOnlineLocation != null) {
                tvOnlineLocation.text = String.format(
                    "Lat: %.6f, Lon: %.6f\nAccuracy: %.1f m\nSpeed: %.1f m/s\nTime: %s",
                    currentOnlineLocation!!.latitude,
                    currentOnlineLocation!!.longitude,
                    currentOnlineLocation!!.accuracy,
                    currentOnlineLocation!!.speed,
                    android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", currentOnlineLocation!!.time)
                )
            } else {
                tvOnlineLocation.text = "Waiting for GPS signal..."
            }
        }
    }

    private fun performComparison() {
        val online = currentOnlineLocation
        val offline = lastKnownOfflineLocation
        if (online == null) {
            runOnUiThread { tvComparison.text = "No Online location available for comparison" }
            return
        }
        if (offline == null) {
            runOnUiThread { tvComparison.text = "No Offline stored location" }
            return
        }

        val distanceInMeters = online.distanceTo(offline)
        val accuracyDiff = online.accuracy - offline.accuracy

        val comparisonText = """
            Distance between locations: ${String.format("%.2f", distanceInMeters)} meters
            Online accuracy: ${String.format("%.1f", online.accuracy)} m
            Offline accuracy: ${String.format("%.1f", offline.accuracy)} m
            ${if (distanceInMeters > 50) "⚠️ Large movement - stored location may be outdated"
        else if (online.accuracy < offline.accuracy) "✅ Online is more accurate"
        else "ℹ️ Offline location is still acceptable"}
        """.trimIndent()
        runOnUiThread {
            tvComparison.text = comparisonText
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
    }
}