package com.elthobhy.attendanceapps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.elthobhy.attendanceapps.databinding.ActivityMainBinding
import com.elthobhy.attendanceapps.databinding.LayoutDialogFormBinding
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.lang.Math.toRadians
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private var _binding : ActivityMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var locationRequest: LocationRequest
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initLocation()
        checkPermissionLocation()
        onClick()

    }

    private fun checkPermissionLocation() {
        if (checkPermission()) {
            if (!isLocationEnabled()) {
                Toast.makeText(this, "Please Turn On your Location", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else {
                requestPermission()
            }
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            ID_LOCATION_PERMISSION
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService((Context.LOCATION_SERVICE)) as LocationManager
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            return true
        }
        return false
    }

    private fun checkPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun initLocation() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create().apply {
            interval = 1000 * 5
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun onClick() {
        binding.fabCheckIn.setOnClickListener {
            loadLocation()
            Handler(Looper.getMainLooper()).postDelayed({
                getLastLocation()
            }, 2000)
        }
    }

    private fun getLastLocation() {
        if (checkPermission()) {
            if (isLocationEnabled()) {
                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(p0: LocationResult) {
                        super.onLocationResult(p0)
                        val location = p0.lastLocation
                        val currentLat = location.latitude
                        val currentLong = location.longitude
                        //lokasi tempat absen
                        val absenceLat = -6.357272186780644
                        val absenceLong = 106.81912983857849

                        val distance = calculateDistance(
                            currentLat,
                            currentLong,
                            absenceLat,
                            absenceLong
                        ) * 1000

                        Log.d("MainActivity", "onLocationResult - $distance ")
                        if(distance <90.0){
                            showDialogForm()
                            Toast.makeText(this@MainActivity, "Success", Toast.LENGTH_SHORT).show()
                        }else{
                            binding.tvCheckInSuccess.visibility = View.VISIBLE
                            binding.tvCheckInSuccess.text = "Out of range"
                        }
                        fusedLocationProviderClient?.removeLocationUpdates(this)
                        stopScanLocation()
                    }
                }
                fusedLocationProviderClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }else{
                Toast.makeText(this, "Please Turn on Your Location", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }else{
            requestPermission()
        }
    }

    private fun stopScanLocation() {
        binding.rippleBackground.stopRippleAnimation()
        binding.tvScanning.visibility = View.GONE
    }

    private fun showDialogForm() {
        val dialogBinding : LayoutDialogFormBinding = LayoutDialogFormBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton("Submit"){dialog, _->
                val name = dialogBinding.etName.text.toString()
                inputDataToFireBase(name)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel"){dialog, _->
                dialog.dismiss()

            }
            .show()
    }

    private fun inputDataToFireBase(name: String) {
        val user = User(name, getCurrentData())

        val database = FirebaseDatabase.getInstance("https://liveattendance-336301-default-rtdb.asia-southeast1.firebasedatabase.app")
        val attendanceRef = database.getReference("log_attendance")

        attendanceRef.child(name).setValue(user)
            .addOnSuccessListener {
                binding.tvCheckInSuccess.visibility = View.VISIBLE
                binding.tvCheckInSuccess.text = getString(R.string.check_in_success)
            }
            .addOnFailureListener {
                Toast.makeText(this,"${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCurrentData(): String {
        val currentTime = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(currentTime)
    }

    private fun calculateDistance(
        currentLat: Double,
        currentLong: Double,
        absenceLat: Double,
        absenceLong: Double
    ): Double {
        val r = 6372.8 // in km
        val radiansLat1 = toRadians(currentLat)
        val radiansLat2 = toRadians(absenceLat)
        val dLat = toRadians(absenceLat - currentLat)
        val dLong = toRadians(absenceLong - currentLong)
        return 2 * r * asin(
            sqrt(
                sin(dLat / 2).pow(2.0) + sin(dLong / 2).pow(2.0) * cos(radiansLat1) * cos(
                    radiansLat2
                )
            )
        )
    }

    private fun loadLocation() {
        binding.rippleBackground.startRippleAnimation()
        binding.tvScanning.visibility = View.VISIBLE
        binding.tvCheckInSuccess.visibility = View.GONE

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val ID_LOCATION_PERMISSION = 0
    }
}