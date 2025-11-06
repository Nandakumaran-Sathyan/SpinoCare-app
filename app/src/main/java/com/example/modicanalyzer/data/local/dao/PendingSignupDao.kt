package com.example.modicanalyzer.data.local.dao

import androidx.room.*
import com.example.modicanalyzer.data.local.entity.PendingSignupEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing pending signup queue
 */
@Dao
interface PendingSignupDao {
    
    @Insert
    suspend fun insertPendingSignup(signup: PendingSignupEntity): Long
    
    @Query("SELECT * FROM pending_signups WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSignups(): List<PendingSignupEntity>
    
    @Query("SELECT * FROM pending_signups ORDER BY createdAt DESC")
    fun getAllSignupsFlow(): Flow<List<PendingSignupEntity>>
    
    @Query("SELECT * FROM pending_signups WHERE id = :id")
    suspend fun getSignupById(id: Long): PendingSignupEntity?
    
    @Update
    suspend fun updateSignup(signup: PendingSignupEntity)
    
    @Delete
    suspend fun deleteSignup(signup: PendingSignupEntity)
    
    @Query("DELETE FROM pending_signups WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedSignups()
    
    @Query("SELECT COUNT(*) FROM pending_signups WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int
    
    @Query("UPDATE pending_signups SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markAsFailed(id: Long, error: String)
    
    @Query("UPDATE pending_signups SET status = 'COMPLETED' WHERE id = :id")
    suspend fun markAsCompleted(id: Long)
}
