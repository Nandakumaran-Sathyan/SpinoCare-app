-- Clean up all unverified users before implementing strict OTP verification
-- Run this in phpMyAdmin or MySQL Workbench

USE spinocare_db;

-- Show unverified users before deletion
SELECT id, email, display_name, email_verified, created_at 
FROM users 
WHERE email_verified = FALSE OR email_verified = 0;

-- Delete all unverified users
DELETE FROM users 
WHERE email_verified = FALSE OR email_verified = 0;

-- Verify deletion
SELECT COUNT(*) as remaining_users FROM users;
SELECT id, email, display_name, email_verified FROM users;
