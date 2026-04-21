package com.inventory.app.mobile.utils.rest.response

open class BaseResponse {
    var result: Int = RESULT_NOK
    var message: String? = null

    companion object {
        const val RESULT_OK = 0
        const val RESULT_NOK = 1
    }
}