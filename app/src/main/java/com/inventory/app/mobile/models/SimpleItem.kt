package com.inventory.app.mobile.models

import com.inventory.app.mobile.R
import java.io.Serializable

open class SimpleItem : Serializable {
    var id : Long = 0L
    var sku : String? = null
    var name : String? = null
    var no : String = ""
    var epc : String? = null
    var type : Int = TYPE_ITEM
    var parentItemId : Long? = null
    var parentItemNo : String? = null
    var serialNo : String? = null
    var locationId : Long? = null
    var locationName : String? = null

    companion object {
        // Mirrors TritunggalWeb.Domain.Entities.Inventories.Item.Type / SimpleItemVM.Type
        const val TYPE_ITEM = 0  // Regular item, can be FG
        const val TYPE_GROUP = 1 // Group item, cannot be FG and not counted in inventory, used for grouping items
        const val TYPE_BOM = 2   // Bill of Materials, item consumed in production, cannot be FG and not counted in inventory
    }

    fun typeIconRes() : Int {
        return when (type) {
            TYPE_GROUP -> R.drawable.ic_item_type_group
            TYPE_BOM -> R.drawable.ic_item_type_bom
            else -> R.drawable.ic_item_type_item
        }
    }

    fun typeLabelRes() : Int {
        return when (type) {
            TYPE_GROUP -> R.string.type_group
            TYPE_BOM -> R.string.type_bom
            else -> R.string.type_item
        }
    }
}
