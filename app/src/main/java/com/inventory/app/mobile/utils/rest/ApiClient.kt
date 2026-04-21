package com.inventory.app.mobile.utils.rest

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object ApiClient {
    private var retrofit: Retrofit? = null
    private var gson: Gson? = null
    private var context: Context? = null
    private var baseUrl: String = ""


    fun setup(context: Context, baseUrl: String) {
        this.context = context;
        this.baseUrl = baseUrl
        retrofit = null // Reset Retrofit instance to apply new base URL
    }

    val client : Retrofit
        get() {
            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

            val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor)
                //.addInterceptor(OdooSessionInterceptor(context!!)) // Add session interceptor
                .readTimeout(150, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS).build()

            if (retrofit == null) {
                gson = GsonBuilder()
                    .setLenient()
                    .serializeNulls()
                    .create()

                retrofit = Retrofit.Builder()
                    //.baseUrl(Params.URL)
                    .baseUrl(this.baseUrl)
                    .client(client)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create(gson!!))
                    .build()
            }

            return retrofit!!
        }
}