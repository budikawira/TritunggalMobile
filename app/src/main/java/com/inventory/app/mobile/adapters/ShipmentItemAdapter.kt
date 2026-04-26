package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.databinding.RowTransferBinding
import com.inventory.app.mobile.models.ShipmentItem

class ShipmentItemAdapter (
    var data: ArrayList<ShipmentItem>,
    var listener : OnItemClick?
) : RecyclerView.Adapter<ShipmentItemAdapter.ShipmentItemViewHolder>() {
    // ViewHolder class using View Binding
    class ShipmentItemViewHolder(val binding: RowTransferBinding) : RecyclerView.ViewHolder(binding.root) {
        // No need to findViewById here, views are accessed via binding object
    }

    interface OnItemClick {
        fun onClick(position: Int, shipmentItem : ShipmentItem)
    }

    // Called when RecyclerView needs a new ViewHolder of the given type to represent an item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShipmentItemViewHolder {
        // Inflate the row_transfer.xml layout using View Binding
        val binding = RowTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShipmentItemViewHolder(binding)
    }
    // Called by RecyclerView to display the data at the specified position
    override fun onBindViewHolder(holder: ShipmentItemViewHolder, position: Int) {
        val currentItem = data[position]

        // Bind data to the TextViews using binding object
        holder.binding.textNo.text = currentItem.no
        holder.binding.textSrc.text = currentItem.locationName
        holder.binding.textDest.visibility = View.GONE

        // Set click listener for the CardView using binding object
        holder.binding.cardView.setOnClickListener {
            listener?.onClick(position, currentItem)
        }
    }
    // Returns the total number of items in the data set held by the adapter
    override fun getItemCount(): Int {
        return data.size
    }
}