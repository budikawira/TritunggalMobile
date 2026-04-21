package com.inventory.app.mobile.utils.rest.requests

class StockOpnameUploadRequest(
    var stockOpnameId : Long,
    var locationId : Long?,
    var shelfId : Long?,
    var shelfSlotId : Long?,
    var scannedItemIds : ArrayList<Long>,
    var notScannedItemIds : ArrayList<Long>
    )