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
import com.google.android.material.tabs.TabLayout
import com.inventory.app.mobile.adapters.SimpleItemAdapter
import com.inventory.app.mobile.databinding.FragmentPlacementBinding
import com.inventory.app.mobile.databinding.FragmentTransferBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.TabViewUtils
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
        private const val TAB_READY = 0
        private const val TAB_REVIEW = 1
    }

    // Correct way to declare NavArgs
    private val args: PlacementFragmentArgs by navArgs()

    private var _binding : FragmentPlacementBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx : AppCtx

    // Single adapter shared by both tabs; its bound `data` is swapped between mReadyItems and
    // mReviewItems depending on which tab is selected (see switchData()/onTabChanged()), while
    // its ok/nok/epcs bookkeeping stays global across both lists.
    private lateinit var mAdapter: SimpleItemAdapter
    private var mReadyItems: ArrayList<SimpleItem> = ArrayList()
    private var mReviewItems: ArrayList<SimpleItem> = ArrayList()
    private var isReadyTabActive = true
    private var isConfirmAllowed = false

    private val lock = Any()

    private var mScannedEpc : ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc : ArrayList<String> = ArrayList() //just scanned epc

    private val debugEpc = arrayOf("00000000","3236473030303731","3236473030303830","3236483030303134","3236483030303133","32364330303339FF")

    private var mId : Long = 0L

    // Guards against re-fetching/resetting the scanned list when this fragment's view is
    // rebuilt after returning from a "peek" child destination like ChildItemsFragment.
    private var isDataLoaded = false
    private var mNo : String = ""
    private var mDestLocationName : String = ""

    private val itemByEpcListener = object : Callback<GetItemByEpcResponse?> {
        @SuppressLint("NotifyDataSetChanged")
        override fun onResponse(
            call: Call<GetItemByEpcResponse?>,
            response: Response<GetItemByEpcResponse?>
        ) {
            var getItemByPinResponse = response.body()
            if (getItemByPinResponse != null) {
                if (getItemByPinResponse.result == BaseResponse.RESULT_OK && getItemByPinResponse.data != null) {
                    synchronized(lock)
                    {
                        var readyAdded = false
                        var reviewAdded = false
                        getItemByPinResponse.data!!.forEach { row ->
                            val needsReview = row.type == SimpleItem.TYPE_GROUP || row.parentItemId != null
                            val target = if (needsReview) mReviewItems else mReadyItems
                            if (mAdapter.addTo(target, row)) {
                                if (needsReview) reviewAdded = true else readyAdded = true
                            }
                        }
                        if ((readyAdded && isReadyTabActive) || (reviewAdded && !isReadyTabActive)) {
                            mAdapter.notifyDataSetChanged()
                        }
                        updateTabTitles()
                        updateEmptyState()
                        refreshUploadButtonState()
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

        if (!isDataLoaded) {
            mAdapter = SimpleItemAdapter(appCtx, ArrayList<SimpleItem>(), this)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = mAdapter

        val targetTab = if (isReadyTabActive) TAB_READY else TAB_REVIEW
        setupTabs(targetTab)
        binding.tabLayout.getTabAt(targetTab)?.select()
        // getTabAt(0)?.select() is a no-op if it's already the selected tab (the listener won't
        // fire), so bind explicitly too.
        onTabChanged(targetTab)

        binding.buttonScan.setOnClickListener {
            toggleScan()
        }
        binding.btnMore.setOnClickListener { view -> showPopUp(view) }
        binding.buttonUpload.setOnClickListener { uploadData() }
        binding.textPower.setOnClickListener { showPowerDialog() }
        mId = args.id

        if (!isDataLoaded) {
            init()
        } else {
            // Restored after returning from a peek destination (e.g. ChildItemsFragment):
            // the header views are new objects even though mAdapter's data survived.
            binding.textNo.text = mNo
            binding.textDest.text = mDestLocationName
            binding.btnMore.isEnabled = true
            refreshUploadButtonState()
        }

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
        if (!Params.DEBUG) {
            if (uhf?.connectStatus == ConnectionStatus.CONNECTED) {
                // Connection was preserved across a peek-and-return; just refresh the UI.
                var address = remoteBTName
                if (address.isNotEmpty()) address += "\n"
                address += remoteBTAdd
                binding.tvAddress.text = address
                binding.buttonScan.isEnabled = true
            } else {
                binding.tvAddress.setText(R.string.connecting)
                initConnect()
            }
        } else {
            binding.tvAddress.setText(R.string.connect_success)
            binding.buttonScan.isEnabled = true
        }

        observeUngroupResult()
    }

    private fun observeUngroupResult() {
        val backStackEntry = findNavController().currentBackStackEntry ?: return
        val savedStateHandle = backStackEntry.savedStateHandle
        savedStateHandle.getLiveData<ArrayList<SimpleItem>>(ChildItemsFragment.KEY_UNGROUP_CHILDREN)
            .observe(viewLifecycleOwner) { children ->
                val groupItemId = savedStateHandle.get<Long>(ChildItemsFragment.KEY_UNGROUP_GROUP_ITEM_ID)
                savedStateHandle.remove<ArrayList<SimpleItem>>(ChildItemsFragment.KEY_UNGROUP_CHILDREN)
                savedStateHandle.remove<Long>(ChildItemsFragment.KEY_UNGROUP_GROUP_ITEM_ID)
                if (groupItemId != null) {
                    applyUngroupResult(groupItemId, children)
                }
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyUngroupResult(groupItemId: Long, children: ArrayList<SimpleItem>) {
        // Reached only via Needs Review's "Select Child Items (Ungroup)" dialog option - a
        // not-yet-uploaded group row in Ready no longer offers "View Children", so the group can
        // never be sitting in mReadyItems when this fires.
        val groupItem = mReviewItems.firstOrNull { it.id == groupItemId } ?: return
        val groupNo = groupItem.no
        synchronized(lock) {
            removeFromReviewList(groupItem, freeForRescan = false)
            children.forEach { child -> mergeChildIntoReady(child) }
        }
        refreshUploadButtonState()
        updateEmptyState()
        updateTabTitles()
        showToast(getString(R.string.ungroup_result, groupNo, children.size))
    }

    // Removes an item from the Needs Review list. `freeForRescan` controls whether the item's
    // epc is released so the physical tag can be scanned again later: true for an explicit
    // "Remove from Scan List" action, false when the item is just being superseded/merged into
    // Ready to Upload (it's still tracked, just moved).
    @SuppressLint("NotifyDataSetChanged")
    private fun removeFromReviewList(item: SimpleItem, freeForRescan: Boolean) {
        if (!mReviewItems.remove(item)) return
        if (freeForRescan) {
            item.epc?.let { epc ->
                if (epc.isNotEmpty()) {
                    mAdapter.epcs.remove(epc)
                    synchronized(lock) {
                        mScannedEpc.remove(epc)
                        mProcessingEpc.remove(epc)
                    }
                }
            }
        }
        if (!isReadyTabActive) mAdapter.notifyDataSetChanged()
        updateTabTitles()
        updateEmptyState()
    }

    // Removes a row from Ready to Upload because it's been superseded by a group row that now
    // covers it (not a user-initiated removal), so its epc stays reserved in mAdapter.epcs.
    @SuppressLint("NotifyDataSetChanged")
    private fun removeSupersededFromReady(item: SimpleItem) {
        if (!mReadyItems.remove(item)) return
        if (isReadyTabActive) mAdapter.notifyDataSetChanged()
        updateTabTitles()
    }

    // When a group is added to Ready as a whole (see addWholeGroup()), any of its children that
    // were already scanned/resolved as their own separate rows - in either tab - are redundant:
    // the group row now represents them too. Matched by parentItemId, no network call needed.
    private fun removeChildrenOfGroup(groupId: Long) {
        mReviewItems.filter { it.parentItemId == groupId }.forEach { child ->
            removeFromReviewList(child, freeForRescan = false)
        }
        mReadyItems.filter { it.parentItemId == groupId }.forEach { child ->
            removeSupersededFromReady(child)
        }
    }

    // Moves a group's child into Ready to Upload. If that same item was already individually
    // scanned and is sitting in Needs Review on its own, that entry is preferred (it carries the
    // real scanned epc) and is merged/removed rather than left as a duplicate; if the item is
    // already present in Ready to Upload (e.g. resolved earlier), this is a no-op.
    @SuppressLint("NotifyDataSetChanged")
    private fun mergeChildIntoReady(child: SimpleItem) {
        val existingInReview = mReviewItems.firstOrNull { it.id == child.id }
        val itemToAdd = existingInReview ?: child
        if (existingInReview != null) {
            removeFromReviewList(existingInReview, freeForRescan = false)
        }
        if (mReadyItems.none { it.id == itemToAdd.id }) {
            mAdapter.moveTo(mReadyItems, itemToAdd)
            if (isReadyTabActive) mAdapter.notifyDataSetChanged()
        }
    }

    private fun updateTabTitles() {
        TabViewUtils.updateTabCount(binding.tabLayout, TAB_READY, mReadyItems.size)
        TabViewUtils.updateTabCount(binding.tabLayout, TAB_REVIEW, mReviewItems.size)
    }

    private fun updateEmptyState() {
        val currentList = if (isReadyTabActive) mReadyItems else mReviewItems
        binding.textEmpty.visibility = if (currentList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshUploadButtonState() {
        binding.buttonUpload.isEnabled = !mIsScanning && mReviewItems.isEmpty()
    }

    private fun onTabChanged(position: Int) {
        isReadyTabActive = position == TAB_READY
        mAdapter.switchData(if (isReadyTabActive) mReadyItems else mReviewItems)
        updateEmptyState()
    }

    private fun setupTabs(initialTab: Int) {
        if (binding.tabLayout.tabCount == 0) {
            binding.tabLayout.addTab(binding.tabLayout.newTab())
            binding.tabLayout.addTab(binding.tabLayout.newTab())
        }
        TabViewUtils.createCustomTabs(
            binding.tabLayout,
            listOf(getString(R.string.tab_ready_to_upload), getString(R.string.tab_needs_review)),
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

    private fun handleReviewItemClick(item: SimpleItem) {
        if (item.type == SimpleItem.TYPE_GROUP) {
            showGroupResolutionDialog(item)
        } else {
            showChildResolutionDialog(item)
        }
    }

    private fun showGroupResolutionDialog(item: SimpleItem) {
        val options: Array<CharSequence> = arrayOf(
            getString(R.string.action_add_whole_group),
            getString(R.string.action_select_child_items),
            getString(R.string.remove_item)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.resolve_group_item_title, item.no))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> addWholeGroup(item)
                    1 -> navigateToSelectChildItems(item)
                    2 -> removeReviewItemFromScan(item)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChildResolutionDialog(item: SimpleItem) {
        val options: Array<CharSequence> = arrayOf(
            getString(R.string.action_ungroup_from_parent),
            getString(R.string.remove_item)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.resolve_child_item_title, item.no))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> ungroupChildItem(item)
                    1 -> removeReviewItemFromScan(item)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun removeReviewItemFromScan(item: SimpleItem) {
        removeFromReviewList(item, freeForRescan = true)
        refreshUploadButtonState()
    }

    private fun ungroupChildItem(item: SimpleItem) {
        synchronized(lock) {
            removeFromReviewList(item, freeForRescan = false)
            if (mReadyItems.none { it.id == item.id }) {
                mAdapter.moveTo(mReadyItems, item)
                if (isReadyTabActive) mAdapter.notifyDataSetChanged()
            }
        }
        refreshUploadButtonState()
        updateEmptyState()
        updateTabTitles()
    }

    private fun navigateToSelectChildItems(item: SimpleItem) {
        preserveConnectionOnDestroy = true
        val action = PlacementFragmentDirections
            .actionPlacementFragmentToChildItemsFragment(item.id, item.no, true, true)
        findNavController().navigate(action)
    }

    // Moves the Group item itself into Ready to Upload, intact (still type == TYPE_GROUP) -
    // no expansion into its children. The backend resolves a grouped child's effective location
    // via its parent while it stays grouped (see GetAllItemQuery's location-search fallback), and
    // ConfirmPlacementHandler already un-groups a child if *it* gets confirmed on its own - so
    // uploading just the group's id is sufficient; no API call is needed here at all.
    @SuppressLint("NotifyDataSetChanged")
    private fun addWholeGroup(groupItem: SimpleItem) {
        synchronized(lock) {
            removeFromReviewList(groupItem, freeForRescan = false)
            removeChildrenOfGroup(groupItem.id)
            if (mReadyItems.none { it.id == groupItem.id }) {
                mAdapter.moveTo(mReadyItems, groupItem)
                if (isReadyTabActive) mAdapter.notifyDataSetChanged()
            }
        }
        refreshUploadButtonState()
        updateEmptyState()
        updateTabTitles()
    }

    override fun onPowerUpdated() {
        super.onPowerUpdated()
        binding.textPower.text = "$radioPower dB"
    }

    private fun confirm() {
        val simpleItems = mAdapter.getUploadable(mReadyItems)
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

    @SuppressLint("NotifyDataSetChanged")
    private fun uploadData() {
        var itemIds = ArrayList<Long>()
        var simpleItems = mAdapter.getUploadable(mReadyItems)
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
                        if (isReadyTabActive) mAdapter.notifyDataSetChanged()
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
                        mNo = result.no
                        mDestLocationName = result.destLocationName
                        binding.textNo.text = mNo
                        binding.textDest.text = mDestLocationName
                        // Already-uploaded items are historical record, not pending decisions:
                        // they always land in Ready to Upload, even if Group-type or a child.
                        mReadyItems = result.items
                        mReviewItems = ArrayList()
                        mAdapter.initData(mReadyItems, true)
                        isReadyTabActive = true
                        binding.tabLayout.getTabAt(TAB_READY)?.select()
                        onTabChanged(TAB_READY)
                        mScannedEpc.clear()
                        mProcessingEpc.clear()
                        isDataLoaded = true
                        refreshUploadButtonState()
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
        if (mIsScanning) return
        if (!isReadyTabActive) {
            handleReviewItemClick(item)
            return
        }
        val isRemovable = !mAdapter.ok.contains(item.no)
        val isGroup = item.type == SimpleItem.TYPE_GROUP
        // "View Children" is a read-only browse for already-uploaded/historical groups only. A
        // not-yet-uploaded group row just gets Remove - to change how it was resolved (whole
        // group vs. specific children), remove it and re-scan the tag to go through the Needs
        // Review resolution dialog again, rather than editing it in place here.
        val showViewChildren = isGroup && !isRemovable
        if (!isRemovable && !isGroup) return

        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.popup_menu_row, popup.menu)
        if (!isRemovable) {
            popup.menu.removeItem(R.id.action_remove_item)
        }
        if (!showViewChildren) {
            popup.menu.removeItem(R.id.action_view_children)
        }
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_remove_item -> {
                    showRemoveItemConfirmationDialog(item)
                    true
                }
                R.id.action_view_children -> {
                    preserveConnectionOnDestroy = true
                    val action = PlacementFragmentDirections
                        .actionPlacementFragmentToChildItemsFragment(item.id, item.no, false)
                    findNavController().navigate(action)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRemoveItemConfirmationDialog(item: SimpleItem) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.remove_item_confirm_title)
        builder.setMessage(R.string.remove_item_confirm_message)
        builder.setPositiveButton(R.string.remove_item) { dialog: DialogInterface, _: Int ->
            removeItem(item)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
        builder.create().show()
    }

    private fun removeItem(item: SimpleItem) {
        synchronized(lock) {
            item.epc?.let { epc ->
                mScannedEpc.remove(epc)
                mProcessingEpc.remove(epc)
            }
        }
        mAdapter.removeItem(item)
        updateTabTitles()
        updateEmptyState()
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
        return uhf?.readTagFromBufferList_EpcTidUser()
        //return uhf!!.readTagFromBufferList()
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