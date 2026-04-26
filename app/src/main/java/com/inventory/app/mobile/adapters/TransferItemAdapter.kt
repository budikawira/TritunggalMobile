package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.databinding.RowTransferBinding
import com.inventory.app.mobile.models.Transfer

class TransferItemAdapter (
    var data: ArrayList<Transfer>,
    var listener : OnItemClick?
) : RecyclerView.Adapter<TransferItemAdapter.TransferItemViewHolder>() {
    var isPlacement : Boolean = false
    // ViewHolder class using View Binding
    class TransferItemViewHolder(val binding: RowTransferBinding) : RecyclerView.ViewHolder(binding.root) {
        // No need to findViewById here, views are accessed via binding object
    }

    interface OnItemClick {
        fun onClick(position: Int, transferItem : Transfer)
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
        if (!isPlacement) {
            var srcLoc = ""
            if (currentItem.srcLocationParentNames.isNotEmpty()) {
                srcLoc = currentItem.srcLocationParentNames.joinToString(" ➤ ")
                srcLoc += " ➤ " + currentItem.srcLocationName
            }
            holder.binding.textSrc.text = srcLoc
        } else {
            holder.binding.textSrc.visibility = View.GONE
            holder.binding.textSrcLabel.visibility = View.GONE
            holder.binding.textDestLabel.visibility = View.GONE
        }

        var destLoc = ""
        if (currentItem.destLocationParentNames.isNotEmpty()) {
            destLoc = currentItem.destLocationParentNames.joinToString(" ➤ ")
            destLoc += " ➤ " + currentItem.destLocationName
        }
        holder.binding.textDest.text = destLoc

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