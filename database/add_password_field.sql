-- Add password authentication support to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) NULL AFTER phone_number;

-- Add index for faster lookups
CREATE INDEX idx_email ON users(email);
