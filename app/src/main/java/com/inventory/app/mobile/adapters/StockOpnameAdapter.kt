package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.databinding.RowSimpleItemBinding
import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.models.StockOpnameItem

class StockOpnameAdapter (
    appCtx : AppCtx,
    private var data: ArrayList<StockOpnameItem>,
    var listener : OnItemClick?
) : RecyclerView.Adapter<StockOpnameAdapter.SimpleItemViewHolder>() {
    private var modeIsScanned = false
    private var notScannedEpc = ArrayList<String>()
    private var scannedEpc = ArrayList<String>()
    private var filteredData = ArrayList<StockOpnameItem>()

    class SimpleItemViewHolder(val binding: RowSimpleItemBinding) : RecyclerView.ViewHolder(binding.root) {
        // No need to findViewById here, views are accessed via binding object
    }

    interface OnItemClick {
        fun onClick(position: Int, view: View, item : SimpleItem)
    }

    // Called when RecyclerView needs a new ViewHolder of the given type to represent an item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleItemViewHolder {
        // Inflate the row_transfer.xml layout using View Binding
        val binding = RowSimpleItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SimpleItemViewHolder(binding)
    }

    // Called by RecyclerView to display the data at the specified position
    override fun onBindViewHolder(holder: SimpleItemViewHolder, position: Int) {
        val currentItem = filteredData[position]

        // Bind data to the TextViews using binding object
        holder.binding.textNo.text = currentItem.no
        holder.binding.textSku.text = currentItem.sku
        holder.binding.textName.text = currentItem.name
        holder.binding.textEpc.text = currentItem.epc ?: ""

        // Set click listener for the CardView using binding object
        holder.binding.cardView.setOnClickListener { view ->
            listener?.onClick(position, view, currentItem)
        }

    }
    // Returns the total number of items in the data set held by the adapter
    override fun getItemCount(): Int {
        return filteredData.size
    }

    fun countNotScanned() : Int {
        return data.size - scannedEpc.count()
    }
    fun countScanned() : Int {
        return scannedEpc.count()
    }

    fun setScanned(epc : String) : Boolean {
        if (notScannedEpc.contains(epc)) {
            data.forEach { dt ->
                if (dt.epc == epc) {
                    dt.isScanned = true
                    scannedEpc.add(epc)
                    if (modeIsScanned) {
                        filteredData.add(dt)
                    } else {
                        filteredData.remove(dt)
                    }
                    notScannedEpc.remove(epc)
                    return true
                }
            }
        }
        return false
    }

    fun setMode(isScanned : Boolean) {
        modeIsScanned = isScanned
        filteredData.clear()
        data.forEach { dt ->
            if (dt.isScanned == isScanned) {
                filteredData.add(dt)
            }
        }
        notifyDataSetChanged()
    }
    fun setData(data: ArrayList<SimpleItem>) {
        notScannedEpc.clear()
        this.data.clear()
        this.filteredData.clear()
        data.forEach { dt ->
            var item = StockOpnameItem()
            item.id = dt.id
            item.sku = dt.sku
            item.name = dt.name
            item.no = dt.no
            item.epc = dt.epc
            item.isScanned = false
            this.data.add(item)
            this.filteredData.add(item)
            if (!dt.epc.isNullOrEmpty()) {
                notScannedEpc.add(dt.epc!!)
            }
        }
    }

    fun getData() : ArrayList<StockOpnameItem> {
        return data
    }

}