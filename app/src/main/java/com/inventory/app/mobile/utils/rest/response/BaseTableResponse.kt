package com.inventory.app.mobile.utils.rest.response

open class BaseTableResponse<T : Any> : BaseResponse() {
    var skip : Int = 0
    var pageSize : Int = 0
    var recordsTotal : Int = 0
    var recordsFiltered : Int = 0
    var data : ArrayList<Any> = ArrayList<Any>()
}