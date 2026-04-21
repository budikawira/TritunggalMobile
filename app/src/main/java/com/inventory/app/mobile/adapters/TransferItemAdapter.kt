package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.databinding.RowTransferBinding
import com.inventory.app.mobile.models.TransferItem

class TransferItemAdapter (
    var data: ArrayList<TransferItem>,
    var listener : OnItemClick?
) : RecyclerView.Adapter<TransferItemAdapter.TransferItemViewHolder>() {
    // ViewHolder class using View Binding
    class TransferItemViewHolder(val binding: RowTransferBinding) : RecyclerView.ViewHolder(binding.root) {
        // No need to findViewById here, views are accessed via binding object
    }

    interface OnItemClick {
        fun onClick(position: Int, transferItem : TransferItem)
    }

    // Called when RecyclerView needs a new ViewHolder of the given type to represent an item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferItemViewHolder {
        // Inflate the row_transfer.xml layout using View Binding
        val binding = RowTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransferItemViewHolder(binding)
    }
    // Called by RecyclerView to display the data at the specified position
    override fun onBindViewHolder(holder: TransferItemViewHolder, position: Int) {
        val currentItem = data[position]

        // Bind data to the TextViews using binding object
        holder.binding.textNo.text = currentItem.no
        holder.binding.textSrc.text = currentItem.sourceLocationName
        holder.binding.textDest.text = currentItem.destinationLocationName

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