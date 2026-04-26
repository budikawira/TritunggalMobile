package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.SimpleItem

class TransferInitResponse : BaseResponse() {
    var id : Long = 0L
    var no : String = ""
    var srcLocationParentNames : List<String> = ArrayList()
    var srcLocationName : String = ""
    var destLocationParentNames : List<String> = ArrayList()
    var destLocationName : String = ""
    var items : ArrayList<SimpleItem> = ArrayList()
}