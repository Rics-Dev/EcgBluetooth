
package dev.atick.compose.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.orhanobut.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import dev.atick.compose.R
import dev.atick.core.utils.extensions.checkForPermissions
import dev.atick.movesense.utils.BleUtils
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var bleUtils: BleUtils

    private val permissions = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_JetpackComposeStarter)
        setContentView(R.layout.activity_main)

        // Configure required permissions based on Android version
        setupPermissions()

        // Initialize BLE utils first, then check permissions
        initializeBluetooth()
    }

    private fun setupPermissions() {
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires new Bluetooth permissions
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            // Older Android versions require location permissions for Bluetooth
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // File access permissions
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
            // Now check for permissions after BLE utils is initialized
            checkForPermissions(permissions) {
                Logger.i("ALL PERMISSIONS GRANTED")
                // Additional setup can go here if needed
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bleUtils.setupBluetooth(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any resources if needed
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