package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.TransferItem

class TransferListInitResponse : BaseResponse() {
    var locationUpdate : String = ""
    var locationCount : Int = 0
    var transferItems : ArrayList<TransferItem>  = ArrayList<TransferItem>();
}