package com.example.autokitt.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // Note: Use an appropriate URL for deploying. 10.0.2.2 is loopback for Android Emulator.
    private const val BASE_URL = "http://10.0.2.2:3000/api/v1/"

    val instance: TimescaleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TimescaleApiService::class.java)
    }
}
