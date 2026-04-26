package com.inventory.app.mobile.utils

class Params {
    companion object {
        //const val URL = "https://trackerindo-api.sistemdigital.my.id:8444/"
        const val URL = "http://10.0.2.2:5054/"

        const val MIN_POWER = 1
        const val MAX_POWER = 30

        const val DEBUG = true

        const val MENU_PAIRING = "pair"
        const val MENU_PLACEMENT = "plc"
        const val MENU_PLACEMENT_CONFIRM = "plc_confirm"
        const val MENU_TRANSFER = "trx"
        const val MENU_TRANSFER_CONFIRM_OUT = "trx_confirm_out"
        const val MENU_TRANSFER_CONFIRM_IN = "trx_confirm_in"
        const val MENU_SHIPMENT = "shp"
        const val MENU_STOCK_OPNAME = "so"
        const val MENU_FIND = "find"
    }
}