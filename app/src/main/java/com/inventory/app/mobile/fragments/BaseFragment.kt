package com.inventory.app.mobile.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import cn.pedant.SweetAlert.SweetAlertDialog
import com.inventory.app.mobile.R
import com.inventory.app.mobile.REQUEST_ENABLE_BT
import com.inventory.app.mobile.REQUEST_SELECT_DEVICE
import com.inventory.app.mobile.RUNNING_DISCONNECT_TIMER
import com.inventory.app.mobile.SHOW_HISTORY_CONNECTED_LIST
import com.inventory.app.mobile.activities.DeviceListActivity
import com.inventory.app.mobile.activities.MainActivity
import com.inventory.app.mobile.utils.FileUtils
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SPUtils
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.rscja.deviceapi.RFIDWithUHFBLE
import com.rscja.deviceapi.interfaces.ConnectionStatus
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback
import java.util.Timer
import java.util.TimerTask
import kotlin.ranges.rangeTo
import kotlin.text.toInt
import kotlin.toString


open class BaseFragment : Fragment(), ConnectionStatusCallback<Any> {
    protected val mainActivity : MainActivity? get() = activity as MainActivity?
    var flow : Int = 0
    protected lateinit var sessionManager : SessionManager
    protected lateinit var apiInterface: ApiInterface
    protected var radioPower : Int = Params.MAX_POWER

    var uhf: RFIDWithUHFBLE? = RFIDWithUHFBLE.getInstance()

    private var mypDialog: ProgressDialog? = null

    protected var mBtAdapter: BluetoothAdapter? = null
    protected var mDevice: BluetoothDevice? = null
    private var toast: Toast? = null
    protected var connectionStatusCallback : ConnectionStatusCallback<Any>? = null

    // BT connection state
    protected var remoteBTName: String = ""
    protected var remoteBTAdd: String = ""
    protected var mIsActiveDisconnect = true
    protected val RECONNECT_NUM = Int.MAX_VALUE
    protected var mReConnectCount = RECONNECT_NUM
    var timeCountCur: Long = 0
    private val mDisconnectTimer = Timer()
    private var timerTask: DisconnectTimerTask? = null
    private val period: Long = (1000 * 30).toLong()
    protected var isKeyDownUP = false
    protected var mIsScanning: Boolean = false
    protected val connectStatusList: ArrayList<IConnectStatus> = ArrayList()

    interface IConnectStatus {
        fun getStatus(connectionStatus: ConnectionStatus?)
    }

    protected val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                RUNNING_DISCONNECT_TIMER -> {
                    val time = msg.obj as Long
                    formatConnectButton(time)
                }
            }
        }
    }
    fun showToast(text: String?) {
        if (toast != null) {
            toast!!.cancel()
        }
        toast = Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
        toast!!.show()
    }

    fun showToast(resId: Int) {
        showToast(getString(resId))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionStatusCallback = this
        sessionManager = SessionManager(requireContext())
        radioPower = sessionManager.getRfidPower().toInt()
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)
        initUHF()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onPowerUpdated()
    }

    protected fun showMessage(text: String, dialogType : Int = SweetAlertDialog.SUCCESS_TYPE,
                              listenerOk : SweetAlertDialog.OnSweetClickListener? = null,
                              listenerCancel : SweetAlertDialog.OnSweetClickListener? = null,
                              okText : String = "Ok",
                              cancelText : String = "Batal") {
        val dlg = SweetAlertDialog(requireContext(), dialogType)
        dlg.setTitleText(text)
        dlg.setConfirmText(okText)
        dlg.setConfirmClickListener { sweetAlertDialog ->
            sweetAlertDialog.dismiss()
            listenerOk?.onClick(sweetAlertDialog)
        }
        if (listenerOk != null) {
            dlg.setCancelText(cancelText)
            dlg.setCancelClickListener { sweetAlertDialog ->
                sweetAlertDialog.dismiss()
                listenerCancel?.onClick(sweetAlertDialog )
            }
        }
        dlg.show()
    }


    open fun ReaderOnKeyDwon() {
    }

    open fun onPowerUpdated() {

    }

    protected fun showPowerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_power, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerOptions)

        val options = (Params.MIN_POWER ..Params.MAX_POWER).map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(radioPower - Params.MIN_POWER)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Power")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                radioPower = spinner.selectedItem.toString().toInt()
                onPowerUpdated()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    fun connect(deviceAddress: String?) {
        if (uhf?.connectStatus == ConnectionStatus.CONNECTING) {
            showToast(R.string.connecting)
        } else {
            uhf?.connect(deviceAddress, connectionStatusCallback)
        }
    }

    fun search() {
        showBluetoothDevice(false)
    }


    protected fun showBluetoothDevice(isHistory: Boolean) {
        if (!isAdded) return  // Guard if fragment is detached

        if (mBtAdapter == null) {
            showToast("Bluetooth is not available")
            return
        }
        if (!mBtAdapter!!.isEnabled) {
            Log.i("Beka", "onClick - BT not enabled yet")
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        } else {
            val activity = activity ?: return  // Safe null check instead of requireActivity()
            val newIntent = Intent(activity, DeviceListActivity::class.java)
            newIntent.putExtra(SHOW_HISTORY_CONNECTED_LIST, isHistory)
            startActivityForResult(newIntent, REQUEST_SELECT_DEVICE)
        }
    }


    fun disconnect(isActiveDisconnect: Boolean) {
        mIsActiveDisconnect = isActiveDisconnect
        cancelDisconnectTimer()
        uhf?.disconnect()
    }


    fun initConnect() {
        if (sessionManager.getDeviceAddress().isNullOrBlank()) {
            search()
        } else {
            remoteBTAdd = sessionManager.getDeviceAddress()!!
            connect(remoteBTAdd)
        }
    }

    fun initUHF() {
        try {
            mBtAdapter = BluetoothAdapter.getDefaultAdapter()

            uhf?.init(requireContext())

            //mReader = RFIDWithUHFUART.getInstance()
        } catch (ex: java.lang.Exception) {
            showToast(ex.message)

            return
        }

//        if (mReader != null) {
//            // Show progress dialog on the main thread
//            mypDialog = ProgressDialog(this).apply { // Use requireContext() for Fragments
//                setProgressStyle(ProgressDialog.STYLE_SPINNER)
//                setMessage("init...")
//                setCanceledOnTouchOutside(false)
//                show()
//            }
//
//            // Launch a coroutine in the lifecycleScope of the Fragment/Activity
//            // This coroutine will be cancelled when the Fragment/Activity is destroyed
//            lifecycleScope.launch {
//                val result = withContext(Dispatchers.IO) {
//                    // Perform the potentially long-running operation on a background thread (IO dispatcher)
//                    // Assuming mReader.init returns a Boolean and requires a Context (like your original `this@MainActivity`)
//                    // You might need to adjust `mReader.init` if it expects a specific Context type
//                    // For a Fragment, use requireActivity() or requireContext()
//                    // If mReader.init truly takes a MainActivity, then you'd need `activity as MainActivity`
//                    // Let's assume it can take a standard Context
//                    mReader?.init(applicationContext) // Replace with your actual mReader instance
//                }
//
//                // After the background task completes, switch back to the Main thread
//                // to update UI and dismiss the dialog
//                mypDialog?.dismiss() // Use dismiss() instead of cancel() for ProgressDialog
//
//                if (result == null || !result) {
//                    Toast.makeText(this@MainActivity, "init fail", Toast.LENGTH_SHORT).show()
//                } else {
//                    Toast.makeText(this@MainActivity, "init success", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
    }

    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SELECT_DEVICE ->                 //When the DeviceListActivity return, with the selected device address
                if (resultCode == RESULT_OK && data != null) {
                    if (uhf?.connectStatus == ConnectionStatus.CONNECTED) {
                        disconnect(true)
                    }
                    val deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE)
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
                    if (data.getBooleanExtra("isSearch", true)) {
                        uhf?.startScanBTDevices { bluetoothDevice, i, bytes -> }
                        SystemClock.sleep(2000)
                        uhf?.stopScanBTDevices()
                    }
                    showToast(String.format(
                        "%s - %s\nconnecting",
                        mDevice!!.name,
                        deviceAddress
                    ))
//                    tvAddress.text = String.format(
//                        "%s - %s\nconnecting",
//                        mDevice!!.name,
//                        deviceAddress
//                    )
                    connect(deviceAddress)
                }

            REQUEST_ENABLE_BT -> if (resultCode == RESULT_OK) {
                showToast("Bluetooth has turned on ")
                initConnect()
            } else {
                showToast("Problem in BT Turning ON ")
            }
            else -> {}
        }
    }

    protected fun shouldShowDisconnected(): Boolean {
        return mIsActiveDisconnect || mReConnectCount == 0
    }

    protected fun reConnect(deviceAddress: String) {
        if (!mIsActiveDisconnect && mReConnectCount > 0) {
            connect(deviceAddress)
            mReConnectCount--
        }
    }

    fun resetDisconnectTime() {
        timeCountCur = SPUtils.getInstance(requireContext()).getSPLong(SPUtils.DISCONNECT_TIME, 0)
        if (timeCountCur > 0) {
            formatConnectButton(timeCountCur)
        }
    }

    open fun formatConnectButton(time: Long) {
        // Default: no-op. Override in child fragments if needed.
    }

    protected fun startDisconnectTimer(time: Long) {
        timeCountCur = time
        timerTask = DisconnectTimerTask()
        mDisconnectTimer.schedule(timerTask, 0, period)
    }

    fun cancelDisconnectTimer() {
        timeCountCur = 0
        timerTask?.cancel()
        timerTask = null
    }

    fun saveConnectedDevice(address: String, name: String) {
        val list: MutableList<Array<String>> = FileUtils.readXmlList()
        for (k in list.indices) {
            if (address == list[k][0]) {
                list.remove(list[k])
                break
            }
        }
        list.add(0, arrayOf(address, name))
        FileUtils.saveXmlList(list)
    }

    // Override in child fragments to check if scanning is active (prevents disconnect timer firing)
    open fun isScanning(): Boolean = false

    inner class DisconnectTimerTask : TimerTask() {
        override fun run() {
            val msg: Message = mHandler.obtainMessage(RUNNING_DISCONNECT_TIMER, timeCountCur)
            mHandler.sendMessage(msg)
            if (isScanning()) {
                resetDisconnectTime()
            } else if (timeCountCur <= 0) {
                disconnect(true)
            }
            timeCountCur -= period
        }
    }

    @SuppressLint("MissingPermission")
    override fun getStatus(connectionStatus: ConnectionStatus, device1: Any?) {
        activity?.runOnUiThread {
            val device = device1 as BluetoothDevice?
            remoteBTName = ""
            remoteBTAdd = ""

            if (connectionStatus == ConnectionStatus.CONNECTED) {
                remoteBTName = device?.name ?: ""
                remoteBTAdd = device?.address ?: ""
                sessionManager.setDeviceAddress(remoteBTAdd)

                if (shouldShowDisconnected()) showToast(R.string.connect_success)

                timeCountCur = SPUtils.getInstance(requireContext()).getSPLong(SPUtils.DISCONNECT_TIME, 0)
                if (timeCountCur > 0) startDisconnectTimer(timeCountCur)
                else formatConnectButton(timeCountCur)

                if (!TextUtils.isEmpty(remoteBTAdd)) saveConnectedDevice(remoteBTAdd, remoteBTName)

                mIsActiveDisconnect = false
                mReConnectCount = RECONNECT_NUM

            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                isKeyDownUP = false
                cancelDisconnectTimer()
                formatConnectButton(timeCountCur)

                if (device != null) {
                    remoteBTName = device.name ?: ""
                    remoteBTAdd = device.address
                }

                if (shouldShowDisconnected()) showToast(R.string.disconnect)

                val reconnect = SPUtils.getInstance(requireContext()).getSPBoolean(SPUtils.AUTO_RECONNECT, false)
                if (mDevice != null && reconnect) reConnect(mDevice!!.address)
            }

            for (iConnectStatus in connectStatusList) {
                iConnectStatus.getStatus(connectionStatus)
            }

            onConnectionStateChange(connectionStatus, device)
        }
    }

    // Override in child fragments to update fragment-specific views on connection state change
    open fun onConnectionStateChange(connectionStatus: ConnectionStatus, device: BluetoothDevice?) {}

    override fun onDestroyView() {
        super.onDestroyView()

        // Stop inventory scanning if running
        connectionStatusCallback = null
        try {
            if (mIsScanning) {
                mIsScanning = false
                onStopScanning()
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Error stopping scanning", e)
        }

        // Cancel disconnect timer tasks
        try {
            cancelDisconnectTimer()
        } catch (e: Exception) {
            Log.e("BaseFragment", "Error cancelling disconnect timer", e)
        }

        // Disconnect UHF if connected
        try {
            if (uhf != null && uhf?.connectStatus == ConnectionStatus.CONNECTED) {
                disconnect(true)
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Error while disconnecting UHF", e)
        }

        // Allow child fragments to clean up their own UHF-related resources
        onDestroyUHF()
    }

    // Override in child fragments to stop scanning (e.g., stopInventory)
    open fun onStopScanning() {}

    // Override in child fragments to release additional UHF resources
    open fun onDestroyUHF() {}
}