package com.inventory.app.mobile.utils.rest.requests

class RegisterItemRequest(
    var masterItemId: Long,
    var quantity: String,
    var unitTypeId: Long,
    var epcList: ArrayList<String>
)
