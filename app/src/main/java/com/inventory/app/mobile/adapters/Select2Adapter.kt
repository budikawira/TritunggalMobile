package com.inventory.app.mobile.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.inventory.app.mobile.R
import com.inventory.app.mobile.models.Select2Item
import java.util.Locale


class Select2Adapter(context: Context, items: ArrayList<Select2Item>) :
    ArrayAdapter<Select2Item>(context, 0, items) {
    private val originalItems: ArrayList<Select2Item> = ArrayList<Select2Item>()
    private val filteredItems: ArrayList<Select2Item> = ArrayList<Select2Item>()

    init {
        originalItems.addAll(items)
        filteredItems.addAll(items)
    }

    fun updateData(newItems: ArrayList<Select2Item>) {
        originalItems.clear()
        originalItems.addAll(newItems)
        filteredItems.clear()
        filteredItems.addAll(newItems)
        clear()
        addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        var convertView = convertView
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_select2, parent, false)
        }

        val item: Select2Item? = getItem(position)

        if (item != null) {
            val textView = convertView!!.findViewById<TextView?>(R.id.text)
            textView.text = item.text
        }
        return convertView!!
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            protected override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results: FilterResults = FilterResults()
                val suggestions: MutableList<Select2Item?> = ArrayList<Select2Item?>()

                if (constraint == null || constraint.isEmpty()) {
                    suggestions.addAll(originalItems)
                } else {
                    val filterPattern =
                        constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
                    for (item in originalItems) {
                        if (item.text.lowercase().contains(filterPattern)) {
                            suggestions.add(item)
                        }
                    }
                }
                results.values = suggestions
                results.count = suggestions.size
                return results
            }

            protected override fun publishResults(
                constraint: CharSequence?,
                results: FilterResults
            ) {
                clear()
                var list = results.values as ArrayList<Select2Item>?
                if (list != null) {
                    addAll(list)
                    notifyDataSetChanged()
                }
            }
        }
    }
}