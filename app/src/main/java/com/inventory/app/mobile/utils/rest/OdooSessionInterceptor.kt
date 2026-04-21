package com.inventory.app.mobile.utils.rest

import android.content.Context
import com.inventory.app.mobile.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class OdooSessionInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val sessionManager = SessionManager(context)
        val sessionId = sessionManager.getSessionId()

        val originalRequest = chain.request()
        val newRequest = if (sessionId != null) {
            originalRequest.newBuilder()
                .addHeader("Cookie", sessionId)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}