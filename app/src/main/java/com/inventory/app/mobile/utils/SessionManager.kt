package com.inventory.app.mobile.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventory.app.mobile.models.Location
import com.inventory.app.mobile.utils.rest.response.SignInResponse


class SessionManager(private var context : Context) {
    private val _privateMode = 0
    private val _prefName = "rfid"
    private var pref: SharedPreferences

    private var editor: SharedPreferences.Editor

    init {
        pref = context.getSharedPreferences(
            _prefName,
            _privateMode
        )
        editor = pref.edit()
    }

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

    fun setLocationUpdate(locationUpdate : String) {
        editor.putString("locUpdate", locationUpdate)
        editor.apply()
    }

    fun getLocationUpdate() : String {
        return pref.getString("locUpdate", "") ?: ""
    }

    fun setLocationList(locations : ArrayList<Location>) {
        var gson = Gson()
        var json = gson.toJson(locations)
        editor.putString("locations", json)
        editor.apply()
    }

    fun getLocationList() : ArrayList<Location> {
        var gson = Gson()
        var json = pref.getString("locations", "")
        if (json == "") {
            return ArrayList<Location>()
        }
        val type = object : TypeToken<ArrayList<Location>?>() {}.getType()
        val locationList: ArrayList<Location> = gson.fromJson(json, type) ?: ArrayList<Location>()
        return locationList
    }
}