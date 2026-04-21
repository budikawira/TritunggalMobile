package com.inventory.app.mobile.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.TransferItemAdapter
import com.inventory.app.mobile.databinding.FragmentTransferListBinding
import com.inventory.app.mobile.models.TransferItem
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.TransferListInitResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


/**
 * A simple [Fragment] subclass.
 * Use the [TransferListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TransferListFragment : BaseFragment() {
    companion object {
        private const val TAG = "TransferListFragment"
    }
    private var _binding : FragmentTransferListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TransferItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentTransferListBinding.inflate(inflater, container, false)

        return binding.root
    }

    private fun init() {
        mainActivity?.showLoading(true)
        var request = apiInterface.transferListInit()
        request.enqueue(object : Callback<TransferListInitResponse?> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(
                call: Call<TransferListInitResponse?>,
                response: Response<TransferListInitResponse?>
            ) {
                mainActivity?.showLoading(false)
                var result = response.body()
                if (result != null) {
                    if (result.result == BaseResponse.RESULT_OK) {
                        adapter.data = result.transferItems
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(
                call: Call<TransferListInitResponse?>,
                t: Throwable
            ) {
                mainActivity?.showLoading(false)
                Toast.makeText(context, "Init fail! Please try again!", Toast.LENGTH_SHORT).show()
            }

        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        adapter = TransferItemAdapter(ArrayList<TransferItem>(), object : TransferItemAdapter.OnItemClick {
            override fun onClick(
                position: Int,
                transferItem: TransferItem
            ) {
                val action = TransferListFragmentDirections
                    .actionTransferListFragmentToTransferFragment(id = transferItem.id)
                findNavController().navigate(action)
            }

        })
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        binding.btnMore.setOnClickListener { view ->
            showPopUp(view)
        }

        init()
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
                    init()
                    true
                }
                R.id.action_exit -> {
                    findNavController().navigate(R.id.action_transferListFragment_to_homeFragment)
                    true
                }
                else -> false
            }
        }

        // Show the menu
        popup.show()
    }
}