package com.inventory.app.mobile.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.FragmentHomeBinding

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
        binding.cardPairing.setOnClickListener{
            findNavController().navigate(R.id.action_homeFragment_to_pairingFragment)
        }
        binding.cardFind.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_findFragment)
        }
        binding.cardTransfer.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_transferListFragment)
        }
        binding.cardShipment.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_shipmentListFragment)
        }
        binding.cardStockOpname.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_stockOpnameFragment)
        }
    }
}