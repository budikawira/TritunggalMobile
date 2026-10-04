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
import com.google.android.material.tabs.TabLayout
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
import com.inventory.app.mobile.utils.TabViewUtils
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

/**
 * Scan-and-ship screen, structured like TransferFragment (out): a Ready to Upload / Needs Review
 * tab split, with Needs Review covering both location-mismatched items and Group/child items that
 * need a resolution choice. A Shipment has a single location (no destination - items leave the
 * system entirely), so mismatch here means "this item isn't currently at the shipment's own
 * location", checked against mLocationId the same way Transfer checks against mSrcLocationId.
 */
class ShipmentFragment : BaseFragment(), SimpleItemAdapter.OnItemClick {
    companion object {
        private const val TAG = "ShipmentFragment"
        private const val TAB_READY = 0
        private const val TAB_REVIEW = 1
    }

    private val args: ShipmentFragmentArgs by navArgs()

    private var _binding: FragmentShipmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx: AppCtx

    // Single adapter shared by both tabs; its bound `data` is swapped between mReadyItems and
    // mReviewItems depending on which tab is selected (see switchData()/onTabChanged()), while
    // its ok/nok/epcs bookkeeping stays global across both lists.
    private lateinit var mAdapter: SimpleItemAdapter
    private var mReadyItems: ArrayList<SimpleItem> = ArrayList()
    private var mReviewItems: ArrayList<SimpleItem> = ArrayList()
    private var isReadyTabActive = true
    private var isConfirmAllowed = false

    private val lock = Any()

    private var mScannedEpc: ArrayList<String> = ArrayList() //processing or processed epc
    private var mProcessingEpc: ArrayList<String> = ArrayList() //just scanned epc

    private val debugEpc = arrayOf("00000000", "32364330303339FF")

    private var mId: Long = 0L

    // Guards against re-fetching/resetting the scanned list when this fragment's view is
    // rebuilt after returning from a "peek" child destination like ChildItemsFragment.
    private var isDataLoaded = false
    private var mNo: String = ""
    private var mLocationId: Long? = null
    private var mLocationName: String = ""

    private val itemByEpcListener = object : Callback<GetItemByEpcResponse?> {
        @SuppressLint("NotifyDataSetChanged")
        override fun onResponse(
            call: Call<GetItemByEpcResponse?>,
            response: Response<GetItemByEpcResponse?>
        ) {
            val getItemByPinResponse = response.body()
            if (getItemByPinResponse != null) {
                if (getItemByPinResponse.result == BaseResponse.RESULT_OK && getItemByPinResponse.data != null) {
                    synchronized(lock) {
                        var readyAdded = false
                        var reviewAdded = false
                        getItemByPinResponse.data!!.forEach { row ->
                            val isMismatch = isLocationMismatch(row)
                            val needsReview = isMismatch || row.type == SimpleItem.TYPE_GROUP || row.parentItemId != null
                            val target = if (needsReview) mReviewItems else mReadyItems
                            if (mAdapter.addTo(target, row)) {
                                if (isMismatch) mAdapter.nok[row.no] = mismatchStatusText(row)
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
            refreshUploadButtonState()
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
                        mNo = result.no
                        mLocationId = result.locationId
                        mLocationName = result.locationName
                        binding.textNo.text = mNo
                        binding.textLocation.text = mLocationName
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

            override fun onFailure(call: Call<ShipmentInitResponse?>, t: Throwable) {}
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        binding.buttonScan.setOnClickListener { toggleScan() }
        binding.btnMore.setOnClickListener { v -> showPopUp(v) }
        binding.buttonUpload.setOnClickListener { uploadData() }
        binding.textPower.setOnClickListener { showPowerDialog() }
        mId = args.id

        if (!isDataLoaded) {
            init()
        } else {
            // Restored after returning from a peek destination (e.g. ChildItemsFragment):
            // the header views are new objects even though mAdapter's data survived.
            binding.textNo.text = mNo
            binding.textLocation.text = mLocationName
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
                sessionManager.setDeviceAddress("")
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
        mAdapter.nok.remove(item.no)
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
        if (mAdapter.nok.containsKey(item.no)) {
            showMismatchResolutionDialog(item)
        } else if (item.type == SimpleItem.TYPE_GROUP) {
            showGroupResolutionDialog(item)
        } else {
            showChildResolutionDialog(item)
        }
    }

    // The item's current LocationId doesn't match the shipment's own location - a physically
    // wrong item was scanned. This takes priority over Group/child resolution (checked first in
    // handleReviewItemClick), and the only ways out are removing it or re-checking its location
    // (e.g. an admin may have just corrected it via the Placement flow while this screen is open).
    private fun isLocationMismatch(item: SimpleItem): Boolean {
        val loc = mLocationId ?: return false
        return item.locationId != loc
    }

    private fun mismatchStatusText(item: SimpleItem): String {
        return if (!item.locationName.isNullOrEmpty()) {
            getString(R.string.wrong_location_status, item.locationName)
        } else {
            getString(R.string.wrong_location_status_unknown)
        }
    }

    private fun showMismatchResolutionDialog(item: SimpleItem) {
        val options: Array<CharSequence> = arrayOf(
            getString(R.string.remove_item),
            getString(R.string.action_refresh_location)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.resolve_mismatch_item_title, item.no))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeReviewItemFromScan(item)
                    1 -> refreshItemLocation(item)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshItemLocation(item: SimpleItem) {
        val epc = item.epc
        if (epc.isNullOrEmpty()) return
        val request = apiInterface.getItemByEpc(
            "Bearer " + sessionManager.getSessionId(),
            GetItemByEpcRequest(arrayListOf(epc))
        )
        request.enqueue(object : Callback<GetItemByEpcResponse?> {
            override fun onResponse(
                call: Call<GetItemByEpcResponse?>,
                response: Response<GetItemByEpcResponse?>
            ) {
                val body = response.body()
                val refreshed = body?.data?.firstOrNull { it.epc == epc }
                if (body != null && body.result == BaseResponse.RESULT_OK && refreshed != null) {
                    applyRefreshedItem(item, refreshed)
                } else {
                    showToast(getString(R.string.failed_refresh_location))
                }
            }

            override fun onFailure(call: Call<GetItemByEpcResponse?>, t: Throwable) {
                showToast(getString(R.string.failed_refresh_location))
            }
        })
    }

    // Re-routes a freshly re-fetched item through the normal rules: still mismatched keeps it
    // flagged in Needs Review (with updated status text), resolved routes it exactly like a fresh
    // scan would (Ready if plain, or Needs Review's Group/child dialogs if it turns out to need
    // those) - refreshing itself is the resolution, no extra tap required.
    @SuppressLint("NotifyDataSetChanged")
    private fun applyRefreshedItem(oldItem: SimpleItem, refreshed: SimpleItem) {
        synchronized(lock) {
            mReviewItems.remove(oldItem)
            mAdapter.nok.remove(oldItem.no)

            val stillMismatch = isLocationMismatch(refreshed)
            val needsReview = stillMismatch || refreshed.type == SimpleItem.TYPE_GROUP || refreshed.parentItemId != null
            if (stillMismatch) {
                mAdapter.nok[refreshed.no] = mismatchStatusText(refreshed)
            }
            if (needsReview) {
                mAdapter.moveTo(mReviewItems, refreshed)
            } else if (mReadyItems.none { it.id == refreshed.id }) {
                mAdapter.moveTo(mReadyItems, refreshed)
            }
            mAdapter.notifyDataSetChanged()
        }
        refreshUploadButtonState()
        updateEmptyState()
        updateTabTitles()
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
        val action = ShipmentFragmentDirections
            .actionShipmentFragmentToChildItemsFragment(item.id, item.no, true, true)
        findNavController().navigate(action)
    }

    // Moves the Group item itself into Ready to Upload, intact (still type == TYPE_GROUP) -
    // no expansion into its children. The backend resolves a grouped child's effective location
    // via its parent while it stays grouped, and ConfirmShipmentHandler already un-groups a child
    // if *it* gets confirmed on its own - so uploading just the group's id is sufficient; no API
    // call is needed here at all.
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

    @SuppressLint("NotifyDataSetChanged")
    private fun uploadData() {
        val itemIds = ArrayList<Long>()
        val simpleItems = mAdapter.getUploadable(mReadyItems)
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
                        if (isReadyTabActive) mAdapter.notifyDataSetChanged()
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
        builder.setTitle("Confirm Refresh")
        builder.setMessage("Are you sure you want to refresh? All scanned data will be reset and cannot be recovered.")
        builder.setPositiveButton("Refresh Anyway") { dialog: DialogInterface, _ ->
            init()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _ ->
            Toast.makeText(requireContext(), "Refresh cancelled.", Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }
        builder.create().show()
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

    override fun onClick(position: Int, view: View, item: SimpleItem) {
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
                    val action = ShipmentFragmentDirections
                        .actionShipmentFragmentToChildItemsFragment(item.id, item.no, false)
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

    override fun isScanning(): Boolean = mIsScanning

    override fun onConnectionStateChange(connectionStatus: ConnectionStatus, device: BluetoothDevice?) {
        if (_binding == null) return
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
