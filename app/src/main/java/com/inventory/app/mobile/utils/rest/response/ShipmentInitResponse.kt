package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.models.TransferItem

class ShipmentInitResponse : BaseResponse() {
    var id : Long = 0L
    var no : String = ""
    var locationName : String = ""
    var items : ArrayList<SimpleItem> = ArrayList()
}