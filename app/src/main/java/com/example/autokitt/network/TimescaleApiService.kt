package com.example.autokitt.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TimescaleApiService {
    
    @POST("sensor_data")
    suspend fun postSensorData(@Body data: SensorDataPayload): Response<Void>
    
    @POST("sensor_data/batch")
    suspend fun postSensorDataBatch(@Body data: List<SensorDataPayload>): Response<Void>

    @GET("sensor_data/history")
    suspend fun getHistoricalData(
        @Query("device_id") deviceId: String,
        @Query("start_time") startTime: Long,
        @Query("end_time") endTime: Long
    ): Response<List<AggregatedDataResponse>>
}
