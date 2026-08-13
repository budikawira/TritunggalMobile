package com.inventory.app.mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.R

class NameListAdapter(
    private val items: ArrayList<String>,
    private val onDeleteClick: (position: Int, name: String) -> Unit
) : RecyclerView.Adapter<NameListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_name_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val name = items[position]
        holder.tvName.text = name
        holder.btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                val removed = items[pos]
                items.removeAt(pos)
                notifyItemRemoved(pos)
                notifyItemRangeChanged(pos, items.size)
                onDeleteClick(pos, removed)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addItem(name: String) {
        items.add(name)
        notifyItemInserted(items.size - 1)
    }

    fun getData(): ArrayList<String> = items
}
