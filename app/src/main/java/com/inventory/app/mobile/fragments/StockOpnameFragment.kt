package com.inventory.app.mobile.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import cn.pedant.SweetAlert.SweetAlertDialog
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.R
import com.inventory.app.mobile.adapters.Select2Adapter
import com.inventory.app.mobile.databinding.FragmentFindBinding
import com.inventory.app.mobile.databinding.FragmentShipmentBinding
import com.inventory.app.mobile.databinding.FragmentStockOpnameBinding
import com.inventory.app.mobile.models.Location
import com.inventory.app.mobile.models.Select2Item
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetEpcByStickerNoRequest
import com.inventory.app.mobile.utils.rest.requests.GetShelfSlotsRequest
import com.inventory.app.mobile.utils.rest.requests.GetShelvesRequest
import com.inventory.app.mobile.utils.rest.response.BaseObjectResponse
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.BaseTableResponse
import com.inventory.app.mobile.utils.rest.response.GetLocationsResponse
import com.inventory.app.mobile.utils.rest.response.GetShelfSlotsResponse
import com.inventory.app.mobile.utils.rest.response.GetShelvesResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * A simple [Fragment] subclass.
 * Use the [StockOpnameFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class StockOpnameFragment : BaseFragment() {
    companion object {
        private const val TAG = "StockOpnameFragment"
    }

    private var _binding : FragmentStockOpnameBinding? = null
    private val binding get() = _binding!!
    private lateinit var appCtx : AppCtx

    private var adapterLocation : Select2Adapter? = null
    private var adapterShelf : Select2Adapter? = null
    private var adapterShelfSlot : Select2Adapter? = null

    private var selectedLocation : Select2Item? = null
    private var selectedShelf : Select2Item? = null
    private var selectedShelfSlot : Select2Item? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentStockOpnameBinding.inflate(inflater, container, false)

        sessionManager = SessionManager(requireContext())
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainActivity?.currentFragment = this
        super.onViewCreated(view, savedInstanceState)

        binding.buttonStart.setOnClickListener {
            var locationId = selectedLocation?.id ?: 0
            var shelfId = selectedShelf?.id ?: 0
            var shelfSlotId = selectedShelfSlot?.id ?: 0
            var names = arrayListOf<String>()
            if (selectedLocation != null) {
                names.add(selectedLocation!!.text)
            } else if (selectedShelf != null) {
                names.add(selectedShelf!!.text)
            } else if (selectedShelfSlot != null) {
                names.add(selectedShelfSlot!!.text)
            }
            val action = StockOpnameFragmentDirections
                .actionStockOpnameFragmentToStockOpnameScanFragment(
                    locationId = locationId,
                    shelfId = shelfId,
                    shelfSlotId = shelfSlotId,
                    name = names.joinToString(" : ")
                    )
            findNavController().navigate(action)
        }

        binding.textLocation.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.textLocation.showDropDown()
        }
        binding.textLocation.setOnClickListener {
            binding.textLocation.showDropDown()
        }
        binding.textLocation.setOnItemClickListener { parent, view, position, id ->
            var newLocation = adapterLocation?.getItem(position)
            updateLocation(newLocation)
        }
        binding.textLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Optional: Do something before text changes
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                // Called after the text has been changed
                // You can validate or trigger logic here
                if (selectedLocation?.text != s.toString()) {
                    selectedLocation = null
                    updateLocation(null)
                }
            }
        })

        binding.textShelf.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.textShelf.showDropDown()
        }
        binding.textShelf.setOnClickListener {
            binding.textShelf.showDropDown()
        }
        binding.textShelf.setOnItemClickListener { parent, view, position, id ->
            var newShelf = adapterShelf?.getItem(position)
            updateShelf(newShelf)
        }
        binding.textShelf.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Optional: Do something before text changes
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                if (selectedShelf?.text != s.toString()) {
                    selectedShelf = null
                    selectedShelfSlot = null
                    binding.tilShelfSlot.visibility = View.INVISIBLE
                    binding.textShelf.setTextColor(Color.RED)
                }
            }
        })

        binding.textShelfSlot.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.textShelfSlot.showDropDown()
        }
        binding.textShelfSlot.setOnClickListener {
            binding.textShelfSlot.showDropDown()
        }
        binding.textShelfSlot.setOnItemClickListener { parent, view, position, id ->
            selectedShelfSlot = adapterShelfSlot?.getItem(position)
            //shelfSlotUpdated()
        }
        getLocations()
        if (selectedLocation != null) {
            getShelves()
            if (selectedShelf != null) {
                getShelfSlots()
            }
        }
    }

    fun getShelfSlots() {
        var request = apiInterface.getShelfSlots(GetShelfSlotsRequest(selectedShelf?.id ?: 0))
        request.enqueue(object : Callback<GetShelfSlotsResponse?> {
            override fun onResponse(
                call: Call<GetShelfSlotsResponse?>,
                response: Response<GetShelfSlotsResponse?>
            ) {
                var response = response.body()
                if (response?.result == BaseResponse.RESULT_OK) {
                    var list = ArrayList<Select2Item>()
                    response.data?.forEach { dt ->
                        list.add(Select2Item(dt.id, dt.name))
                    }

                    if (list.isNotEmpty()) {
                        binding.tilShelf.visibility = View.VISIBLE
                        adapterShelfSlot = Select2Adapter(requireContext(), list)
                        binding.textShelfSlot.setAdapter<Select2Adapter>(adapterShelfSlot)
                    } else {
                        Toast.makeText(requireContext(), "No shelf slots found!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<GetShelfSlotsResponse?>, t: Throwable) {
                Toast.makeText(requireContext(), "Fail loading shelf slots!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun updateShelf(newShelf : Select2Item?) {
        selectedShelf = newShelf
        if (selectedShelf != null) {
            binding.textShelf.setTextColor(Color.BLACK)
            binding.tilShelfSlot.visibility = View.VISIBLE
            getShelfSlots()
        } else {
            binding.textShelf.setTextColor(Color.RED)
            selectedShelfSlot = null
            binding.tilShelfSlot.visibility = View.INVISIBLE
            binding.textShelfSlot.setText("")
        }
    }

    fun getShelves() {
        var request = apiInterface.getShelves(GetShelvesRequest(selectedLocation?.id ?: 0))
        request.enqueue(object : Callback<GetShelvesResponse?> {
            override fun onResponse(
                call: Call<GetShelvesResponse?>,
                response: Response<GetShelvesResponse?>
            ) {
                if (!isAdded || view == null) return
                var response = response.body()
                if (response?.result == BaseResponse.RESULT_OK) {
                    var list = ArrayList<Select2Item>()
                    response.data?.forEach { dt ->
                        list.add(Select2Item(dt.id, dt.name))
                    }

                    if (list.isNotEmpty()) {
                        binding.tilShelf.visibility = View.VISIBLE
                        adapterShelf = Select2Adapter(requireContext(), list)
                        binding.textShelf.setAdapter<Select2Adapter>(adapterShelf)
                    } else {
                        Toast.makeText(requireContext(), "No shelves found!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<GetShelvesResponse?>, t: Throwable) {
                if (!isAdded || view == null) return
                Toast.makeText(requireContext(), "Fail loading shelves!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun updateLocation(newLocation : Select2Item?) {
        selectedLocation = newLocation
        if (selectedLocation != null) {
            binding.textLocation.setTextColor(Color.BLACK)
            binding.buttonStart.isEnabled = true
            getShelves()
        } else {
            binding.textLocation.setTextColor(Color.RED)
            selectedShelf = null
            selectedShelfSlot = null
            binding.tilShelf.visibility = View.INVISIBLE
            binding.tilShelfSlot.visibility = View.INVISIBLE
            binding.textShelf.setText("")
            binding.textShelfSlot.setText("")
        }
    }

    fun getLocations() {
        binding.tilShelf.visibility = View.INVISIBLE
        binding.tilShelfSlot.visibility = View.INVISIBLE
        mainActivity?.showLoading(true)
        var request = apiInterface.getLocations()
        request.enqueue(object : Callback<GetLocationsResponse?> {
            override fun onResponse(
                call: Call<GetLocationsResponse?>,
                response: Response<GetLocationsResponse?>
            ) {
                mainActivity?.showLoading(false)
                var response = response.body()
                if (response != null ) {
                    if (response.result == BaseResponse.RESULT_OK) {
                        var list = ArrayList<Select2Item>()
                        response.data?.forEach { dt ->
                            list.add(Select2Item(dt.id, dt.name))
                        }

                        adapterLocation = Select2Adapter(requireContext(), list)
                        binding.textLocation.setAdapter<Select2Adapter>(adapterLocation)

                    } else {
                        showMessage(response.message ?: "Unknown error!", SweetAlertDialog.ERROR_TYPE)
                    }
                } else {
                    Toast.makeText(context, "Invalid response", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GetLocationsResponse?>, t: Throwable) {
                Toast.makeText(requireContext(), "Fail loading locations!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}