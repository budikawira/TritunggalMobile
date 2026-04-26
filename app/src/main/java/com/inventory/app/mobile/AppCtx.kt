package com.inventory.app.mobile

import android.app.Application

const val IS_NEW_SETUP = "newSetup"
const val REQUEST_ENABLE_BT = 101
const val REQUEST_SELECT_DEVICE = 102
const val SHOW_HISTORY_CONNECTED_LIST = "hist"
const val RUNNING_DISCONNECT_TIMER = 10
const val FLAG_START = 0 //开始

const val FLAG_STOP = 1 //停止

const val FLAG_UPDATE_TIME = 2 // 更新时间

const val FLAG_UHFINFO = 3
const val FLAG_UHFINFO_LIST = 5
const val FLAG_SUCCESS = 10 //成功
const val FLAG_FAIL = 11 //失败
class AppCtx : Application() {
    init {
        instance = this
    }

    companion object {
        private var instance: AppCtx? = null

        fun applicationContext(): AppCtx {
            return instance!!
        }
    }
}