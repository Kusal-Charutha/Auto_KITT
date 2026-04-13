package com.example.autokitt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import com.example.autokitt.database.AppDatabase
import com.example.autokitt.utils.CsvExporter
import com.example.autokitt.utils.DebugLogger
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.widget.ImageView
import android.widget.PopupMenu


class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceList = ArrayList<BluetoothDevice>()
    private lateinit var listAdapter: ArrayAdapter<String>
    private val deviceNames = ArrayList<String>()
    
    // Database
    private lateinit var database: AppDatabase
    
    // Export State removed - Logic moved to ExportActivity

    
    // Permission State
    private var pendingAction: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val name = it.name
                        // Allow all devices to be visible to ensure we don't hide the OBD adapter
                        if (name != null) {
                            if (!deviceList.contains(it)) {
                                deviceList.add(it)
                                deviceNames.add("$name\n${it.address}")
                                listAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        database = AppDatabase.getDatabase(this)
        // --- AI Health Check on Launch ---
        DebugLogger.log(this, "APP_LAUNCH: Checking AI Assets...")
        val assetList = listOf(
            "models/driver_behavior_alert_model/driver_behavior_alert_model.tflite",
            "models/driver_behavior_alert_model/scaler.json",
            "models/driver_behavior_alert_model/feature_columns.json",
            "models/vehicle_fault_prediction_model/vehicle_fault_model.tflite",
            "models/vehicle_fault_prediction_model/scaler.json",
            "models/vehicle_fault_prediction_model/feature_order.json"
        )
        
        assetList.forEach { path ->
            try {
                assets.open(path).use { 
                    DebugLogger.log(this, "ASSET_CHECK: FOUND -> $path")
                }
            } catch (e: Exception) {
                DebugLogger.log(this, "ASSET_CHECK: MISSING !! -> $path")
            }
        }
        val btnConnectPaired = findViewById<Button>(R.id.btnConnectPaired) // Still a Button
        val btnSettings = findViewById<View>(R.id.btnSettings) // Now a LinearLayout
        val btnDashboard = findViewById<View>(R.id.btnDashboard) // Now a FrameLayout
        val btnExport = findViewById<View>(R.id.btnExport) // Now a FrameLayout
        val btnDisconnect = findViewById<Button>(R.id.btnDisconnect) // Still a Button
        
        // This list is now hidden/unused in layout, but kept for logic references
        // We will remove logic references soon
        val btnMyDriving = findViewById<View>(R.id.btnMyDriving)
        val btnVehicleHealth = findViewById<View>(R.id.btnVehicleHealth)

        // This list is now hidden/unused in layout, but kept for logic references
        // We will remove logic references soon
        val listView = findViewById<ListView>(R.id.deviceList)
        
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        listView.adapter = listAdapter





        btnConnectPaired.setOnClickListener {
            Log.d("AutoKITT", "Connect Button Clicked")
            // Toast.makeText(this, "Button Clicked!", Toast.LENGTH_SHORT).show()

            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth Not Supported", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!bluetoothAdapter.isEnabled) {
                Toast.makeText(this, "Please Enable Bluetooth First", Toast.LENGTH_SHORT).show()
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
                return@setOnClickListener
            }

            pendingAction = { 
                Log.d("AutoKITT", "Pending Action Triggered")
                showPairedDevicesDialog() 
            }
            
            if (checkPermissions()) {
                Log.d("AutoKITT", "Permissions Granted - Showing Dialog")
                showPairedDevicesDialog()
            } else {
                 Log.d("AutoKITT", "Requesting Permissions")
            }
        }



        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        findViewById<View>(R.id.btnDashboard).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnMyDriving).setOnClickListener {
            val intent = Intent(this, MyDrivingActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnVehicleHealth).setOnClickListener {
            val intent = Intent(this, VehicleHealthActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnExport).setOnClickListener {
            val intent = Intent(this, ExportActivity::class.java)
            startActivity(intent)
        }
        


        btnDisconnect.setOnClickListener {
            stopService(Intent(this, OBDForegroundService::class.java))
            Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = deviceList[position]
            startOBDService(device.address)
        }
        
        // Profile & Sign Out Logic
        val ivProfile = findViewById<ImageView>(R.id.ivProfile)
        val account = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            Log.d("AutoKITT", "Account found: ${account.displayName}")
            Log.d("AutoKITT", "Photo URL: ${account.photoUrl}")
            
            if (account.photoUrl != null) {
                // Log the URL for debugging
                Log.d("AutoKITT", "Loading Photo URL: ${account.photoUrl}")
                
                Glide.with(this)
                    .load(account.photoUrl)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .circleCrop()
                    .into(ivProfile)
            } else {
                Log.d("AutoKITT", "Photo URL is null for account: ${account.email}")
                // Optional: Show a toast if debugging is needed
                // Toast.makeText(this, "No profile photo found in account", Toast.LENGTH_SHORT).show()
            }
        } else {
             Log.d("AutoKITT", "Account is null")
        }

        ivProfile.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("Sign Out")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "Sign Out") {
                    signOut()
                }
                true
            }
            popup.show()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, 2) // 2 = RECEIVER_NOT_EXPORTED
        } else {
            registerReceiver(receiver, filter)
        }

    }

    private fun checkPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missingPermissions.isNotEmpty()) {
            Log.d("AutoKITT", "Missing permissions: ${missingPermissions.joinToString()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            false
        } else {
            Log.d("AutoKITT", "All permissions present")
            true
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
             Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            showPermissionSettingsDialog()
        }
    }

    private fun showPermissionSettingsDialog() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "This app needs Bluetooth permissions to scan for OBD devices. Please grant them in settings."
        } else {
            "On Android 11 and older, scanning for Bluetooth devices requires Location permission. Please grant Location in settings."
        }

        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showPairedDevicesDialog() {
        Log.d("AutoKITT", "showPairedDevicesDialog Called")
        // Permissions already checked by caller logic via pendingAction pattern
        // But double check doesn't hurt if called directly
        
        // On Android 12+ (API 31+), we need BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                 Log.d("AutoKITT", "Permission NOT granted in showPairedDevicesDialog")
                 // Should not happen if flow is correct, but safe fallback
                 return
            }
        }
        
        Log.d("AutoKITT", "Fetching bonded devices")

        val pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired devices found! Please pair your OBD device in Android Settings.", Toast.LENGTH_LONG).show()
            Log.e("AutoKITT", "No paired devices found")
            return
        }

        val deviceNamesList = pairedDevices.map { "${it.name ?: "Unknown"}\n${it.address}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Paired Device")
            .setItems(deviceNamesList) { _, which ->
                val device = pairedDevices[which]
                startOBDService(device.address)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Export logic moved to ExportActivity


    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        
        deviceList.clear()
        deviceNames.clear()

        // List paired devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.bondedDevices?.forEach {
                deviceList.add(it)
                deviceNames.add("${it.name}\n${it.address}")
            }
        }
        listAdapter.notifyDataSetChanged()
        
        bluetoothAdapter?.startDiscovery()
        Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
    }

    private fun startOBDService(address: String) {
        val intent = Intent(this, OBDForegroundService::class.java)
        intent.putExtra(OBDForegroundService.EXTRA_DEVICE_ADDRESS, address)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        
        // Navigate to Dashboard
        val dashboardIntent = Intent(this, DashboardActivity::class.java)
        startActivity(dashboardIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
    
    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("33413763022-85jfu46cvelhmsq8ond19460kih02sjt.apps.googleusercontent.com")
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener(this) {
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
