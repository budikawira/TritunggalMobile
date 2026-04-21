package com.inventory.app.mobile.utils.rest.response

import com.inventory.app.mobile.models.ShipmentItem

class ShipmentListInitResponse : BaseResponse() {
    var locationUpdate : String = ""
    var locationCount : Int = 0
    var shipItems : ArrayList<ShipmentItem>  = ArrayList<ShipmentItem>();
}