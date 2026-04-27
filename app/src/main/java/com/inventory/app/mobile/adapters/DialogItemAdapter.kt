package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.databinding.RowDialogItemBinding
import com.inventory.app.mobile.models.Select2Item

class DialogItemAdapter(
    var data: ArrayList<Select2Item>,
    private val listener: OnItemClick
) : RecyclerView.Adapter<DialogItemAdapter.ViewHolder>() {

    interface OnItemClick {
        fun onClick(item: Select2Item)
    }

    class ViewHolder(val binding: RowDialogItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowDialogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.binding.textName.text = item.text
        holder.itemView.setOnClickListener { listener.onClick(item) }
    }

    override fun getItemCount(): Int = data.size
}
