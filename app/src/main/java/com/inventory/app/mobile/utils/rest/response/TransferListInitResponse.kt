package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.Transfer

class TransferListInitResponse : BaseResponse() {
    var transfers : ArrayList<Transfer>  = ArrayList<Transfer>();
}