package com.inventory.app.mobile.fragments

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.SimpleItemAdapter
import com.inventory.app.mobile.databinding.FragmentShipmentBinding
import com.inventory.app.mobile.databinding.FragmentTransferBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetItemByEpcRequest
import com.inventory.app.mobile.utils.rest.requests.ShipmentInitRequest
import com.inventory.app.mobile.utils.rest.requests.ShipmentUploadRequest
import com.inventory.app.mobile.utils.rest.requests.TransferInitRequest
import com.inventory.app.mobile.utils.rest.requests.TransferUploadRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByEpcResponse
import com.inventory.app.mobile.utils.rest.response.ShipmentInitResponse
import com.inventory.app.mobile.utils.rest.response.ShipmentUploadResponse
import com.inventory.app.mobile.utils.rest.response.TransferInitResponse
import com.inventory.app.mobile.utils.rest.response.TransferUploadResponse
import com.rscja.deviceapi.entity.UHFTAGInfo
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

class ShipmentFragment : BaseFragment(), SimpleItemAdapter.OnItemClick {
    companion object {
        private const val TAG = "ShipmentFragment"
    }

    // Correct way to declare NavArgs
    private val args: ShipmentFragmentArgs by navArgs()

    private var _binding : FragmentShipmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx : AppCtx

    private lateinit var mAdapter: SimpleItemAdapter

    private val lock = Any()
    private var mIsScanning = false

    private var mScannedEpc : ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc : ArrayList<String> = ArrayList() //just scanned epc

    private val debugEpc = arrayOf("32364135323932FF","32354130393939")

    private var mId : Long = 0L

    private var handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == 1) {
                val info = msg.obj as UHFTAGInfo
                Log.i("::handleMessage", "SoFragment.info=$info")
                val tid = info.tid
                val epc = info.epc
                val user = info.user
                Log.i(
                    TAG,
                    "tid=" + tid + " epc=" + epc + " user=" + user + " info=" + info.rssi
                )
                updateScanData(epc)
            } else if (msg.what == 2) {
                this.removeMessages(2)
            }
        }
    }

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
                var request = apiInterface.getItemByEpc(GetItemByEpcRequest(epcList))
                request.enqueue(itemByEpcListener)
            }
        }
    }

    private fun toggleScan() {
        mIsScanning = !mIsScanning
        if (mIsScanning) {
            if (mainActivity?.mReader != null) {
                mainActivity?.mReader?.setInventoryCallback { uhftagInfo ->
                    val msg = handler.obtainMessage()
                    msg.obj = uhftagInfo
                    msg.what = 1
                    handler.sendMessage(msg)
                    mainActivity?.playSound(1)
                }
                mainActivity!!.mReader!!.power = radioPower
                if (mainActivity!!.mReader!!.startInventoryTag()) {
                    handler.sendEmptyMessageDelayed(2, 10)

                    binding.buttonUpload.isEnabled = false
                    binding.btnMore.isEnabled = false
                    binding.buttonScan.text = "Stop Scan"
                    val color = ContextCompat.getColor(appCtx, R.color.accent)
                    binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
                    //if (binding.spinFilter.selectedItemPosition != 0) {
                    var t = Thread(postWork)
                    t.start()
                    //}
                } else {
                    stopInventory()
                    mIsScanning = false
                }
            } else if (Params.DEBUG) {
                handler.sendEmptyMessageDelayed(2, 10)

                binding.buttonUpload.isEnabled = false
                binding.btnMore.isEnabled = false
                binding.buttonScan.text = "Stop Scan"
                val color = ContextCompat.getColor(appCtx, R.color.accent)
                binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
                //if (binding.spinFilter.selectedItemPosition != 0) {
                var t = Thread(postWork)
                t.start()
                simulateScanRfid()
            }
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
        if (mainActivity?.mReader != null) {
            mainActivity?.mReader?.stopInventory()
        } else {
            Toast.makeText(mainActivity, "Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun ReaderOnKeyDwon() {
        toggleScan()
    }

    override fun onPause() {
        super.onPause()
        if (mainActivity?.mReader != null && mainActivity!!.mReader!!.isInventorying) {
            if (!mainActivity!!.mReader!!.stopInventory()) {
                Toast.makeText(mainActivity, "onPause :: Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun init() {
        var request = apiInterface.shipmentInit(ShipmentInitRequest(mId))
        request.enqueue(object: Callback<ShipmentInitResponse?> {
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

            override fun onFailure(
                call: Call<ShipmentInitResponse?>,
                t: Throwable
            ) {

            }

        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentShipmentBinding.inflate(inflater, container, false)

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appCtx = AppCtx.applicationContext()
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
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
    }

    override fun onPowerUpdated() {
        super.onPowerUpdated()
        binding.textPower.text = "$radioPower dB"
    }

    private fun uploadData() {
        var itemIds = ArrayList<Long>()
        var simpleItems = mAdapter.getDataToUpload()
        for (item in simpleItems) {
            itemIds.add(item.id)
        }
        var request = apiInterface.shipmentUpload(ShipmentUploadRequest(
            mId, itemIds))
        request.enqueue(object : Callback<ShipmentUploadResponse?> {
            override fun onResponse(
                call: Call<ShipmentUploadResponse?>,
                response: Response<ShipmentUploadResponse?>
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
                call: Call<ShipmentUploadResponse?>,
                t: Throwable
            ) {

            }

        })
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

    private fun showUploadCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())

        // Set the dialog title
        builder.setTitle("Upload Completed")

        // Set the dialog message
        builder.setMessage("Update data successful! Do you want to return to main page?")

        // Set the Positive button (Yes/Confirm)
        builder.setPositiveButton("Yes, Exit") { dialog: DialogInterface, which: Int ->
            // User clicked Refresh button
            findNavController().navigate(R.id.action_shipmentFragment_to_homeFragment)
        }

        // Set the Negative button (No/Cancel)
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
            // User clicked Cancel button
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

    private fun showPopUp(view : View) {
        // Create the PopupMenu
        val popup = PopupMenu(requireContext(), view) // 'this' is Context

        // Inflate the menu
        popup.menuInflater.inflate(R.menu.popup_menu, popup.menu)

        // Set click listener
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
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

        // Show the menu
        popup.show()
    }

    override fun onClick(
        position: Int,
        view: View,
        item: SimpleItem
    ) {
    }

}