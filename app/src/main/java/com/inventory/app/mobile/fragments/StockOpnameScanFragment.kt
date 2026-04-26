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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.StockOpnameAdapter
import com.inventory.app.mobile.databinding.FragmentStockOpnameScanBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.models.StockOpnameItem
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetItemByLocationRequest
import com.inventory.app.mobile.utils.rest.requests.StockOpnameUploadRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByLocationResponse
import com.inventory.app.mobile.utils.rest.response.StockOpnameUploadResponse
import com.rscja.deviceapi.entity.UHFTAGInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * A simple [Fragment] subclass.
 * Use the [StockOpnameScanFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class StockOpnameScanFragment : BaseFragment(), StockOpnameAdapter.OnItemClick {
    companion object {
        private const val TAG = "StockOpnameScanFragment"
    }

    // Correct way to declare NavArgs
    private val args: StockOpnameScanFragmentArgs by navArgs()

    private var _binding : FragmentStockOpnameScanBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx : AppCtx

    private lateinit var mAdapter: StockOpnameAdapter

    private val lock = Any()

    private var mScannedEpc : ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc : ArrayList<String> = ArrayList() //just scanned epc

    private val debugEpc = arrayOf("50424D5530323531323525480001FFFF","003","005")
    private val debugEpc1 = arrayOf("001","002","004","005")
    private var mStockOpnameId = 0L

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
                if (mainActivity!!.mReader!!.startInventoryTag()) {
                    handler.sendEmptyMessageDelayed(2, 10)

                    binding.buttonUpload.isEnabled = false
                    binding.btnMore.isEnabled = false
                    binding.buttonScan.text = "Stop Scan"
                    val color = ContextCompat.getColor(appCtx, R.color.accent)
                    binding.buttonScan.backgroundTintList = ColorStateList.valueOf(color)

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

    @OptIn(DelicateCoroutinesApi::class)
    private fun simulateScanRfid() {
        lifecycleScope.launch {
            delay(5000) // Pause for 5 seconds
            withContext(Dispatchers.Main) {
                if (mStockOpnameId == 0L) {
                    debugEpc.forEach { epc ->
                        updateScanData(epc)
                    }
                } else {
                    debugEpc1.forEach { epc -> updateScanData(epc) }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateScanData(epc: String) {
        synchronized(lock) {
            if (epc.isEmpty()) return

            if (mScannedEpc.contains(epc) ||
                    mProcessingEpc.contains(epc)) return
            if (mAdapter.setScanned(epc)) {
                mAdapter.notifyDataSetChanged()
                binding.textNotScanned.text = mAdapter.countNotScanned().toString()
                binding.textScanned.text = mAdapter.countScanned().toString()
            }

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
        try {
            handler.removeCallbacksAndMessages(null)
//            if (mainActivity?.mReader != null && mainActivity?.mReader!!.isInventorying) {
//                if (!mainActivity!!.mReader!!.stopInventory()) {
//                    Toast.makeText(mainActivity, "onPause :: Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
//                }
//            }
        } catch (_ : Exception) {

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentStockOpnameScanBinding.inflate(inflater, container, false)

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appCtx = AppCtx.applicationContext()
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        mAdapter = StockOpnameAdapter(appCtx, ArrayList<StockOpnameItem>(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = mAdapter

        binding.buttonScan.setOnClickListener {
            toggleScan()
        }
        binding.btnMore.setOnClickListener { view -> showPopUp(view) }
        binding.buttonUpload.setOnClickListener { uploadData() }

        binding.textLocation.text = args.name

        binding.layoutScanned.setOnClickListener {
            binding.layoutScanned.setBackgroundResource(R.drawable.border_bg)
            binding.layoutNotScanned.setBackgroundResource(0)
            mAdapter.setMode(true)
        }

        binding.layoutNotScanned.setOnClickListener {
            binding.layoutScanned.setBackgroundResource(0)
            binding.layoutNotScanned.setBackgroundResource(R.drawable.border_bg)
            mAdapter.setMode(false)
        }
        init()
    }

    private fun uploadData() {
        var data = mAdapter.getData()
        var scanned = ArrayList<Long>()
        var notScanned = ArrayList<Long>()

        data.forEach { dt ->
            if (dt.isScanned) {
                scanned.add(dt.id)
            } else {
                notScanned.add(dt.id)
            }
        }

        var param = StockOpnameUploadRequest(
            mStockOpnameId,
            args.locationId,
            args.shelfId,
            args.shelfSlotId,
            scanned,
            notScanned
        )

        var token = ("Bearer " + sessionManager.getSessionId())
        var request = apiInterface.stockOpnameUpload(token, param)
        request.enqueue(object : Callback<StockOpnameUploadResponse?> {
            override fun onResponse(
                call: Call<StockOpnameUploadResponse?>,
                response: Response<StockOpnameUploadResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        mStockOpnameId = result.stockOpnameId
                        showUploadCompleteDialog()
                    } else {
                        Toast.makeText(requireContext(),
                            "Upload fail! " + result.message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(),
                        "Upload fail! Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(
                call: Call<StockOpnameUploadResponse?>,
                t: Throwable
            ) {
                Toast.makeText(requireContext(),
                    "Upload fail! Please try again.", Toast.LENGTH_SHORT).show()
            }

        })


    }

    private fun init() {
        var param = GetItemByLocationRequest()
        param.locationId = args.locationId
        param.shelfId = args.shelfId
        param.shelfSlotId = args.shelfSlotId

        var request = apiInterface.getItemByLocation(param)
        request.enqueue(object: Callback<GetItemByLocationResponse?> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(
                call: Call<GetItemByLocationResponse?>,
                response: Response<GetItemByLocationResponse?>
            ) {
                val result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        var data = result.data ?: ArrayList<SimpleItem>()
                        binding.textNotScanned.text = data.size.toString()
                        mAdapter.setData(data)
                        mAdapter.notifyDataSetChanged()
//                        mScannedEpc.clear()
//                        mProcessingEpc.clear()
                    } else {
                        Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
                    }
                } else {

                    Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(
                call: Call<GetItemByLocationResponse?>,
                t: Throwable
            ) {
                Toast.makeText(context, "Fail loading items! Please try again!", Toast.LENGTH_SHORT).show()
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

    private fun showUploadCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())

        // Set the dialog title
        builder.setTitle("Upload Completed")

        // Set the dialog message
        builder.setMessage("Update data successful! Do you want to return to main page?")

        // Set the Positive button (Yes/Confirm)
        builder.setPositiveButton("Yes, Exit") { dialog: DialogInterface, which: Int ->
            // User clicked Refresh button
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.homeFragment) {
                navController.navigate(R.id.homeFragment)
            }
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
                    findNavController().navigate(R.id.action_stockOpnameScanFragment_to_homeFragment)
                    true
                }
                else -> false
            }
        }

        // Show the menu
        popup.show()
    }
}

