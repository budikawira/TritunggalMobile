package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.SimpleItem

class ShipmentUploadResponse : BaseResponse() {
    var items : ArrayList<SimpleItem> = ArrayList<SimpleItem>()
}