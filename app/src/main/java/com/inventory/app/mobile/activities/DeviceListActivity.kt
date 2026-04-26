package com.inventory.app.mobile.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.inventory.app.mobile.R
import com.inventory.app.mobile.SHOW_HISTORY_CONNECTED_LIST
import com.inventory.app.mobile.utils.FileUtils
import com.rscja.deviceapi.RFIDWithUHFBLE
import java.util.Collections

class DeviceListActivity : AppCompatActivity() {
    // private BluetoothAdapter mBtAdapter;
    private lateinit var mEmptyList: TextView
    private lateinit var tvTitle: TextView
    private lateinit var deviceList: ArrayList<MyDevice>
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var devRssiValues: HashMap<String, Int>
    private val mHandler = Handler()
    private var mScanning = false
    var uhf = RFIDWithUHFBLE.getInstance()

    companion object {
        const val TAG = "DeviceListActivity"
        private const val SCAN_PERIOD: Long = 10000 //10 seconds
        private const val REQUEST_BLE_PERMISSIONS = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        // 设置窗体背景透明
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.device_list)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
        init()
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** Entry point — checks permissions & location before starting BLE scan. */
    private fun checkAndStartScan() {
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions(), REQUEST_BLE_PERMISSIONS)
            return
        }
        // On Android < 12 BLE scan still requires Location Services to be ON
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
            Toast.makeText(
                this,
                "Please enable Location Services to scan for BLE devices",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        scanLeDevice(true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkAndStartScan()   // re-enter to also validate location
            } else {
                Toast.makeText(this, "Bluetooth permissions are required to scan", Toast.LENGTH_SHORT).show()
                mEmptyList.text = "Permission denied"
            }
        }
    }

    // ── Existing init ─────────────────────────────────────────────────────────

    private fun init() {
        tvTitle = findViewById(R.id.title_devices)
        mEmptyList = findViewById<TextView>(R.id.empty)
        findViewById<AppCompatImageView>(R.id.close).setOnClickListener { finish() }
        devRssiValues = HashMap()
        deviceList = ArrayList()
        deviceAdapter = DeviceAdapter(this, deviceList)
        val cancelButton = findViewById<Button>(R.id.btn_cancel)
        cancelButton.setOnClickListener {
            if (!mScanning) {
                checkAndStartScan()   // use the guarded entry point
            } else {
                finish()
            }
        }
        val btnClearHistory: Button = findViewById(R.id.btnClearHistory)
        btnClearHistory.setOnClickListener {
            FileUtils.clearXmlList()
            deviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            mEmptyList.visibility = View.VISIBLE
        }
        val isHistoryList: Boolean = intent.getBooleanExtra(SHOW_HISTORY_CONNECTED_LIST, false)
        if (isHistoryList) {
            tvTitle.setText(R.string.history_connected_device)
            mEmptyList.setText(R.string.no_history)
            cancelButton.visibility = View.GONE
            val historyList: List<Array<String>> = FileUtils.readXmlList()
            for (device in historyList) {
                addDevice(MyDevice(device[0], device[1]), 0)
            }
        } else {
            tvTitle.setText(R.string.select_device)
            mEmptyList.setText(R.string.scanning)
            btnClearHistory.visibility = View.GONE
            checkAndStartScan()   // ← guarded instead of direct scanLeDevice(true)
        }
        val newDevicesListView: ListView = findViewById(R.id.new_devices)
        newDevicesListView.adapter = deviceAdapter
        newDevicesListView.onItemClickListener = mDeviceClickListener
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice(enable: Boolean) {
        val cancelButton = findViewById<Button>(R.id.btn_cancel)
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed({
                mScanning = false
                uhf.stopScanBTDevices()
                cancelButton.setText(R.string.scan)
            }, SCAN_PERIOD)
            mScanning = true
            uhf.startScanBTDevices { bluetoothDevice, rssi, bytes ->
                runOnUiThread {
                    Log.d(TAG, "扫描成功")
                    addDevice(MyDevice(bluetoothDevice.address, bluetoothDevice.name), rssi)
                }
            }
            cancelButton.setText(R.string.cancel)
        } else {
            mScanning = false
            uhf.stopScanBTDevices()
            cancelButton.setText(R.string.scan)
        }
    }

    override fun onStop() {
        super.onStop()
        uhf.stopScanBTDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        uhf.stopScanBTDevices()
    }

    private fun addDevice(device: MyDevice, rssi: Int) {
        var deviceFound = false
        if (device.name == null || device.name == "") return
        for (listDev in deviceList!!) {
            if (listDev.address == device.address) {
                deviceFound = true
                break
            }
        }

        devRssiValues[device.address!!] = rssi
        if (!deviceFound) {
            deviceList.add(device)
            mEmptyList.visibility = View.GONE
        }

        // 根据信号强度重新排序
        Collections.sort(deviceList, object : Comparator<MyDevice> {
            override fun compare(device1: MyDevice, device2: MyDevice): Int {
                val key1 = device1.address
                val key2 = device2.address
                val v1 = devRssiValues[key1]!!
                val v2 = devRssiValues[key2]!!
                return if (v1 > v2) {
                    -1
                } else if (v1 < v2) {
                    1
                } else {
                    0
                }
            }

        })
        if (!deviceFound) {
            deviceAdapter!!.notifyDataSetChanged()
        }
    }

    private val mDeviceClickListener =
        AdapterView.OnItemClickListener { parent, view, position, id ->
            val device = deviceList!![position]
            uhf.stopScanBTDevices()
            val address = device.address!!.trim { it <= ' ' }
            if (!TextUtils.isEmpty(address)) {
                val b = Bundle()
                b.putString(BluetoothDevice.EXTRA_DEVICE, device.address)
                b.putBoolean("isSearch", true)
                val result = Intent()
                result.putExtras(b)
                setResult(Activity.RESULT_OK, result)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.invalid_bluetooth_address),
                    Toast.LENGTH_SHORT).show()
            }
        }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "scanLeDevice==============>")
        scanLeDevice(false)
    }

    internal inner class MyDevice {
        var address: String? = null
        var name: String? = null
        var bondState = 0

        constructor(address: String?, name: String?) {
            this.address = address
            this.name = name
        }
    }

    internal inner class DeviceAdapter(var context: Context, var devices: List<MyDevice>) :
        BaseAdapter() {
        private var inflater: LayoutInflater = LayoutInflater.from(context)

        override fun getCount(): Int {
            return devices.size
        }

        override fun getItem(position: Int): Any {
            return devices[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var vg = if (convertView != null) {
                convertView as ViewGroup
            } else {
                inflater.inflate(R.layout.device_element, null) as ViewGroup
            }
            val device = devices[position]
            val tvadd = vg.findViewById<TextView>(R.id.address)
            val tvname = vg.findViewById<TextView>(R.id.name)
            val tvpaired = vg.findViewById<TextView>(R.id.paired)
            val tvrssi = vg.findViewById<TextView>(R.id.rssi)
            val rssival = devRssiValues!![device.address]!!
            if (rssival != 0) {
                tvrssi.text = String.format("Rssi = %d", rssival)
                tvrssi.setTextColor(Color.BLACK)
                tvrssi.visibility = View.VISIBLE
            }
            tvname.text = device.name
            tvname.setTextColor(Color.BLACK)
            tvadd.text = device.address
            tvadd.setTextColor(Color.BLACK)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "device::" + device.name)
                tvpaired.setText(R.string.paired)
                tvpaired.setTextColor(Color.RED)
                tvpaired.visibility = View.VISIBLE
            } else {
                tvpaired.visibility = View.GONE
            }
            return vg
        }
    }
}