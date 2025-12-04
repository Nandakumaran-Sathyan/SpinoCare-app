-- Add email_verified column to users table
-- Run this after the initial schema setup

USE spinocare_db;

ALTER TABLE users 
ADD COLUMN email_verified BOOLEAN DEFAULT FALSE AFTER is_active;

-- Create index for faster lookups
CREATE INDEX idx_email_verified ON users(email_verified);
