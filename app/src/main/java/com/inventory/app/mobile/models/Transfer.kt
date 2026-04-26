package com.inventory.app.mobile.models

class Transfer {
    var id : Long = 0
    var no : String = ""
    var createdDateString : String = ""
    var srcLocationName : String = ""
    var srcLocationParentNames : List<String> = ArrayList()
    var destLocationName : String = ""
    var destLocationParentNames : List<String> = ArrayList()

    var itemCount: Int = 0
}