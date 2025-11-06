package com.example.modicanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.modicanalyzer.data.model.SyncStatus

/**
 * Room entity for tracking pending image uploads.
 * 
 * When the user performs analysis offline or upload fails:
 * - Images are saved to local storage
 * - This entity tracks the upload job
 * - ImageUploadWorker processes the queue when online
 * 
 * @property id Unique upload job ID
 * @property userId User's Firebase UID (or local UUID for offline users)
 * @property analysisId Associated analysis entry ID (links to Firestore doc)
 * @property t1ImagePath Local file path to T1 image
 * @property t2ImagePath Local file path to T2 image
 * @property t1UploadUrl Uploaded T1 URL (null until uploaded)
 * @property t2UploadUrl Uploaded T2 URL (null until uploaded)
 * @property syncStatus Upload status (PENDING, SYNCING, SYNCED, FAILED)
 * @property retryCount Number of upload retry attempts
 * @property createdAt Timestamp when queued
 * @property uploadedAt Timestamp when successfully uploaded
 * @property errorMessage Last error message (if failed)
 */
@Entity(tableName = "pending_image_uploads")
data class PendingImageUploadEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val analysisId: String,
    val t1ImagePath: String,
    val t2ImagePath: String,
    val t1UploadUrl: String? = null,
    val t2UploadUrl: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null,
    val errorMessage: String? = null
)
