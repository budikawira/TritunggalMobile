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
import androidx.fragment.app.Fragment
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
import com.inventory.app.mobile.databinding.FragmentPlacementBinding
import com.inventory.app.mobile.databinding.FragmentTransferBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetItemByEpcRequest
import com.inventory.app.mobile.utils.rest.requests.TransferConfirmRequest
import com.inventory.app.mobile.utils.rest.requests.TransferInitRequest
import com.inventory.app.mobile.utils.rest.requests.TransferUploadRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByEpcResponse
import com.inventory.app.mobile.utils.rest.response.TransferInitResponse
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
import kotlin.getValue

/**
 * A simple [Fragment] subclass.
 * Use the [PlacementFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PlacementFragment : BaseFragment(), SimpleItemAdapter.OnItemClick {
    companion object {
        private const val TAG = "PlacementFragment"
    }

    // Correct way to declare NavArgs
    private val args: PlacementFragmentArgs by navArgs()

    private var _binding : FragmentPlacementBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx : AppCtx

    private lateinit var mAdapter: SimpleItemAdapter
    private var isConfirmAllowed = false

    private val lock = Any()

    private var mScannedEpc : ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc : ArrayList<String> = ArrayList() //just scanned epc

    private val debugEpc = arrayOf("00000000","32364330303339FF")

    private var mId : Long = 0L

    private val itemByEpcListener = object : Callback<GetItemByEpcResponse?> {
        override fun onResponse(
            call: Call<GetItemByEpcResponse?>,
            response: Response<GetItemByEpcResponse?>
        ) {
            var getItemByPinResponse = response.body()
            if (getItemByPinResponse != null) {
                if (getItemByPinResponse.result == BaseResponse.RESULT_OK && getItemByPinResponse.data != null) {
                    synchronized(lock)
                    {
                        var prevCount = mAdapter.itemCount
                        getItemByPinResponse.data!!.forEach { row ->
                            mAdapter.addData(row)
                        }
                        var insertCount = mAdapter.itemCount - prevCount
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

        override fun onFailure(
            call: Call<GetItemByEpcResponse?>,
            t: Throwable
        ) {
            Toast.makeText(appCtx, "Failure to get item info!", Toast.LENGTH_SHORT).show()
        }
    }

    private var postWork = Runnable {
        while (mIsScanning) {
            Thread.sleep(300)
            var epcList = ArrayList<String>()
            synchronized(lock) {
                mProcessingEpc.forEach { epc ->
                    if (!mScannedEpc.contains(epc)) {
                        epcList.add(epc)
                    }
                }
                mProcessingEpc.clear()
                mScannedEpc.addAll(epcList)
            }

            if (epcList.isNotEmpty()) {
                Log.d(TAG, "------------ getItemByEpc ------------")
                Log.d(TAG, "epcList : ${Gson().toJson(epcList)}")
                var request = apiInterface.getItemByEpc(
                    "Bearer " + sessionManager.getSessionId(),
                    GetItemByEpcRequest(epcList))
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
            val t = Thread(postWork)
            t.start()

//            if (mainActivity?.mReader != null) {
//                mainActivity?.mReader?.setInventoryCallback { uhftagInfo ->
//                    val msg = handler.obtainMessage()
//                    msg.obj = uhftagInfo
//                    msg.what = 1
//                    handler.sendMessage(msg)
//                    mainActivity?.playSound(1)
//                }
//                mainActivity!!.mReader!!.power = radioPower
//                if (mainActivity!!.mReader!!.startInventoryTag()) {
//                    handler.sendEmptyMessageDelayed(2, 10)
//
//                    binding.buttonUpload.isEnabled = false
//                    binding.btnMore.isEnabled = false
//                    binding.buttonScan.text = "Stop Scan"
//                    val color = ContextCompat.getColor(appCtx, R.color.accent)
//                    binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
//                    //if (binding.spinFilter.selectedItemPosition != 0) {
//                    var t = Thread(postWork)
//                    t.start()
//                    //}
//                } else {
//                    stopInventory()
//                    mIsScanning = false
//                }
//            } else if (Params.DEBUG) {
//                handler.sendEmptyMessageDelayed(2, 10)
//
//                binding.buttonUpload.isEnabled = false
//                binding.btnMore.isEnabled = false
//                binding.buttonScan.text = "Stop Scan"
//                val color = ContextCompat.getColor(appCtx, R.color.accent)
//                binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
//                //if (binding.spinFilter.selectedItemPosition != 0) {
//                var t = Thread(postWork)
//                t.start()
//                simulateScanRfid()
//            }
        } else {
            stopInventory()
            binding.buttonUpload.isEnabled = true
            binding.btnMore.isEnabled = true
            binding.buttonScan.text = "Start Scan"
            val color = ContextCompat.getColor(appCtx, R.color.primary)
            binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    @kotlin.OptIn(DelicateCoroutinesApi::class)
    private fun simulateScanRfid() {
        GlobalScope.launch {
            delay(5000) // Pause for 5 seconds
            withContext(Dispatchers.Main) {
                debugEpc.forEach { epc ->
                    updateScanData(epc)
                }
            }
        }
    }


    private fun updateScanData(epc: String) {
        synchronized(lock) {
            if (epc.isEmpty()) return

            if (mScannedEpc.contains(epc) ||
                mProcessingEpc.contains(epc)) return
            mProcessingEpc.add(epc)

        }
    }

    private fun stopInventory() {
        mScannedEpc.clear()
        mIsScanning = false
        if (uhf != null) {
            uhf?.stopInventory()
        } else {
            Toast.makeText(mainActivity, "Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
        }
//        if (mainActivity?.mReader != null) {
//            mainActivity?.mReader?.stopInventory()
//        } else {
//            Toast.makeText(mainActivity, "Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
//        }
    }

    override fun ReaderOnKeyDwon() {
        toggleScan()
    }

    override fun onPause() {
        super.onPause()
//        if (mainActivity?.mReader != null && mainActivity!!.mReader!!.isInventorying) {
//            if (!mainActivity!!.mReader!!.stopInventory()) {
//                Toast.makeText(mainActivity, "onPause :: Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
//            }
//        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentPlacementBinding.inflate(inflater, container, false)

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appCtx = AppCtx.applicationContext()
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        isConfirmAllowed = sessionManager.getMenu().contains(Params.MENU_PLACEMENT_CONFIRM)

        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        mAdapter = SimpleItemAdapter(appCtx, ArrayList<SimpleItem>(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = mAdapter

        binding.buttonScan.setOnClickListener {
            toggleScan()
        }
        binding.btnMore.setOnClickListener { view -> showPopUp(view) }
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
                sessionManager.setDeviceAddress("");
                search()
            }
        }
        binding.tvAddress.setText(R.string.connecting)
        initConnect()
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
        val request = apiInterface.placementConfirm(
            "Bearer " + sessionManager.getSessionId(),
            TransferConfirmRequest(
                mId))
        request.enqueue(object : Callback<BaseResponse?> {
            override fun onResponse(
                call: Call<BaseResponse?>,
                response: Response<BaseResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        showConfirmCompleteDialog()
                    }
                } else {
                    Toast.makeText(requireContext(),
                        "Confirm fail! Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(
                call: Call<BaseResponse?>,
                t: Throwable
            ) {

            }

        })
    }

    private fun uploadData() {
        var itemIds = ArrayList<Long>()
        var simpleItems = mAdapter.getDataToUpload()
        for (item in simpleItems) {
            itemIds.add(item.id)
        }
        var request = apiInterface.placementUpload(
            "Bearer " + sessionManager.getSessionId(),
            TransferUploadRequest(
                mId, itemIds))
        request.enqueue(object : Callback<TransferUploadResponse?> {
            override fun onResponse(
                call: Call<TransferUploadResponse?>,
                response: Response<TransferUploadResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        result.items.forEach { item ->
                            mAdapter.ok.add(item.no)
                        }
                        mAdapter.notifyDataSetChanged()
                        binding.textCount.text = mAdapter.itemCount.toString()
                        binding.buttonUpload.isEnabled = false
                        showUploadCompleteDialog()
                    }
                } else {
                    Toast.makeText(requireContext(),
                        "Upload fail! Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(
                call: Call<TransferUploadResponse?>,
                t: Throwable
            ) {

            }

        })
    }

    private fun init() {
        var request = apiInterface.placementInit(
            "Bearer " + sessionManager.getSessionId(),
            TransferInitRequest(mId))
        request.enqueue(object: Callback<TransferInitResponse?> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(
                call: Call<TransferInitResponse?>,
                response: Response<TransferInitResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        binding.textNo.text = result.no
                        binding.textDest.text = result.destLocationName
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

            override fun onFailure(
                call: Call<TransferInitResponse?>,
                t: Throwable
            ) {
            }
        })
    }

    override fun onClick(
        position: Int,
        view: View,
        item: SimpleItem
    ) {
        //pop up menu to remove if the item is not yet uploaded
    }


    private fun showRefreshConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())

        // Set the dialog title
        builder.setTitle("Confirm Refresh")

        // Set the dialog message
        builder.setMessage("Are you sure you want to refresh? All scanned data will be reset and cannot be recovered.")

        // Set the Positive button (Yes/Confirm)
        builder.setPositiveButton("Refresh Anyway") { dialog: DialogInterface, which: Int ->
            // User clicked Refresh button
            init()
            dialog.dismiss() // Dismiss the dialog
        }

        // Set the Negative button (No/Cancel)
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
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

        // Set the dialog title
        builder.setTitle("Upload Completed")

        builder.setMessage("Confirm successful.")

        // Set the Positive button (Yes/Confirm)
        builder.setPositiveButton("Ok") { dialog: DialogInterface, which: Int ->
            findNavController().navigate(R.id.action_placementFragment_to_homeFragment)
        }

        // Create the AlertDialog
        val dialog: AlertDialog = builder.create()
        // Show the dialog
        dialog.show()
    }

    private fun showUploadCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())

        // Set the dialog title
        builder.setTitle("Upload Completed")

        if (isConfirmAllowed) {
            // Set the dialog message
            builder.setMessage("Upload data successful! Finalize and Confirm placement?")

            // Set the Positive button (Yes/Confirm)
            builder.setPositiveButton("Yes, Confirm") { dialog: DialogInterface, which: Int ->
                confirm()
            }

            builder.setNegativeButton("No") {dialog: DialogInterface, _: Int -> dialog.dismiss() }
        } else {
            // Set the dialog message
            builder.setMessage("Upload data successful!")

            // Set the Positive button (Yes/Confirm)
            builder.setPositiveButton("Ok") { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
            }
        }

        // Create the AlertDialog
        val dialog: AlertDialog = builder.create()
        // Show the dialog
        dialog.show()
    }

    private fun showPopUp(view : View) {
        // Create the PopupMenu
        val popup = PopupMenu(requireContext(), view) // 'this' is Context

        // Inflate the menu
        popup.menuInflater.inflate(R.menu.popup_menu, popup.menu)
        if (!isConfirmAllowed) {
            popup.menu.removeItem(R.id.action_confirm)
        }
        // Set click listener
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
                    findNavController().navigate(R.id.action_placementFragment_to_homeFragment)
                    true
                }
                else -> false
            }
        }

        // Show the menu
        popup.show()
    }

    @Synchronized
    private fun getUHFInfo(): List<UHFTAGInfo>? {

        //旧主板才需要调用readTagFromBufferList_EpcTidUser 输出 RSSI
        //return uhf?.readTagFromBufferList_EpcTidUser()
        return uhf!!.readTagFromBufferList()
    }

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
                //mStrTime = System.currentTimeMillis()
                msg.arg1 = FLAG_SUCCESS
            } else {
                msg.arg1 = FLAG_FAIL
                mIsScanning = false
            }
            mHandlerTag.sendMessage(msg)
            //var startTime = System.currentTimeMillis()
            while (mIsScanning) {
                val list: List<UHFTAGInfo>? = getUHFInfo()
                if (list.isNullOrEmpty()) {
                    SystemClock.sleep(1)
                    Log.i(TAG, "No Tag found")
                } else {
                    mainActivity?.playSound(1)
                    mHandlerTag.sendMessage(mHandlerTag.obtainMessage(FLAG_UHFINFO_LIST, list))
                }
//                if (System.currentTimeMillis() - startTime > 10) {
//                    startTime = System.currentTimeMillis()
//                    mHandlerTag.sendEmptyMessage(FLAG_UPDATE_TIME)
//                }
//                //-------------------------
//                if (System.currentTimeMillis() - mStrTime >= maxRunTime) {
//                    isScanning = false
//                    break
//                }
                //--------------------------------
            }
            stopInventory()
        }
    }

    val mHandlerTag = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                FLAG_STOP -> if (msg.arg1 == FLAG_SUCCESS) {
                    //停止成功
                    binding.buttonScan.setText(R.string.start_scan)
//                    btClear.setEnabled(true)
//                    btStop.setEnabled(false)
//                    InventoryLoop.setEnabled(true)
//                    btInventory.setEnabled(true)
//                    btInventoryPerMinute.setEnabled(true)
                } else {
                    //停止失败
                    mainActivity?.playSound(2)
                    Toast.makeText(
                        requireActivity(),
                        "Gagal stop scan!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                FLAG_UHFINFO_LIST -> {
                    val list = msg.obj as ArrayList<UHFTAGInfo>
                    //addEPCToList(list)
                    list.forEach { tag ->
                        updateScanData(tag.epc)
                    }
                }

                FLAG_START -> if (msg.arg1 == FLAG_SUCCESS) {
                    //开始读取标签成功
                    binding.buttonScan.setText(R.string.stop_scan)
                } else {
                    //开始读取标签失败
                    mainActivity?.playSound(2)
                }

                FLAG_UHFINFO -> {
                    val info = msg.obj as UHFTAGInfo
                    val list1 = java.util.ArrayList<UHFTAGInfo>()
                    list1.add(info)
                    updateScanData(info.epc)
                    //addEPCToList(list1)
                }
            }
        }
    }
}