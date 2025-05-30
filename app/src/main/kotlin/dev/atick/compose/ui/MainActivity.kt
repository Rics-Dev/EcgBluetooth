package dev.atick.compose.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.orhanobut.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import dev.atick.compose.R
import dev.atick.movesense.utils.BleUtils
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var bleUtils: BleUtils

    private val permissions = mutableListOf<String>()
    private var permissionsInitialized = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Logger.i("ALL PERMISSIONS GRANTED")
            onAllPermissionsGranted()
        } else {
            Logger.w("Some permissions were denied")
            handlePermissionsDenied(permissions)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_JetpackComposeStarter)
        setContentView(R.layout.activity_main)

        setupPermissions()
        initializeBluetooth()
    }

    private fun setupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun initializeBluetooth() {
        bleUtils.initialize(this) {
            Logger.i("BLUETOOTH IS READY")
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        if (permissionsInitialized) {
            Logger.d("Permissions already checked, skipping")
            return
        }

        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            Logger.i("ALL PERMISSIONS ALREADY GRANTED")
            onAllPermissionsGranted()
        } else {
            Logger.i("Requesting ${missingPermissions.size} permissions")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        permissionsInitialized = true
    }

    private fun onAllPermissionsGranted() {
        Logger.i("App is ready with all permissions")
    }

    private fun handlePermissionsDenied(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys
        Logger.w("Denied permissions: $deniedPermissions")
        Logger.i("Continuing with limited functionality")
    }

    override fun onResume() {
        super.onResume()
        if (::bleUtils.isInitialized) {
            bleUtils.setupBluetooth(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

//package dev.atick.compose.ui
//
//import android.Manifest
//import android.os.Build
//import android.os.Bundle
//import androidx.appcompat.app.AppCompatActivity
//import com.orhanobut.logger.Logger
//import dagger.hilt.android.AndroidEntryPoint
//import dev.atick.compose.R
//import dev.atick.core.utils.extensions.checkForPermissions
//import dev.atick.movesense.utils.BleUtils
//import javax.inject.Inject
//
//@AndroidEntryPoint
//class MainActivity : AppCompatActivity() {
//
//    @Inject
//    lateinit var bleUtils: BleUtils
//
//    private val permissions = mutableListOf<String>()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setTheme(R.style.Theme_JetpackComposeStarter)
//        setContentView(R.layout.activity_main)
//
//        // Configure required permissions
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
//        }
//
//        permissions.add(Manifest.permission.RECORD_AUDIO)
//
//        checkForPermissions(permissions) {
//            Logger.i("ALL PERMISSIONS GRANTED")
//        }
//
//        bleUtils.initialize(this) {
//            Logger.i("BLUETOOTH IS READY")
//        }
//    }
//
//    override fun onResume() {
//        bleUtils.setupBluetooth(this)
//        super.onResume()
//    }
//}