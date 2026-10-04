package com.inventory.app.mobile.utils.rest.response

import com.google.gson.annotations.SerializedName
import com.inventory.app.mobile.models.SimpleItem

class ShipmentInitResponse : BaseResponse() {
    var id : Long = 0L
    var no : String = ""
    // The backend reuses TransferInitResponse (Src/DestLocation naming) for ShipmentInit - a
    // Shipment only has one location, so these are kept short here rather than propagating the
    // Src/Dest naming into a screen that has no destination.
    @SerializedName("srcLocationId")
    var locationId : Long? = null
    @SerializedName("srcLocationName")
    var locationName : String = ""
    var items : ArrayList<SimpleItem> = ArrayList()
}
