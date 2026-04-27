package com.inventory.app.mobile.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventory.app.mobile.utils.rest.response.SignInResponse


class SessionManager(private var context : Context) {
    private val _privateMode = 0
    private val _prefName = "rfid"
    private var pref: SharedPreferences = context.getSharedPreferences(
        _prefName,
        _privateMode
    )

    private var editor: SharedPreferences.Editor = pref.edit()

    fun setSessionId(sessionId : String) {
        editor.putString("sessionId", sessionId)
        editor.apply()
    }

    fun getSessionId() : String? {
        return pref.getString("sessionId", null)
    }
    fun storeLoginResult(loginResult: SignInResponse) {
        var gson = Gson()
        var json = gson.toJson(loginResult)
        editor.putString("loginResult", json)
        editor.commit()
    }

    fun getServerUrl() : String {
        return pref.getString("serverUrl", Params.URL) ?: ""
    }

    fun getDeviceAddress() : String? {
        return pref.getString("device_address", "")
    }

    fun setDeviceAddress(deviceAddress : String) {
        val prev = getDeviceAddress()
        if (prev != null && prev.compareTo(deviceAddress) != 0) {
            val edit = pref.edit()
            edit.putString("device_address", deviceAddress)
            edit.apply()
        }
    }

    fun getRfidPower() : Byte {
        var res = pref.getInt("power", 0)
        return (res and 0xFF).toByte()
    }

    fun setServerUrl(serverUrl : String) {
        editor.putString("serverUrl", serverUrl)
        editor.apply()
    }

    fun setRfidPower(power : Int) {
        editor.putInt("power", power)
        editor.apply()
    }

    fun setMenu(menu : List<String>) {
        val set: MutableSet<String> = HashSet<String>(menu)
        editor.putStringSet("menu", set)
        editor.apply()
    }

    fun getMenu() : List<String> {
        val set: MutableSet<String>? = pref.getStringSet("menu", HashSet<String>())
        if (set != null) {
            return set.toMutableList()
        }
        return ArrayList()
    }

}