package com.inventory.app.mobile.models

import java.util.stream.LongStream

class ShelfSlot(
    var id : Long,
    var name : String,
    var locationId : Long,
    var locationName : String,
    var shelfId : Long,
    var shelfName : String
)