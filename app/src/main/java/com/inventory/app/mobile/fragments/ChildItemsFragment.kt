package com.inventory.app.mobile.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.SimpleItemAdapter
import com.inventory.app.mobile.databinding.FragmentChildItemsBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetItemByGroupRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByGroupResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChildItemsFragment : BaseFragment(), SimpleItemAdapter.OnItemClick {
    companion object {
        private const val TAG = "ChildItemsFragment"
        const val KEY_UNGROUP_GROUP_ITEM_ID = "ungroupGroupItemId"
        const val KEY_UNGROUP_CHILDREN = "ungroupChildren"

        private const val SPINNER_POSITION_PROCESS_GROUP = 1
        private const val SPINNER_POSITION_UNGROUP = 2
    }

    private val args: ChildItemsFragmentArgs by navArgs()

    private var _binding: FragmentChildItemsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx: AppCtx

    private lateinit var mAdapter: SimpleItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChildItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appCtx = AppCtx.applicationContext()
        mainActivity?.currentFragment = this
        // This is a peek screen that never manages the RFID/BT connection itself; its own
        // teardown (e.g. pressing back to the fragment that pushed it) must not disconnect
        // a connection that screen is expecting to still be alive on return.
        preserveConnectionOnDestroy = true
        sessionManager = SessionManager(mainActivity!!)
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        binding.textGroupName.text = args.name

        mAdapter = SimpleItemAdapter(appCtx, ArrayList<SimpleItem>(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = mAdapter
        mAdapter.onSelectionChanged = {
            binding.buttonConfirm.isEnabled = mAdapter.selectedItems.isNotEmpty()
        }

        binding.buttonConfirm.setOnClickListener { confirmUngroup() }

        if (args.startInSelectionMode) {
            // The caller (e.g. PlacementFragment's "Select Child Items" dialog) already asked
            // the whole-group-vs-ungroup question, so skip the spinner and go straight into
            // the checkbox picker.
            mAdapter.setSelectionMode(true)
            binding.buttonConfirm.visibility = View.VISIBLE
            binding.buttonConfirm.isEnabled = false
        } else if (args.isEditable) {
            setupActionSpinner()
        }

        init()
    }

    private fun setupActionSpinner() {
        binding.spinnerAction.visibility = View.VISIBLE
        val options = listOf(
            getString(R.string.select_action),
            getString(R.string.process_item_group),
            getString(R.string.child_item_ungroup)
        )
        val spinnerAdapter = object : ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_spinner_item, options
        ) {
            override fun isEnabled(position: Int): Boolean = position != 0

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getDropDownView(position, convertView, parent) as TextView
                row.setTextColor(if (position == 0) Color.GRAY else Color.BLACK)
                return row
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAction.adapter = spinnerAdapter

        binding.spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    SPINNER_POSITION_PROCESS_GROUP -> findNavController().popBackStack()
                    SPINNER_POSITION_UNGROUP -> {
                        mAdapter.setSelectionMode(true)
                        binding.buttonConfirm.visibility = View.VISIBLE
                        binding.buttonConfirm.isEnabled = false
                    }
                    else -> {
                        mAdapter.setSelectionMode(false)
                        binding.buttonConfirm.visibility = View.GONE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun confirmUngroup() {
        val selected = ArrayList(mAdapter.selectedItems)
        if (selected.isEmpty()) return
        val backStackEntry = findNavController().previousBackStackEntry
        backStackEntry?.savedStateHandle?.set(KEY_UNGROUP_GROUP_ITEM_ID, args.groupItemId)
        backStackEntry?.savedStateHandle?.set(KEY_UNGROUP_CHILDREN, selected)
        findNavController().popBackStack()
    }

    private fun init() {
        mainActivity?.showLoading(true)
        val request = apiInterface.getItemByGroup(
            "Bearer " + sessionManager.getSessionId(),
            GetItemByGroupRequest(args.groupItemId)
        )
        request.enqueue(object : Callback<GetItemByGroupResponse?> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(
                call: Call<GetItemByGroupResponse?>,
                response: Response<GetItemByGroupResponse?>
            ) {
                mainActivity?.showLoading(false)
                val result = response.body()
                if (result != null && result.result == BaseResponse.RESULT_OK && result.data != null) {
                    mAdapter.initData(result.data!!, false)
                    mAdapter.notifyDataSetChanged()
                    binding.textEmpty.visibility = if (result.data!!.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(context, "Failed to load child items! Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GetItemByGroupResponse?>, t: Throwable) {
                mainActivity?.showLoading(false)
                Toast.makeText(context, "Failed to load child items! Please try again.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onClick(position: Int, view: View, item: SimpleItem) {
        // Selection is handled via the row's checkbox in selection mode; read-only otherwise.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
