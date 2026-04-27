package com.inventory.app.mobile.fragments

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.inventory.app.mobile.R
import com.inventory.app.mobile.activities.DialogListActivity
import com.inventory.app.mobile.databinding.FragmentPlacementCreateBinding
import com.inventory.app.mobile.models.Select2Item
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.PlacementCreateRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class PlacementCreateFragment : BaseFragment() {
    companion object {
        private const val TAG = "PlacementCreateFragment"
        private const val REQUEST_LOCATION = 1001
        private const val REQUEST_LOCATION1 = 1002
        private const val REQUEST_LOCATION2 = 1003
        private const val REQUEST_LOCATION3 = 1004
        private const val REQUEST_LOCATION4 = 1005
    }

    private var _binding: FragmentPlacementCreateBinding? = null
    private val binding get() = _binding!!

    private var selectedLocation: Select2Item? = null
    private var selectedLocation1: Select2Item? = null
    private var selectedLocation2: Select2Item? = null
    private var selectedLocation3: Select2Item? = null
    private var selectedLocation4: Select2Item? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlacementCreateBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainActivity?.currentFragment = this
        super.onViewCreated(view, savedInstanceState)

        binding.textLocation.setOnClickListener { openLocationDialog() }
        binding.textLocation1.setOnClickListener { openLocation1Dialog() }
        binding.textLocation2.setOnClickListener { openShelfSlotDialog() }
        binding.textLocation3.setOnClickListener { openLocation3Dialog() }
        binding.textLocation4.setOnClickListener { openLocation4Dialog() }

        setupClearIcon(binding.textLocation) { clearFrom(0) }
        setupClearIcon(binding.textLocation1) { clearFrom(1) }
        setupClearIcon(binding.textLocation2) { clearFrom(2) }
        setupClearIcon(binding.textLocation3) { clearFrom(3) }
        setupClearIcon(binding.textLocation4) { clearFrom(4) }

        binding.buttonStart.setOnClickListener {
            var selectedLocationId = selectedLocation?.value ?: 0L
            if (selectedLocation4 != null) {
                selectedLocationId = selectedLocation4!!.value
            } else if (selectedLocation3 != null) {
                selectedLocationId = selectedLocation3!!.value
            } else if (selectedLocation2 != null) {
                selectedLocationId = selectedLocation2!!.value
            } else if (selectedLocation1 != null) {
                selectedLocationId = selectedLocation1!!.value
            }

            if (selectedLocationId == 0L) {
                showToast("Please select the location")
                return@setOnClickListener
            }

            val request = apiInterface.placementCreate(
                "Bearer " + sessionManager.getSessionId(),
                PlacementCreateRequest(selectedLocationId)
            )
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
                            "Create fail! Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<BaseResponse?>, t: Throwable) {

                }
            })

        }
    }

    private fun showConfirmCompleteDialog() {
        val builder = AlertDialog.Builder(requireContext())

        // Set the dialog title
        builder.setTitle("Completed")

        builder.setMessage("Create placement successful.")

        // Set the Positive button (Yes/Confirm)
        builder.setPositiveButton("Ok") { dialog: DialogInterface, which: Int ->
            findNavController().navigate(R.id.action_placementCreateFragment_to_placementListFragment)
        }

        // Create the AlertDialog
        val dialog: AlertDialog = builder.create()
        // Show the dialog
        dialog.show()
    }


    // ── Open dialogs ─────────────────────────────────────────────────────────

    private fun openLocationDialog() {
        startDialog(REQUEST_LOCATION, getString(R.string.location_level_0), 0, 0L)
    }

    private fun openLocation1Dialog() {
        val parentId: Long = selectedLocation?.value ?: run {
            Toast.makeText(context, "Please select a location first.", Toast.LENGTH_SHORT).show()
            return
        }
        startDialog(REQUEST_LOCATION1, getString(R.string.location_level_1), 1, parentId)
    }

    private fun openShelfSlotDialog() {
        val parentId: Long = selectedLocation1?.value ?: run {
            Toast.makeText(context, "Please select a shelf first.", Toast.LENGTH_SHORT).show()
            return
        }
        startDialog(REQUEST_LOCATION2, getString(R.string.location_level_2), 2, parentId)
    }

    private fun openLocation3Dialog() {
        val parentId: Long = selectedLocation2?.value ?: run {
            Toast.makeText(context, "Please select a ${getString(R.string.location_level_2)} first.", Toast.LENGTH_SHORT).show()
            return
        }
        startDialog(REQUEST_LOCATION3, getString(R.string.location_level_3), 3, parentId)
    }

    private fun openLocation4Dialog() {
        val parentId: Long = selectedLocation3?.value ?: run {
            Toast.makeText(context, "Please select a ${getString(R.string.location_level_3)} first.", Toast.LENGTH_SHORT).show()
            return
        }
        startDialog(REQUEST_LOCATION4, getString(R.string.location_level_4), 4, parentId)
    }

    private fun startDialog(requestCode: Int, title: String, level: Int, parentId: Long) {
        val intent = Intent(requireActivity(), DialogListActivity::class.java).apply {
            putExtra(DialogListActivity.EXTRA_TITLE, title)
            putExtra(DialogListActivity.EXTRA_LEVEL, level)
            putExtra(DialogListActivity.EXTRA_PARENT_ID, parentId)
        }
        startActivityForResult(intent, requestCode)
    }

    // ── Handle result ─────────────────────────────────────────────────────────

    @Deprecated("Using for fragment compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        val id   = data.getLongExtra(DialogListActivity.RESULT_ITEM_ID, 0L)
        val text = data.getStringExtra(DialogListActivity.RESULT_ITEM_TEXT) ?: ""
        val item = Select2Item(id, text)

        when (requestCode) {
            REQUEST_LOCATION -> {
                clearFrom(1)
                selectedLocation = item
                binding.textLocation.text = text
                binding.textLocation.setTextColor(Color.BLACK)
                showClearIcon(binding.textLocation)
                binding.labelLocation1.visibility = View.VISIBLE
                binding.textLocation1.visibility  = View.VISIBLE
                binding.buttonStart.isEnabled = true
            }
            REQUEST_LOCATION1 -> {
                clearFrom(2)
                selectedLocation1 = item
                binding.textLocation1.text = text
                binding.textLocation1.setTextColor(Color.BLACK)
                showClearIcon(binding.textLocation1)
                binding.labelLocation2.visibility = View.VISIBLE
                binding.textLocation2.visibility  = View.VISIBLE
            }
            REQUEST_LOCATION2 -> {
                clearFrom(3)
                selectedLocation2 = item
                binding.textLocation2.text = text
                binding.textLocation2.setTextColor(Color.BLACK)
                showClearIcon(binding.textLocation2)
                binding.labelLocation3.visibility = View.VISIBLE
                binding.textLocation3.visibility  = View.VISIBLE
            }
            REQUEST_LOCATION3 -> {
                clearFrom(4)
                selectedLocation3 = item
                binding.textLocation3.text = text
                binding.textLocation3.setTextColor(Color.BLACK)
                showClearIcon(binding.textLocation3)
                binding.labelLocation4.visibility = View.VISIBLE
                binding.textLocation4.visibility  = View.VISIBLE
            }
            REQUEST_LOCATION4 -> {
                selectedLocation4 = item
                binding.textLocation4.text = text
                binding.textLocation4.setTextColor(Color.BLACK)
                showClearIcon(binding.textLocation4)
            }
        }
    }

    // ── Clear icon helpers ────────────────────────────────────────────────────

    private fun setupClearIcon(textView: TextView, onClear: () -> Unit) {
        textView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = textView.compoundDrawablesRelative[2]
                if (drawable != null) {
                    val clearStart = textView.right - textView.paddingEnd - drawable.bounds.width()
                    if (event.rawX >= clearStart) {
                        onClear()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun showClearIcon(textView: TextView) {
        val icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_close_clear_cancel)!!
        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null)
    }

    private fun hideClearIcon(textView: TextView) {
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
    }

    private fun clearFrom(level: Int) {
        if (level <= 0) {
            selectedLocation = null
            binding.textLocation.text = ""
            binding.textLocation.setTextColor(Color.GRAY)
            hideClearIcon(binding.textLocation)
            binding.buttonStart.isEnabled = false
        }
        if (level <= 1) {
            selectedLocation1 = null
            binding.textLocation1.text = ""
            hideClearIcon(binding.textLocation1)
            if (level < 1) {
                binding.labelLocation1.visibility = View.INVISIBLE
                binding.textLocation1.visibility  = View.INVISIBLE
            }
        }
        if (level <= 2) {
            selectedLocation2 = null
            binding.textLocation2.text = ""
            hideClearIcon(binding.textLocation2)
            if (level < 2) {
                binding.labelLocation2.visibility = View.INVISIBLE
                binding.textLocation2.visibility  = View.INVISIBLE
            }
        }
        if (level <= 3) {
            selectedLocation3 = null
            binding.textLocation3.text = ""
            hideClearIcon(binding.textLocation3)
            if (level < 3) {
                binding.labelLocation3.visibility = View.INVISIBLE
                binding.textLocation3.visibility  = View.INVISIBLE
            }
        }
        if (level <= 4) {
            selectedLocation4 = null
            binding.textLocation4.text = ""
            hideClearIcon(binding.textLocation4)
            if (level < 4) {
                binding.labelLocation4.visibility = View.INVISIBLE
                binding.textLocation4.visibility  = View.INVISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}