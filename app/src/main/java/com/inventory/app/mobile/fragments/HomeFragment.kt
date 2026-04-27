package com.inventory.app.mobile.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.FragmentHomeBinding
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager

class HomeFragment : BaseFragment() {
    companion object {
        private const val TAG = "HomeFragment"
    }
    private var _binding : FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        val menu = sessionManager.getMenu()
        binding.cardPairing.setOnClickListener{
            findNavController().navigate(R.id.action_homeFragment_to_pairingFragment)
        }
        if (!menu.contains(Params.MENU_PAIRING)) {
            binding.cardPairing.visibility = View.GONE
        }

        binding.cardFind.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_findFragment)
        }
        if (!menu.contains(Params.MENU_FIND)) {
            binding.cardFind.visibility = View.GONE
        }

        binding.cardPlacement.setOnClickListener {
            //findNavController().navigate(R.id.action_homeFragment_to_transferListFragment)
        }
        if (!menu.contains(Params.MENU_PLACEMENT)) {
            binding.cardPlacement.visibility = View.GONE
        }

        binding.cardTransfer.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_transferListFragment)
        }
        if (!menu.contains(Params.MENU_TRANSFER)) {
            binding.cardTransfer.visibility = View.GONE
        }
        if (!menu.contains(Params.MENU_TRANSFER_CONFIRM_IN)) {
            binding.cardTransferIn.visibility = View.GONE
        }

        binding.cardShipment.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_shipmentListFragment)
        }
        if (!menu.contains(Params.MENU_SHIPMENT)) {
            binding.cardShipment.visibility = View.GONE
        }

        binding.cardStockOpname.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_stockOpnameFragment)
        }
        if (!menu.contains(Params.MENU_STOCK_OPNAME)) {
            binding.cardStockOpname.visibility = View.GONE
        }

        binding.cardPlacement.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_placementListFragment)
        }
    }
}