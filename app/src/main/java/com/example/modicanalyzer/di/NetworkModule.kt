package com.example.modicanalyzer.di

import com.example.modicanalyzer.data.api.SpinoCareApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * Base URL for the API
     * For local development with XAMPP
     * 
     * IMPORTANT: Using USB tethering IP address
     * - Current: "http://10.20.139.33/spinocare-api/" (your laptop's IP via USB tethering)
     * - Your phone is connected via USB tethering (Ethernet 2)
     * - Phone's gateway IP: 10.20.139.174
     * - Laptop's assigned IP: 10.20.139.33
     * - If connection fails, run: ipconfig to find current "Ethernet 2" IP
     * - Update this IP if tethering reconnects with different IP
     */
    private const val BASE_URL = "http://10.20.139.33/spinocare-api/"
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideSpinoCareApiService(retrofit: Retrofit): SpinoCareApiService {
        return retrofit.create(SpinoCareApiService::class.java)
    }
}
