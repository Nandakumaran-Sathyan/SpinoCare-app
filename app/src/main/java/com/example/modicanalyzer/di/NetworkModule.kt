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
     * IMPORTANT: Changed to local IP because 10.0.2.2 doesn't work on this system
     * - Current: "http://10.69.68.243/spinocare-api/" (your PC's local IP)
     * - If connection fails, update this IP with your current local network IP
     * - Run: ipconfig | Select-String "IPv4" to find your IP
     */
    private const val BASE_URL = "http://10.69.68.243/spinocare-api/"
    
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
