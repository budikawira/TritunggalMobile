package com.inventory.app.mobile.fragments

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.FLAG_FAIL
import com.inventory.app.mobile.FLAG_START
import com.inventory.app.mobile.FLAG_STOP
import com.inventory.app.mobile.FLAG_SUCCESS
import com.inventory.app.mobile.FLAG_UHFINFO
import com.inventory.app.mobile.FLAG_UHFINFO_LIST
import com.inventory.app.mobile.R
import com.inventory.app.mobile.activities.DialogListActivity
import com.inventory.app.mobile.adapters.NameListAdapter
import com.inventory.app.mobile.databinding.FragmentRegisterItemBinding
import com.inventory.app.mobile.models.Select2Item
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetUnitByMasterItemIdRequest
import com.inventory.app.mobile.utils.rest.requests.RegisterItemRequest
import com.inventory.app.mobile.utils.rest.response.BaseObjectResponse
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.interfaces.ConnectionStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterItemFragment : BaseFragment() {
    companion object {
        private const val TAG = "RegisterItemFragment"
        private const val REQUEST_MASTER_ITEM = 2001
    }

    private lateinit var appCtx : AppCtx
    private var _binding: FragmentRegisterItemBinding? = null
    private val binding get() = _binding!!
    private lateinit var mAdapter: NameListAdapter

    private val lock = Any()
    private val debugEpc = arrayOf("00000001","33364330303339FF")

    private var selectedMasterItem: Select2Item? = null
    private var selectedUnit: Select2Item? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterItemBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)
        return binding.root
    }


    override fun ReaderOnKeyDwon() {
        toggleScan()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity?.currentFragment = this

        appCtx = AppCtx.applicationContext()
        mAdapter = NameListAdapter(ArrayList()) { _, _ ->
            updateCount()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = mAdapter
        updateCount()

        binding.textMasterItem.setOnClickListener { openMasterItemDialog() }

        binding.buttonScan.setOnClickListener { toggleScan() }
        binding.buttonUpload.setOnClickListener { registerItem() }
        binding.textPower.setOnClickListener { showPowerDialog() }

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

        binding.spinnerUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedUnit = parent?.getItemAtPosition(position) as Select2Item
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedUnit = null
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
            if (mAdapter.getData().contains(epc)) return
            mAdapter.addItem(epc)
        }
        activity?.runOnUiThread { updateCount() }
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
            binding.buttonScan.text = "Stop Scan"
            val color = ContextCompat.getColor(appCtx, R.color.accent)
            binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)

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
            binding.buttonScan.text = "Start Scan"
            val color = ContextCompat.getColor(appCtx, R.color.primary)
            binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun stopInventory() {
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

    private fun registerItem() {
        val masterItem = selectedMasterItem
        if (masterItem == null) {
            showToast("Please select a master item first.")
            return
        }
        val epcList = mAdapter.getData()
        if (epcList.isEmpty()) {
            showToast("No EPC scanned. Please scan items first.")
            return
        }

        val unitType = selectedUnit
        if (unitType == null) {
            showToast("Please select a unit type first.")
            return
        }
        val qty = binding.editQuantity.text.toString().toFloatOrNull()
        if (qty == null) {
            showToast("Please input a valid quantity.")
            return
        }
        val request = apiInterface.registerItem(
            "Bearer " + sessionManager.getSessionId(),
            RegisterItemRequest(masterItem.value,
                qty.toString(),
                unitType.value,
                ArrayList(epcList))
        )
        mainActivity?.showLoading(true)
        request.enqueue(object : Callback<BaseResponse?> {
            override fun onResponse(call: Call<BaseResponse?>, response: Response<BaseResponse?>) {

                mainActivity?.showLoading(false)
                val result = response.body()
                if (result != null && result.result == BaseResponse.RESULT_OK) {
                    showToast("Register successful!")
                    mAdapter.getData().clear()
                    mAdapter.notifyDataSetChanged()
                    updateCount()
                    binding.buttonUpload.isEnabled = false
                } else {
                    showToast("Register failed: ${result?.message ?: "Unknown error"}")
                }
            }
            override fun onFailure(call: Call<BaseResponse?>, t: Throwable) {

                mainActivity?.showLoading(false)
                showToast("Connection error: ${t.message}")
            }
        })
    }

    private fun updateCount() {
        binding.textCount.text = mAdapter.itemCount.toString()
    }

    private fun openMasterItemDialog() {
        val intent = Intent(requireActivity(), DialogListActivity::class.java).apply {
            putExtra(DialogListActivity.EXTRA_TITLE, getString(R.string.master_item))
            putExtra(DialogListActivity.EXTRA_MODE, DialogListActivity.MODE_MASTER_ITEM)
        }
        startActivityForResult(intent, REQUEST_MASTER_ITEM)
    }

    @Deprecated("Using for fragment compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        val id   = data.getLongExtra(DialogListActivity.RESULT_ITEM_ID, 0L)
        val text = data.getStringExtra(DialogListActivity.RESULT_ITEM_TEXT) ?: ""

        if (requestCode == REQUEST_MASTER_ITEM) {
            selectedMasterItem = Select2Item(id, text)
            binding.textMasterItem.text = text
            binding.textMasterItem.setTextColor(Color.BLACK)

            //load the unittypes
            val param = GetUnitByMasterItemIdRequest(id)
            val request = apiInterface.getUnitByMasterItemId(
                "Bearer " + sessionManager.getSessionId(),
                param)
            mainActivity?.showLoading(true)
            request.enqueue(object : Callback<BaseObjectResponse<List<Select2Item>>?> {
                override fun onResponse(
                    call: Call<BaseObjectResponse<List<Select2Item>>?>,
                    response: Response<BaseObjectResponse<List<Select2Item>>?>
                ) {
                    mainActivity?.showLoading(false)
                    val result = response.body()
                    if (result != null && result.result == BaseResponse.RESULT_OK) {
                        binding.labelQuantity.visibility = View.VISIBLE
                        binding.editQuantity.visibility = View.VISIBLE
                        binding.spinnerUnit.visibility = View.VISIBLE
                        binding.editQuantity.setText("1")

                        val itemList = mutableListOf<Select2Item>()
                        try {
                            result.data?.forEach { dt ->
                                itemList.add(dt)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_item, // Layout standar Android
                            itemList
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinnerUnit.adapter = adapter

                        // 4. Set item pertama sebagai default (Index 0) secara eksplisit
                        if (itemList.isNotEmpty()) {
                            binding.spinnerUnit.setSelection(0)
                            selectedUnit = itemList[0]
                        }
                    }
                }

                override fun onFailure(
                    call: Call<BaseObjectResponse<List<Select2Item>>?>,
                    t: Throwable
                ) {
                    mainActivity?.showLoading(false)
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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

        //旧主板才需要调用readTagFromBufferList_EpcTidUser 输出 RSSI
        return uhf?.readTagFromBufferList_EpcTidUser()
        //return uhf!!.readTagFromBufferList()
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
}