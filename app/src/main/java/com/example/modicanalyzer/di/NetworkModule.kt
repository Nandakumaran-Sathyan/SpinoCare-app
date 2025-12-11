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
     * 
     * DEVELOPMENT (Local XAMPP Testing):
     * - Current: "http://192.168.29.203/spinocare-api/" (your laptop's WiFi IP)
     * - Make sure your phone and laptop are on the SAME WiFi network
     * - If connection fails, run: ipconfig to find current WiFi IP
     * - Backend files located in: backend-deploy/php-api/ (copy to C:\xampp-2\htdocs\spinocare-api\)
     * 
     * PRODUCTION (After Deployment):
     * - Change to: "https://yourdomain.com/spinocare-api/"
     * - Follow deployment guide: backend-deploy/README.md
     * - Update this URL after deploying backend
     */
    private const val BASE_URL = "http://192.168.29.203/spinocare-api/"
    
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
