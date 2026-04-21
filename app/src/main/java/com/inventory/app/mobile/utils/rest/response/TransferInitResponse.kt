package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.SimpleItem
import com.inventory.app.mobile.models.TransferItem

class TransferInitResponse : BaseResponse() {
    var id : Long = 0L
    var no : String = ""
    var sourceLocationName : String = ""
    var destinationLocationName : String = ""
    var items : ArrayList<SimpleItem> = ArrayList()
}