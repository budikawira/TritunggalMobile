package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.SimpleItem

class TransferUploadResponse : BaseResponse() {
    var items : ArrayList<SimpleItem> = ArrayList<SimpleItem>()
}