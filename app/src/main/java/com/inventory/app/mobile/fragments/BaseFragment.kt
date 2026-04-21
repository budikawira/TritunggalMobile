package com.inventory.app.mobile.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import cn.pedant.SweetAlert.SweetAlertDialog
import com.inventory.app.mobile.R
import com.inventory.app.mobile.activities.MainActivity
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import kotlin.ranges.rangeTo
import kotlin.text.toInt
import kotlin.toString


open class BaseFragment : Fragment() {
    protected val mainActivity : MainActivity? get() = activity as MainActivity?
    var flow : Int = 0
    protected lateinit var sessionManager : SessionManager
    protected lateinit var apiInterface: ApiInterface
    protected var radioPower : Int = Params.MAX_POWER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(requireContext())
        radioPower = sessionManager.getRfidPower().toInt()
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onPowerUpdated()
    }

    protected fun showMessage(text: String, dialogType : Int = SweetAlertDialog.SUCCESS_TYPE,
                              listenerOk : SweetAlertDialog.OnSweetClickListener? = null,
                              listenerCancel : SweetAlertDialog.OnSweetClickListener? = null,
                              okText : String = "Ok",
                              cancelText : String = "Batal") {
        val dlg = SweetAlertDialog(requireContext(), dialogType)
        dlg.setTitleText(text)
        dlg.setConfirmText(okText)
        dlg.setConfirmClickListener { sweetAlertDialog ->
            sweetAlertDialog.dismiss()
            listenerOk?.onClick(sweetAlertDialog)
        }
        if (listenerOk != null) {
            dlg.setCancelText(cancelText)
            dlg.setCancelClickListener { sweetAlertDialog ->
                sweetAlertDialog.dismiss()
                listenerCancel?.onClick(sweetAlertDialog )
            }
        }
        dlg.show()
    }


    open fun ReaderOnKeyDwon() {
    }

    open fun onPowerUpdated() {

    }

    protected fun showPowerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_power, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerOptions)

        val options = (Params.MIN_POWER ..Params.MAX_POWER).map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(radioPower - Params.MIN_POWER)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Power")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                radioPower = spinner.selectedItem.toString().toInt()
                onPowerUpdated()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}