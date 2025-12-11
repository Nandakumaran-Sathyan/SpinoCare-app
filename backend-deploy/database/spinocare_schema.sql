-- SpinoCare Database Schema
-- Created: November 12, 2025
-- Purpose: Migrate from Firestore to MySQL

-- ============================================
-- Create Database
-- ============================================
CREATE DATABASE IF NOT EXISTS spinocare_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE spinocare_db;

-- ============================================
-- Users Table
-- ============================================
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    firebase_uid VARCHAR(128) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_firebase_uid (firebase_uid),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- MRI Analysis Entries Table
-- ============================================
CREATE TABLE mri_analysis_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    entry_id VARCHAR(50) UNIQUE NOT NULL,
    user_id INT NOT NULL,
    
    -- Image URLs from Firebase Storage
    t1_image_url TEXT NOT NULL,
    t2_image_url TEXT NOT NULL,
    
    -- Analysis Results
    analysis_result VARCHAR(50) NOT NULL,
    confidence DECIMAL(5, 4) NOT NULL,
    
    -- Metadata
    analysis_mode VARCHAR(20) DEFAULT 'online',
    model_version VARCHAR(20) DEFAULT 'v1.0',
    processing_time_ms INT,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_entry_id (entry_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Analysis Statistics Table (Optional)
-- ============================================
CREATE TABLE analysis_statistics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    total_analyses INT DEFAULT 0,
    modic_detected_count INT DEFAULT 0,
    no_modic_count INT DEFAULT 0,
    average_confidence DECIMAL(5, 4),
    last_analysis_date TIMESTAMP NULL,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_stats (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Sample Data for Testing
-- ============================================

-- Insert sample user (you'll replace this with real Firebase users)
INSERT INTO users (firebase_uid, email, display_name) VALUES
('test_firebase_uid_123', 'test@example.com', 'Test User');

-- Insert sample analysis entry
INSERT INTO mri_analysis_entries (
    entry_id, 
    user_id, 
    t1_image_url, 
    t2_image_url, 
    analysis_result, 
    confidence,
    analysis_mode,
    model_version,
    processing_time_ms
) VALUES (
    'sample_entry_001',
    1,
    'https://firebasestorage.googleapis.com/v0/b/modic-d0149.firebasestorage.app/o/users%2Ftest_firebase_uid_123%2Fimages%2Ft1_sample.jpg',
    'https://firebasestorage.googleapis.com/v0/b/modic-d0149.firebasestorage.app/o/users%2Ftest_firebase_uid_123%2Fimages%2Ft2_sample.jpg',
    'No Modic Changes',
    0.9970,
    'offline',
    'v1.0',
    1245
);

-- ============================================
-- Useful Queries for Development
-- ============================================

-- Get all analyses for a user by Firebase UID
-- SELECT mae.* FROM mri_analysis_entries mae
-- JOIN users u ON mae.user_id = u.id
-- WHERE u.firebase_uid = 'YOUR_FIREBASE_UID'
-- ORDER BY mae.created_at DESC;

-- Get user statistics
-- SELECT 
--     u.email,
--     COUNT(mae.id) as total_analyses,
--     AVG(mae.confidence) as avg_confidence,
--     SUM(CASE WHEN mae.analysis_result = 'Modic Change Detected' THEN 1 ELSE 0 END) as modic_count,
--     MAX(mae.created_at) as last_analysis
-- FROM users u
-- LEFT JOIN mri_analysis_entries mae ON u.id = mae.user_id
-- WHERE u.firebase_uid = 'YOUR_FIREBASE_UID'
-- GROUP BY u.id;
