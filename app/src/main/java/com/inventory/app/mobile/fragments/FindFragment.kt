package com.inventory.app.mobile.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import cn.pedant.SweetAlert.SweetAlertDialog
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.FragmentFindBinding
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetEpcByStickerNoRequest
import com.inventory.app.mobile.utils.rest.response.BaseObjectResponse
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * A simple [Fragment] subclass.
 * Use the [FindFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FindFragment : BaseFragment() {
    companion object {
        private const val TAG = "FindFragment"
    }
    private var _binding : FragmentFindBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFindBinding.inflate(inflater, container, false)

        sessionManager = SessionManager(requireContext())
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainActivity?.currentFragment = this
        super.onViewCreated(view, savedInstanceState)
        var list = ArrayList<String>()
        list.add(requireContext().getString(R.string.product_code))
        list.add(requireContext().getString(R.string.serial_number))
        // Create an ArrayAdapter
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)
        // Set adapter to the MaterialAutoCompleteTextView

        binding.btnSearch.setOnClickListener {
            searchBySn()
        }
    }

    private fun searchBySn() {
        var input = binding.etInput.text.toString()
        var request = apiInterface.getEpcByStickerNo(GetEpcByStickerNoRequest(input))
        request.enqueue(object : Callback<BaseObjectResponse<String>?> {
            override fun onResponse(
                call: Call<BaseObjectResponse<String>?>,
                response: Response<BaseObjectResponse<String>?>
            ) {
                mainActivity?.showLoading(false)
                var response = response.body()
                if (response != null ) {
                    if (response.result == BaseResponse.RESULT_OK && response.data != null) {
                        var epc = response.data!!
                        val action = FindFragmentDirections
                            .actionFindFragmentToFindScanFragment(epc = epc)
                        findNavController().navigate(action)
                    } else {
                        showMessage(response?.message ?: "Unknown error!", SweetAlertDialog.ERROR_TYPE)
                    }
                } else {
                    Toast.makeText(context, "Invalid response", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BaseObjectResponse<String>?>, t: Throwable) {

            }
        })
    }


}