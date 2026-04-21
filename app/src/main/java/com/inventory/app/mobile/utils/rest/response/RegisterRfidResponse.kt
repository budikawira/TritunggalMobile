package com.inventory.app.mobile.utils.rest.response

class RegisterRfidResponse : BaseResponse() {
    var nok : ArrayList<RegisterRfidResult> = ArrayList()
    var ok : ArrayList<RegisterRfidResult> = ArrayList()
}