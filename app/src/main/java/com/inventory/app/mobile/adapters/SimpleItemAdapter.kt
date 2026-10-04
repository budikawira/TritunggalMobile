package com.inventory.app.mobile.adapters

import android.annotation.SuppressLint
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
    var colorDanger = appCtx.getColor(R.color.colorDanger)

    // Checkbox-selection mode, used by ChildItemsFragment's "ungroup" picker.
    var selectionModeEnabled = false
    val selectedItems : MutableSet<SimpleItem> = mutableSetOf()
    var onSelectionChanged : (() -> Unit)? = null

    class SimpleItemViewHolder(val binding: RowSimpleItemBinding) : RecyclerView.ViewHolder(binding.root) {
        // Captured before any status coloring is ever applied, so a recycled row can be reset
        // back to its normal appearance instead of leaking a stale ok/danger background.
        val defaultCardBackgroundColor = binding.cardView.cardBackgroundColor
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

        if (currentItem.name.isNullOrEmpty()) {
            holder.binding.textName.visibility = View.GONE
        } else {
            holder.binding.textName.visibility = View.VISIBLE
            holder.binding.textName.text = currentItem.name
        }
        holder.binding.textEpc.text = currentItem.epc ?: "-"

        // Show the item's Type (Item / Group / BOM) as an icon
        holder.binding.imgType.setImageResource(currentItem.typeIconRes())
        holder.binding.imgType.contentDescription =
            holder.itemView.context.getString(currentItem.typeLabelRes())

        // Extra badge for items that belong to a parent (a group's child scanned directly)
        holder.binding.imgChildBadge.visibility =
            if (currentItem.parentItemId != null) View.VISIBLE else View.GONE

        if (currentItem.parentItemNo.isNullOrEmpty()) {
            holder.binding.textParentItemNo.visibility = View.GONE
        } else {
            holder.binding.textParentItemNo.visibility = View.VISIBLE
            holder.binding.textParentItemNo.text = currentItem.parentItemNo
        }

        if (currentItem.serialNo.isNullOrEmpty()) {
            holder.binding.textSerialNo.visibility = View.GONE
        } else {
            holder.binding.textSerialNo.visibility = View.VISIBLE
            holder.binding.textSerialNo.text = currentItem.serialNo
        }

        holder.binding.checkboxSelect.setOnCheckedChangeListener(null)
        if (selectionModeEnabled) {
            holder.binding.checkboxSelect.visibility = View.VISIBLE
            holder.binding.checkboxSelect.isChecked = selectedItems.contains(currentItem)
            holder.binding.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(currentItem) else selectedItems.remove(currentItem)
                onSelectionChanged?.invoke()
            }
            holder.binding.cardView.setOnClickListener {
                holder.binding.checkboxSelect.toggle()
            }
        } else {
            holder.binding.checkboxSelect.visibility = View.GONE
            holder.binding.cardView.setOnClickListener { view ->
                listener?.onClick(position, view, currentItem)
            }
        }

        if (nok.containsKey(currentItem.no)) {
            holder.binding.textStatus.text = nok[currentItem.no]
            holder.binding.textStatus.visibility = View.VISIBLE
            holder.binding.cardView.setCardBackgroundColor(colorDanger)
        } else {
            holder.binding.textStatus.visibility = View.GONE
            if (ok.contains(currentItem.no)) {
                holder.binding.cardView.setCardBackgroundColor(colorOk)
            } else {
                holder.binding.cardView.setCardBackgroundColor(holder.defaultCardBackgroundColor)
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

    fun findItemById(id: Long): SimpleItem? {
        return data.firstOrNull { it.id == id }
    }

    fun removeItem(item: SimpleItem) {
        val position = data.indexOf(item)
        if (position == -1) return
        data.removeAt(position)
        epcs.remove(item.epc)
        notifyItemRemoved(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectionMode(enabled: Boolean) {
        selectionModeEnabled = enabled
        selectedItems.clear()
        notifyDataSetChanged()
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

    // Points this adapter at a different backing list (e.g. switching between the "Ready to
    // Upload" and "Needs Review" tabs) without touching the shared ok/nok/epcs bookkeeping,
    // which stays valid across whichever list is currently rendered.
    @SuppressLint("NotifyDataSetChanged")
    fun switchData(newData: ArrayList<SimpleItem>) {
        data = newData
        notifyDataSetChanged()
    }

    // Adds an item to an arbitrary list (not necessarily the one currently bound/rendered),
    // still respecting the shared epc-dedup set. Used to route newly-scanned items into
    // whichever of the two tab lists they belong to, even while the other tab is on screen.
    fun addTo(list: ArrayList<SimpleItem>, item: SimpleItem): Boolean {
        if (!item.epc.isNullOrEmpty() && epcs.contains(item.epc)) return false
        list.add(item)
        if (!item.epc.isNullOrEmpty()) epcs.add(item.epc!!)
        return true
    }

    // Relocates an item that's already tracked in the shared epcs set (its epc was reserved when
    // it first landed in one of the two lists) into a different list - e.g. Needs Review -> Ready
    // to Upload. Unlike addTo(), this never epc-checks: the epc is deliberately still reserved
    // from the item's original add (see PlacementFragment.removeFromReviewList's freeForRescan
    // flag), so re-checking it here would always find a "conflict" against itself and silently
    // drop the item. Callers must do their own id-based duplicate check first.
    fun moveTo(list: ArrayList<SimpleItem>, item: SimpleItem) {
        list.add(item)
    }

    // Same idea as getDataToUpload(), but operates on an explicit list rather than the bound
    // `data`, since the uploadable ("Ready to Upload") list may not be the one on screen.
    fun getUploadable(list: List<SimpleItem>): List<SimpleItem> {
        return list.filter { !ok.contains(it.no) }
    }
}
