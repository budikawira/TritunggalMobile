package com.inventory.app.mobile.fragments

import android.annotation.SuppressLint
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
import com.google.android.material.tabs.TabLayout
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.FLAG_FAIL
import com.inventory.app.mobile.FLAG_START
import com.inventory.app.mobile.FLAG_STOP
import com.inventory.app.mobile.FLAG_SUCCESS
import com.inventory.app.mobile.FLAG_UHFINFO_LIST
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.SimpleItemAdapter
import com.inventory.app.mobile.databinding.FragmentTransferInBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.TabViewUtils
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetItemByEpcRequest
import com.inventory.app.mobile.utils.rest.requests.TransferConfirmRequest
import com.inventory.app.mobile.utils.rest.requests.TransferInitRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByEpcResponse
import com.inventory.app.mobile.utils.rest.response.TransferInitResponse
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

/**
 * Receiving/scan-checklist screen for a Transfer that has already been confirmed out.
 * Not Scanned -> Scanned is a local checklist match against the transfer's expected item list
 * (matched by epc, no network call needed); a scanned epc that doesn't match anything expected
 * lands in Needs Review instead. Upload is only a client-side gate - it's enabled once Not
 * Scanned and Needs Review are both empty, and finalizes the whole transfer server-side via
 * TransferConfirmIn (which doesn't take an item list; the item set was already fixed at
 * confirm-out time).
 */
class TransferInFragment : BaseFragment(), SimpleItemAdapter.OnItemClick {
    companion object {
        private const val TAG = "TransferInFragment"
        private const val TAB_NOT_SCANNED = 0
        private const val TAB_SCANNED = 1
        private const val TAB_REVIEW = 2
    }

    private val args: TransferInFragmentArgs by navArgs()

    private var _binding: FragmentTransferInBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx: AppCtx

    // Single adapter shared by all three tabs; its bound `data` is swapped between the three
    // lists depending on which tab is selected (see switchData()/onTabChanged()). `ok` drives the
    // green "Scanned" highlight and `nok` drives the red "Needs Review" highlight/status text -
    // reused from the same adapter mechanism the Transfer Out screens use.
    private lateinit var mAdapter: SimpleItemAdapter
    private var mNotScannedItems: ArrayList<SimpleItem> = ArrayList()
    private var mScannedItems: ArrayList<SimpleItem> = ArrayList()
    private var mReviewItems: ArrayList<SimpleItem> = ArrayList()
    private var currentTab = TAB_NOT_SCANNED

    private val lock = Any()

    private var mScannedEpc: ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc: ArrayList<String> = ArrayList() //just scanned epc

    private var mId: Long = 0L

    // Guards against re-fetching/resetting the scanned list when this fragment's view is
    // rebuilt (e.g. after a configuration change).
    private var isDataLoaded = false
    private var mNo: String = ""
    private var mSrcLocationName: String = ""
    private var mDestLocationName: String = ""

    private val unexpectedItemListener = object : Callback<GetItemByEpcResponse?> {
        @SuppressLint("NotifyDataSetChanged")
        override fun onResponse(
            call: Call<GetItemByEpcResponse?>,
            response: Response<GetItemByEpcResponse?>
        ) {
            val body = response.body()
            if (body != null && body.result == BaseResponse.RESULT_OK && body.data != null) {
                synchronized(lock) {
                    var added = false
                    body.data!!.forEach { row ->
                        if (mAdapter.addTo(mReviewItems, row)) {
                            mAdapter.nok[row.no] = getString(R.string.not_expected_status)
                            added = true
                        }
                    }
                    if (added) notifyIfActive(TAB_REVIEW)
                }
                updateTabTitles()
                updateEmptyState()
            } else {
                Toast.makeText(appCtx, "Error : " + (body?.message ?: ""), Toast.LENGTH_SHORT).show()
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
            val epcList = ArrayList<String>()
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
                Log.d(TAG, "------------ processScannedEpcs ------------")
                activity?.runOnUiThread { processScannedEpcs(epcList) }
            }
        }
    }

    // Checks each freshly-scanned epc against the still-expected (Not Scanned) items first -
    // a match is a pure local list move, no network call needed. Only epcs that don't match
    // anything expected get looked up via getItemByEpc to find out what they actually are.
    private fun processScannedEpcs(epcList: List<String>) {
        val unmatchedEpcs = ArrayList<String>()
        synchronized(lock) {
            epcList.forEach { epc ->
                val match = mNotScannedItems.firstOrNull { it.epc == epc }
                if (match != null) {
                    moveToScanned(match)
                } else {
                    unmatchedEpcs.add(epc)
                }
            }
        }
        if (unmatchedEpcs.isNotEmpty()) {
            val request = apiInterface.getItemByEpc(
                "Bearer " + sessionManager.getSessionId(),
                GetItemByEpcRequest(unmatchedEpcs)
            )
            request.enqueue(unexpectedItemListener)
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
            refreshUploadButtonState()
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
                // Simulate receiving a couple of the actually-expected items, plus one bogus tag
                // to exercise the Needs Review path.
                mNotScannedItems.mapNotNull { it.epc }.take(2).forEach { epc -> updateScanData(epc) }
                updateScanData("112233")
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
    }

    override fun ReaderOnKeyDwon() {
        toggleScan()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTransferInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appCtx = AppCtx.applicationContext()
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        if (!isDataLoaded) {
            mAdapter = SimpleItemAdapter(appCtx, ArrayList<SimpleItem>(), this)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = mAdapter

        setupTabs(currentTab)
        binding.tabLayout.getTabAt(currentTab)?.select()
        onTabChanged(currentTab)

        binding.buttonScan.setOnClickListener { toggleScan() }
        binding.buttonUpload.setOnClickListener { showConfirmInDialog() }
        binding.btnMore.setOnClickListener { view -> showPopUp(view) }
        binding.textPower.setOnClickListener { showPowerDialog() }
        mId = args.id

        if (!isDataLoaded) {
            init()
        } else {
            binding.textNo.text = mNo
            binding.textSrc.text = mSrcLocationName
            binding.textDest.text = mDestLocationName
            binding.btnMore.isEnabled = true
            refreshUploadButtonState()
        }
    }

    override fun onPowerUpdated() {
        super.onPowerUpdated()
        binding.textPower.text = "$radioPower dB"
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun init() {
        val request = apiInterface.transferInit(
            "Bearer " + sessionManager.getSessionId(),
            TransferInitRequest(mId)
        )
        request.enqueue(object : Callback<TransferInitResponse?> {
            override fun onResponse(
                call: Call<TransferInitResponse?>,
                response: Response<TransferInitResponse?>
            ) {
                val result = response.body()
                if (result != null && result.result == BaseResponse.RESULT_OK) {
                    mNo = result.no
                    mSrcLocationName = result.srcLocationName
                    mDestLocationName = result.destLocationName
                    binding.textNo.text = mNo
                    binding.textSrc.text = mSrcLocationName
                    binding.textDest.text = mDestLocationName

                    mNotScannedItems = result.items
                    mScannedItems = ArrayList()
                    mReviewItems = ArrayList()
                    mAdapter.initData(mNotScannedItems, false)
                    mNotScannedItems.forEach { item ->
                        if (!item.epc.isNullOrEmpty()) mAdapter.epcs.add(item.epc!!)
                    }
                    currentTab = TAB_NOT_SCANNED
                    binding.tabLayout.getTabAt(TAB_NOT_SCANNED)?.select()
                    onTabChanged(TAB_NOT_SCANNED)
                    updateTabTitles()
                    mScannedEpc.clear()
                    mProcessingEpc.clear()
                    isDataLoaded = true
                } else {
                    Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(
                call: Call<TransferInitResponse?>,
                t: Throwable
            ) {
                Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun currentList(): ArrayList<SimpleItem> = when (currentTab) {
        TAB_SCANNED -> mScannedItems
        TAB_REVIEW -> mReviewItems
        else -> mNotScannedItems
    }

    private fun notifyIfActive(tab: Int) {
        if (currentTab == tab) mAdapter.notifyDataSetChanged()
    }

    private fun updateTabTitles() {
        TabViewUtils.updateTabCount(binding.tabLayout, TAB_NOT_SCANNED, mNotScannedItems.size)
        TabViewUtils.updateTabCount(binding.tabLayout, TAB_SCANNED, mScannedItems.size)
        TabViewUtils.updateTabCount(binding.tabLayout, TAB_REVIEW, mReviewItems.size)
        refreshUploadButtonState()
    }

    // Upload finalizes the whole transfer server-side (TransferConfirmIn doesn't take an item
    // list), so it's only safe to allow once every expected item has been physically verified and
    // nothing unresolved is sitting in Needs Review.
    private fun refreshUploadButtonState() {
        binding.buttonUpload.isEnabled =
            !mIsScanning && mNotScannedItems.isEmpty() && mReviewItems.isEmpty()
    }

    private fun updateEmptyState() {
        binding.textEmpty.visibility = if (currentList().isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onTabChanged(position: Int) {
        currentTab = position
        mAdapter.switchData(currentList())
        updateEmptyState()
    }

    private fun setupTabs(initialTab: Int) {
        if (binding.tabLayout.tabCount == 0) {
            binding.tabLayout.addTab(binding.tabLayout.newTab())
            binding.tabLayout.addTab(binding.tabLayout.newTab())
            binding.tabLayout.addTab(binding.tabLayout.newTab())
        }
        TabViewUtils.createCustomTabs(
            binding.tabLayout,
            listOf(
                getString(R.string.tab_not_scanned),
                getString(R.string.tab_scanned),
                getString(R.string.tab_needs_review)
            ),
            initialTab
        )
        binding.tabLayout.clearOnTabSelectedListeners()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                TabViewUtils.setTabSelected(tab, true)
                onTabChanged(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                TabViewUtils.setTabSelected(tab, false)
            }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        updateTabTitles()
    }

    // Moves an item from Not Scanned into Scanned - used both by an actual epc match and by the
    // manual "Mark as Scanned" popup action.
    @SuppressLint("NotifyDataSetChanged")
    private fun moveToScanned(item: SimpleItem) {
        if (!mNotScannedItems.remove(item)) return
        mAdapter.ok.add(item.no)
        mAdapter.moveTo(mScannedItems, item)
        notifyIfActive(TAB_NOT_SCANNED)
        notifyIfActive(TAB_SCANNED)
        updateTabTitles()
        updateEmptyState()
    }

    // "Remove" from the Scanned tab: it's still an expected item, just un-confirmed - moves back
    // to Not Scanned rather than being dropped from the manifest entirely.
    @SuppressLint("NotifyDataSetChanged")
    private fun moveToNotScanned(item: SimpleItem) {
        if (!mScannedItems.remove(item)) return
        mAdapter.ok.remove(item.no)
        mAdapter.moveTo(mNotScannedItems, item)
        notifyIfActive(TAB_SCANNED)
        notifyIfActive(TAB_NOT_SCANNED)
        updateTabTitles()
        updateEmptyState()
    }

    // "Remove" from Needs Review: this item was never expected, so it's dropped entirely (unlike
    // Scanned's remove) and its epc is freed so a later re-scan can be re-evaluated fresh.
    @SuppressLint("NotifyDataSetChanged")
    private fun removeReviewItem(item: SimpleItem) {
        if (!mReviewItems.remove(item)) return
        mAdapter.nok.remove(item.no)
        item.epc?.let { epc ->
            if (epc.isNotEmpty()) {
                mAdapter.epcs.remove(epc)
                synchronized(lock) {
                    mScannedEpc.remove(epc)
                    mProcessingEpc.remove(epc)
                }
            }
        }
        notifyIfActive(TAB_REVIEW)
        updateTabTitles()
        updateEmptyState()
    }

    override fun onClick(position: Int, view: View, item: SimpleItem) {
        if (mIsScanning) return
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.popup_menu_row_transfer_in, popup.menu)
        when (currentTab) {
            TAB_NOT_SCANNED -> popup.menu.removeItem(R.id.action_remove_item)
            TAB_SCANNED, TAB_REVIEW -> popup.menu.removeItem(R.id.action_mark_scanned)
        }
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_mark_scanned -> {
                    moveToScanned(item)
                    true
                }
                R.id.action_remove_item -> {
                    if (currentTab == TAB_SCANNED) moveToNotScanned(item) else removeReviewItem(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showPopUp(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.popup_menu, popup.menu)
        popup.menu.removeItem(R.id.action_confirm)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    showRefreshConfirmationDialog()
                    true
                }
                R.id.action_exit -> {
                    findNavController().navigate(R.id.action_transferInFragment_to_homeFragment)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRefreshConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirm Refresh")
        builder.setMessage("Are you sure you want to refresh? All scanned data will be reset and cannot be recovered.")
        builder.setPositiveButton("Refresh Anyway") { dialog, _ ->
            init()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            Toast.makeText(requireContext(), "Refresh cancelled.", Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }
        builder.create().show()
    }

    private fun showConfirmInDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirm Receipt")
        builder.setMessage("Confirm receipt of this transfer? This will finalize the transfer and move all items into $mDestLocationName.")
        builder.setPositiveButton("Yes, Confirm") { dialog, _ ->
            confirmIn()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.create().show()
    }

    private fun confirmIn() {
        val request = apiInterface.transferConfirmIn(
            "Bearer " + sessionManager.getSessionId(),
            TransferConfirmRequest(mId)
        )
        request.enqueue(object : Callback<BaseResponse?> {
            override fun onResponse(
                call: Call<BaseResponse?>,
                response: Response<BaseResponse?>
            ) {
                val result = response.body()
                if (result != null && result.result == BaseResponse.RESULT_OK) {
                    showConfirmInCompleteDialog()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Confirm fail! " + (result?.message ?: "Please try again."),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<BaseResponse?>, t: Throwable) {
                Toast.makeText(requireContext(), "Confirm fail! Please try again.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showConfirmInCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirm Completed")
        builder.setMessage("Transfer receipt confirmed successfully.")
        builder.setPositiveButton("Ok") { _, _ ->
            findNavController().navigate(R.id.action_transferInFragment_to_homeFragment)
        }
        builder.create().show()
    }

    @Synchronized
    private fun getUHFInfo(): List<UHFTAGInfo>? {
        return uhf?.readTagFromBufferList_EpcTidUser()
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
