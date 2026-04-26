package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.AppCtx
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.RowSimpleItemBinding
import com.inventory.app.mobile.models.SimpleItem

class SimpleItemAdapter (
    appCtx : AppCtx,
    private var data: ArrayList<SimpleItem>,
    var listener : OnItemClick?
) : RecyclerView.Adapter<SimpleItemAdapter.SimpleItemViewHolder>() {
    var ok : ArrayList<String> = ArrayList()
    var nok : HashMap<String, String> = HashMap()
    var epcs : ArrayList<String> = ArrayList()

    var colorOk = appCtx.getColor(R.color.colorOk)

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
        val currentItem = data[position]

        // Bind data to the TextViews using binding object
        holder.binding.textNo.text = currentItem.no
        holder.binding.textSku.text = currentItem.sku
        holder.binding.textName.text = currentItem.name
        holder.binding.textEpc.text = currentItem.epc ?: ""

        // Set click listener for the CardView using binding object
        holder.binding.cardView.setOnClickListener { view ->
            listener?.onClick(position, view, currentItem)
        }

        if (nok.containsKey(currentItem.no)) {
            holder.binding.textStatus.text = nok[currentItem.epc]
            holder.binding.textStatus.visibility = View.VISIBLE
        } else {
            holder.binding.textStatus.visibility = View.GONE
            if (ok.contains(currentItem.no)) {
                holder.binding.cardView.setCardBackgroundColor(colorOk)
            }
        }
    }
    // Returns the total number of items in the data set held by the adapter
    override fun getItemCount(): Int {
        return data.size
    }

    fun getDataToUpload() : ArrayList<SimpleItem> {
        var res = ArrayList<SimpleItem>()
        for (item in data) {
            //filter only data that are not uploaded yet
            if (!ok.contains(item.no)) {
                res.add(item)
            }
        }
        return res
    }

    fun addData(item: SimpleItem) {
        if (!epcs.contains(item.epc)) {
            //no duplication
            data.add(item)
            epcs.add(item.epc!!)
        }
    }

    fun initData(data: ArrayList<SimpleItem>, isOk: Boolean) {
        this.data = data
        if (isOk) {
            ok = ArrayList()
            epcs = ArrayList()
            for (item in data) {
                ok.add(item.no)
                if (!item.epc.isNullOrEmpty()) {
                    epcs.add(item.epc!!)
                }
            }
        }
    }
}