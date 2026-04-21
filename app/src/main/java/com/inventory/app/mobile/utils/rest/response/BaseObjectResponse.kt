package com.inventory.app.mobile.utils.rest.response

open class BaseObjectResponse<T: Any?> : BaseResponse() {
    var data : T? = null
}