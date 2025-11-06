package com.example.modicanalyzer.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modicanalyzer.data.remote.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing user profile data from Firestore
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val firestoreHelper: FirestoreHelper,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {
    
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    companion object {
        private const val TAG = "UserProfileViewModel"
    }
    
    init {
        loadUserProfile()
    }
    
    /**
     * Load user profile from Firestore
     */
    fun loadUserProfile() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "No user logged in, cannot load profile")
            _error.value = "Not logged in"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val result = firestoreHelper.getUserProfile(userId)
                
                result.onSuccess { profileData ->
                    if (profileData != null) {
                        _userProfile.value = UserProfile(
                            userId = userId,
                            name = profileData["name"] as? String ?: "",
                            email = profileData["email"] as? String ?: "",
                            role = profileData["role"] as? String ?: "",
                            profileImageUrl = profileData["profileImage"] as? String,
                            createdAt = profileData["createdAt"],
                            updatedAt = profileData["updatedAt"]
                        )
                        Log.d(TAG, "✅ User profile loaded: ${_userProfile.value?.name}")
                    } else {
                        _error.value = "Profile not found"
                        Log.w(TAG, "Profile not found for user: $userId")
                    }
                }.onFailure { exception ->
                    _error.value = exception.message
                    Log.e(TAG, "❌ Failed to load profile", exception)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update user profile
     */
    fun updateProfile(
        name: String? = null,
        role: String? = null,
        profileImageUrl: String? = null
    ) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val currentEmail = firebaseAuth.currentUser?.email ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val result = firestoreHelper.createOrUpdateUserProfile(
                    userId = userId,
                    name = name ?: _userProfile.value?.name ?: "",
                    email = currentEmail,
                    role = role ?: _userProfile.value?.role,
                    profileImageUrl = profileImageUrl ?: _userProfile.value?.profileImageUrl
                )
                
                result.onSuccess {
                    loadUserProfile() // Reload to get updated data
                    Log.d(TAG, "✅ Profile updated successfully")
                }.onFailure { exception ->
                    _error.value = exception.message
                    Log.e(TAG, "❌ Failed to update profile", exception)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update profile image URL
     */
    fun updateProfileImage(imageUrl: String) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val result = firestoreHelper.updateProfileImage(userId, imageUrl)
                
                result.onSuccess {
                    loadUserProfile() // Reload to get updated data
                    Log.d(TAG, "✅ Profile image updated")
                }.onFailure { exception ->
                    _error.value = exception.message
                    Log.e(TAG, "❌ Failed to update profile image", exception)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get user's display name
     */
    fun getUserName(): String {
        return _userProfile.value?.name 
            ?: firebaseAuth.currentUser?.displayName 
            ?: firebaseAuth.currentUser?.email?.substringBefore('@')
            ?: "User"
    }
    
    /**
     * Get user's email
     */
    fun getUserEmail(): String {
        return _userProfile.value?.email 
            ?: firebaseAuth.currentUser?.email 
            ?: "Unknown"
    }
    
    /**
     * Get user's role
     */
    fun getUserRole(): String {
        return _userProfile.value?.role ?: "patient"
    }
}

/**
 * Data class representing a user profile
 */
data class UserProfile(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val profileImageUrl: String? = null,
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
