package com.inventory.app.mobile.fragments

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.FLAG_FAIL
import com.inventory.app.mobile.FLAG_START
import com.inventory.app.mobile.FLAG_STOP
import com.inventory.app.mobile.FLAG_SUCCESS
import com.inventory.app.mobile.FLAG_UHFINFO
import com.inventory.app.mobile.FLAG_UHFINFO_LIST
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.SimpleItemAdapter
import com.inventory.app.mobile.databinding.FragmentShipmentBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetItemByEpcRequest
import com.inventory.app.mobile.utils.rest.requests.ShipmentInitRequest
import com.inventory.app.mobile.utils.rest.requests.TransferConfirmRequest
import com.inventory.app.mobile.utils.rest.requests.TransferUploadRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByEpcResponse
import com.inventory.app.mobile.utils.rest.response.ShipmentInitResponse
import com.inventory.app.mobile.utils.rest.response.TransferUploadResponse
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.interfaces.ConnectionStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ShipmentFragment : BaseFragment(), SimpleItemAdapter.OnItemClick {
    companion object {
        private const val TAG = "ShipmentFragment"
    }

    // Correct way to declare NavArgs
    private val args: ShipmentFragmentArgs by navArgs()

    private var _binding: FragmentShipmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx: AppCtx

    private lateinit var mAdapter: SimpleItemAdapter
    private var isConfirmAllowed = false

    private val lock = Any()

    private var mScannedEpc: ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc: ArrayList<String> = ArrayList() //just scanned epc

    private val debugEpc = arrayOf("00000000", "32364330303339FF")

    private var mId: Long = 0L

    private val itemByEpcListener = object : Callback<GetItemByEpcResponse?> {
        override fun onResponse(
            call: Call<GetItemByEpcResponse?>,
            response: Response<GetItemByEpcResponse?>
        ) {
            val getItemByPinResponse = response.body()
            if (getItemByPinResponse != null) {
                if (getItemByPinResponse.result == BaseResponse.RESULT_OK && getItemByPinResponse.data != null) {
                    synchronized(lock) {
                        val prevCount = mAdapter.itemCount
                        getItemByPinResponse.data!!.forEach { row -> mAdapter.addData(row) }
                        val insertCount = mAdapter.itemCount - prevCount
                        if (insertCount > 0) {
                            mAdapter.notifyItemRangeChanged(prevCount, insertCount)
                        }
                        binding.textCount.text = mAdapter.itemCount.toString()
                    }
                } else {
                    Toast.makeText(appCtx, "Error : " + getItemByPinResponse.message, Toast.LENGTH_SHORT).show()
                }
            } else {
                //handle failure ?
                Toast.makeText(appCtx, "Error null response", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onFailure(call: Call<GetItemByEpcResponse?>, t: Throwable) {
            Toast.makeText(appCtx, "Failure to get item info!", Toast.LENGTH_SHORT).show()
        }
    }

    private val postWork = Runnable {
        while (mIsScanning) {
            Thread.sleep(300)
            val epcList = ArrayList<String>()
            synchronized(lock) {
                mProcessingEpc.forEach { epc ->
                    if (!mScannedEpc.contains(epc)) epcList.add(epc)
                }
                mProcessingEpc.clear()
                mScannedEpc.addAll(epcList)
            }
            if (epcList.isNotEmpty()) {
                Log.d(TAG, "------------ getItemByEpc ------------")
                Log.d(TAG, "epcList : ${Gson().toJson(epcList)}")
                val request = apiInterface.getItemByEpc(
                    "Bearer " + sessionManager.getSessionId(),
                    GetItemByEpcRequest(epcList)
                )
                request.enqueue(itemByEpcListener)
            }
        }
    }

    private fun toggleScan() {
        mIsScanning = !mIsScanning
        if (mIsScanning) {
            if (!Params.DEBUG) {
                TagThread().start()
            } else {
                simulateScanRfid()
            }
            binding.buttonUpload.isEnabled = false
            binding.btnMore.isEnabled = false
            binding.buttonScan.text = "Stop Scan"
            val color = ContextCompat.getColor(appCtx, R.color.accent)
            binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
            Thread(postWork).start()
        } else {
            stopInventory()
            binding.buttonUpload.isEnabled = true
            binding.btnMore.isEnabled = true
            binding.buttonScan.text = "Start Scan"
            val color = ContextCompat.getColor(appCtx, R.color.primary)
            binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun simulateScanRfid() {
        GlobalScope.launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                debugEpc.forEach { epc -> updateScanData(epc) }
            }
        }
    }

    private fun updateScanData(epc: String) {
        synchronized(lock) {
            if (epc.isEmpty()) return
            if (mScannedEpc.contains(epc) || mProcessingEpc.contains(epc)) return
            mProcessingEpc.add(epc)
        }
    }

    private fun stopInventory() {
        mScannedEpc.clear()
        mIsScanning = false
        if (uhf != null) {
            uhf?.stopInventory()
        } else {
            Toast.makeText(mainActivity, "Stop scanning inventory fail!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun ReaderOnKeyDwon() {
        toggleScan()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun init() {
        val request = apiInterface.shipmentInit(
            "Bearer " + sessionManager.getSessionId(),
            ShipmentInitRequest(mId)
        )
        request.enqueue(object : Callback<ShipmentInitResponse?> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(
                call: Call<ShipmentInitResponse?>,
                response: Response<ShipmentInitResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        binding.textNo.text = result.no
                        binding.textLocation.text = result.locationName
                        mAdapter.initData(result.items, true)
                        mAdapter.notifyDataSetChanged()
                        binding.textCount.text = mAdapter.itemCount.toString()
                        mScannedEpc.clear()
                        mProcessingEpc.clear()
                    } else {
                        Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ShipmentInitResponse?>, t: Throwable) {}
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentShipmentBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appCtx = AppCtx.applicationContext()
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        isConfirmAllowed = sessionManager.getMenu().contains(Params.MENU_SHIPMENT_CONFIRM)
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        mAdapter = SimpleItemAdapter(appCtx, ArrayList<SimpleItem>(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = mAdapter

        binding.buttonScan.setOnClickListener { toggleScan() }
        binding.btnMore.setOnClickListener { v -> showPopUp(v) }
        binding.buttonUpload.setOnClickListener { uploadData() }
        binding.textPower.setOnClickListener { showPowerDialog() }
        mId = args.id
        init()

        binding.tvAddress.setOnClickListener {
            if (mIsScanning) {
                showToast(R.string.title_stop_read_card)
            } else if (uhf?.connectStatus == ConnectionStatus.CONNECTING) {
                showToast(R.string.connecting)
            } else if (uhf?.connectStatus == ConnectionStatus.CONNECTED) {
                disconnect(true)
            } else {
                sessionManager.setDeviceAddress("")
                search()
            }
        }
        if (!Params.DEBUG) {
            binding.tvAddress.setText(R.string.connecting)
            initConnect()
        } else {
            binding.tvAddress.setText(R.string.connect_success)
            binding.buttonScan.isEnabled = true
        }
    }

    override fun onPowerUpdated() {
        super.onPowerUpdated()
        binding.textPower.text = "$radioPower dB"
    }

    private fun confirm() {
        val simpleItems = mAdapter.getDataToUpload()
        if (simpleItems.isNotEmpty()) {
            showToast("Data upload is required before confirmation.")
            return
        }
        val request = apiInterface.shipmentConfirm(
            "Bearer " + sessionManager.getSessionId(),
            TransferConfirmRequest(mId)
        )
        request.enqueue(object : Callback<BaseResponse?> {
            override fun onResponse(call: Call<BaseResponse?>, response: Response<BaseResponse?>) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        showConfirmCompleteDialog()
                    }
                } else {
                    Toast.makeText(requireContext(), "Confirm fail! Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<BaseResponse?>, t: Throwable) {}
        })
    }

    private fun uploadData() {
        val itemIds = ArrayList<Long>()
        val simpleItems = mAdapter.getDataToUpload()
        for (item in simpleItems) {
            itemIds.add(item.id)
        }
        val request = apiInterface.shipmentUpload(
            "Bearer " + sessionManager.getSessionId(),
            TransferUploadRequest(mId, itemIds)
        )
        request.enqueue(object : Callback<TransferUploadResponse?> {
            override fun onResponse(
                call: Call<TransferUploadResponse?>,
                response: Response<TransferUploadResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        result.items.forEach { item -> mAdapter.ok.add(item.no) }
                        mAdapter.notifyDataSetChanged()
                        binding.textCount.text = mAdapter.itemCount.toString()
                        binding.buttonUpload.isEnabled = false
                        showUploadCompleteDialog()
                    }
                } else {
                    Toast.makeText(requireContext(), "Upload fail! Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<TransferUploadResponse?>, t: Throwable) {}
        })
    }

    private fun showRefreshConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())

        // Set the dialog title
        builder.setTitle("Confirm Refresh")

        // Set the dialog message
        builder.setMessage("Are you sure you want to refresh? All scanned data will be reset and cannot be recovered.")

        // Set the Positive button (Yes/Confirm)
        builder.setPositiveButton("Refresh Anyway") { dialog: DialogInterface, _ ->
            // User clicked Refresh button
            init()
            dialog.dismiss() // Dismiss the dialog
        }

        // Set the Negative button (No/Cancel)
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _ ->
            // User clicked Cancel button
            Toast.makeText(requireContext(), "Refresh cancelled.", Toast.LENGTH_SHORT).show()
            dialog.cancel() // Dismiss the dialog
        }

        // Optional: Set a Neutral button (if needed, less common for simple confirmations)
        // builder.setNeutralButton("Learn More") { dialog: DialogInterface, which: Int ->
        //     // Handle "Learn More" action
        // }
        // Create the AlertDialog
        val dialog: AlertDialog = builder.create()

        // Show the dialog
        dialog.show()
    }

    private fun showConfirmCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirm Completed")
        builder.setMessage("Confirm successful.")
        builder.setPositiveButton("Ok") { _, _ ->
            findNavController().navigate(R.id.action_shipmentFragment_to_homeFragment)
        }
        builder.create().show()
    }

    private fun showUploadCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Upload Completed")
        if (isConfirmAllowed) {
            builder.setMessage("Upload data successful! Finalize and Confirm shipment?")
            builder.setPositiveButton("Yes, Confirm") { _, _ -> confirm() }
            builder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
        } else {
            builder.setMessage("Upload data successful!")
            builder.setPositiveButton("Ok") { dialog, _ -> dialog.dismiss() }
        }
        builder.create().show()
    }

    private fun showPopUp(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.popup_menu, popup.menu)
        if (!isConfirmAllowed) {
            popup.menu.removeItem(R.id.action_confirm)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_confirm -> {
                    confirm()
                    true
                }
                R.id.action_refresh -> {
                    showRefreshConfirmationDialog()
                    true
                }
                R.id.action_exit -> {
                    findNavController().navigate(R.id.action_shipmentFragment_to_homeFragment)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onClick(position: Int, view: View, item: SimpleItem) {}

    override fun isScanning(): Boolean = mIsScanning

    override fun onConnectionStateChange(connectionStatus: ConnectionStatus, device: BluetoothDevice?) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            var address = remoteBTName
            if (address.isNotEmpty()) address += "\n"
            address += remoteBTAdd
            binding.tvAddress.text = address
            binding.buttonScan.isEnabled = true
        } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
            binding.buttonScan.isEnabled = false
            binding.tvAddress.text = if (device != null) {
                String.format("%s - %s\ndisconnected", remoteBTName, remoteBTAdd)
            } else {
                "disconnected"
            }
        }
    }

    @Synchronized
    private fun getUHFInfo(): List<UHFTAGInfo>? {
        return uhf!!.readTagFromBufferList()
    }

    inner class TagThread : Thread() {
        override fun run() {
            val msg: Message = mHandlerTag.obtainMessage(FLAG_START)
            Log.i(TAG, "startInventoryTag() 1")
            if (!uhf!!.setPower(radioPower)) {
                activity?.runOnUiThread { showToast("Set power failed") }
            }
            if (!uhf!!.setEPCMode()) {
                activity?.runOnUiThread { showToast("Set mode failed") }
            }
            if (uhf!!.startInventoryTag()) {
                msg.arg1 = FLAG_SUCCESS
            } else {
                msg.arg1 = FLAG_FAIL
                mIsScanning = false
            }
            mHandlerTag.sendMessage(msg)
            while (mIsScanning) {
                val list: List<UHFTAGInfo>? = getUHFInfo()
                if (list.isNullOrEmpty()) {
                    SystemClock.sleep(1)
                    Log.i(TAG, "No Tag found")
                } else {
                    mainActivity?.playSound(1)
                    mHandlerTag.sendMessage(mHandlerTag.obtainMessage(FLAG_UHFINFO_LIST, list))
                }
            }
            stopInventory()
        }
    }

    val mHandlerTag = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                FLAG_STOP -> if (msg.arg1 == FLAG_SUCCESS) {
                    binding.buttonScan.setText(R.string.start_scan)
                } else {
                    mainActivity?.playSound(2)
                    Toast.makeText(requireActivity(), "Gagal stop scan!", Toast.LENGTH_SHORT).show()
                }

                FLAG_UHFINFO_LIST -> {
                    val list = msg.obj as ArrayList<UHFTAGInfo>
                    list.forEach { tag -> updateScanData(tag.epc) }
                }

                FLAG_START -> if (msg.arg1 == FLAG_SUCCESS) {
                    binding.buttonScan.setText(R.string.stop_scan)
                } else {
                    mainActivity?.playSound(2)
                }

                FLAG_UHFINFO -> {
                    val info = msg.obj as UHFTAGInfo
                    updateScanData(info.epc)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}