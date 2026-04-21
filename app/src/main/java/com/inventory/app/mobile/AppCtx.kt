package com.inventory.app.mobile

import android.app.Application

const val IS_NEW_SETUP = "newSetup"

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