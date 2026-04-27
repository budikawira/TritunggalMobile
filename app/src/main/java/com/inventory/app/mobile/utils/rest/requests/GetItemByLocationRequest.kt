package com.inventory.app.mobile.utils.rest.requests

class GetItemByLocationRequest(
    var locationId : Long = 0,
    var includeSubLocation : Boolean = false) {
}