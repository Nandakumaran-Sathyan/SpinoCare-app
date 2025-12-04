<?php
/**
 * SpinoCare Email Service
 * Handles OTP and email notifications using Gmail SMTP
 * Created: November 29, 2025
 */

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

require_once __DIR__ . '/PHPMailer-master/src/Exception.php';
require_once __DIR__ . '/PHPMailer-master/src/PHPMailer.php';
require_once __DIR__ . '/PHPMailer-master/src/SMTP.php';
require_once 'config.php';

/**
 * Generate a 6-digit OTP
 */
function generateOTP() {
    return rand(100000, 999999);
}

/**
 * Send OTP via email
 * @param string $email Recipient email
 * @param int $otp 6-digit OTP
 * @return array Result with success status and message
 */
function sendOTPEmail($email, $otp) {
    $mail = new PHPMailer(true);
    
    try {
        // SMTP Configuration
        $mail->isSMTP();
        $mail->Host = SMTP_HOST;
        $mail->SMTPAuth = true;
        $mail->Username = SMTP_USERNAME;
        $mail->Password = SMTP_PASSWORD;
        $mail->SMTPSecure = SMTP_SECURE;
        $mail->Port = SMTP_PORT;
        
        // Sender & Recipient
        $mail->setFrom(SMTP_USERNAME, 'SpinoCare');
        $mail->addAddress($email);
        
        // Email Content
        $mail->isHTML(true);
        $mail->Subject = 'Your SpinoCare Verification Code';
        $mail->Body = '
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: #667eea;">SpinoCare Verification</h2>
                <p>Your verification code is:</p>
                <div style="background: #f3f4f6; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                    <h1 style="color: #667eea; font-size: 36px; margin: 0; letter-spacing: 5px;">' . $otp . '</h1>
                </div>
                <p style="color: #666;">This code will expire in 10 minutes.</p>
                <p style="color: #666;">If you did not request this code, please ignore this email.</p>
                <hr style="border: none; border-top: 1px solid #e0e0e0; margin: 20px 0;">
                <p style="color: #999; font-size: 12px;">SpinoCare - MRI Analysis Platform</p>
            </div>
        ';
        $mail->AltBody = "Your SpinoCare verification code is: $otp\n\nThis code will expire in 10 minutes.";
        
        $mail->send();
        return [
            'success' => true,
            'message' => 'OTP sent successfully'
        ];
        
    } catch (Exception $e) {
        error_log("Email Error: " . $mail->ErrorInfo);
        return [
            'success' => false,
            'message' => 'Failed to send email',
            'error' => $mail->ErrorInfo
        ];
    }
}

/**
 * Send welcome email
 */
function sendWelcomeEmail($email, $displayName) {
    $mail = new PHPMailer(true);
    
    try {
        $mail->isSMTP();
        $mail->Host = SMTP_HOST;
        $mail->SMTPAuth = true;
        $mail->Username = SMTP_USERNAME;
        $mail->Password = SMTP_PASSWORD;
        $mail->SMTPSecure = SMTP_SECURE;
        $mail->Port = SMTP_PORT;
        
        $mail->setFrom(SMTP_USERNAME, 'SpinoCare');
        $mail->addAddress($email);
        
        $mail->isHTML(true);
        $mail->Subject = 'Welcome to SpinoCare!';
        $mail->Body = '
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: #667eea;">Welcome to SpinoCare, ' . htmlspecialchars($displayName) . '! 🎉</h2>
                <p>Your account has been created successfully.</p>
                <p>You can now start using SpinoCare to analyze MRI images and track your spine health.</p>
                <hr style="border: none; border-top: 1px solid #e0e0e0; margin: 20px 0;">
                <p style="color: #999; font-size: 12px;">SpinoCare - MRI Analysis Platform</p>
            </div>
        ';
        
        $mail->send();
        return ['success' => true];
        
    } catch (Exception $e) {
        error_log("Email Error: " . $mail->ErrorInfo);
        return ['success' => false];
    }
}

/**
 * Handle OTP sending endpoint
 */
function handleSendOTP() {
    global $pdo;
    
    $data = getRequestBody();
    
    // Validate email
    if (!isset($data['email']) || empty($data['email'])) {
        sendResponse(false, null, 'Email is required', 400);
    }
    
    $email = filter_var($data['email'], FILTER_VALIDATE_EMAIL);
    if (!$email) {
        sendResponse(false, null, 'Invalid email format', 400);
    }
    
    // Generate OTP
    $otp = generateOTP();
    
    // Store OTP in database (create otps table if needed)
    try {
        $pdo = getDBConnection();
        
        // Calculate expiration using MySQL's NOW() + 10 minutes to avoid timezone issues
        $expiresAt = null; // Will be calculated in SQL
        
        // Create otps table if it doesn't exist
        $pdo->exec("
            CREATE TABLE IF NOT EXISTS otps (
                id INT AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(255) NOT NULL,
                otp VARCHAR(6) NOT NULL,
                expires_at DATETIME NOT NULL,
                verified BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_email (email),
                INDEX idx_expires (expires_at)
            )
        ");
        
        // Delete old OTPs for this email
        $stmt = $pdo->prepare("DELETE FROM otps WHERE email = ? OR expires_at < NOW()");
        $stmt->execute([$email]);
        
        // Insert new OTP with expiration calculated using MySQL NOW()
        $stmt = $pdo->prepare("INSERT INTO otps (email, otp, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE))");
        $stmt->execute([$email, $otp]);
        
        // Send email
        $result = sendOTPEmail($email, $otp);
        
        if ($result['success']) {
            sendResponse(true, [
                'message' => 'OTP sent to your email',
                'email' => $email,
                'expires_in_minutes' => 10
            ]);
        } else {
            sendResponse(false, null, $result['message'], 500);
        }
        
    } catch (Exception $e) {
        error_log("OTP Error: " . $e->getMessage());
        sendResponse(false, null, 'Failed to process OTP request', 500);
    }
}

/**
 * Handle OTP verification endpoint
 */
function handleVerifyOTP() {
    global $pdo;
    
    $data = getRequestBody();
    
    // Validate input
    if (!isset($data['email']) || !isset($data['otp'])) {
        sendResponse(false, null, 'Email and OTP are required', 400);
    }
    
    $email = filter_var($data['email'], FILTER_VALIDATE_EMAIL);
    $otp = $data['otp'];
    
    if (!$email) {
        sendResponse(false, null, 'Invalid email format', 400);
    }
    
    try {
        $pdo = getDBConnection();
        
        // Check OTP
        $stmt = $pdo->prepare("
            SELECT * FROM otps 
            WHERE email = ? AND otp = ? AND expires_at > NOW() AND verified = FALSE
            ORDER BY created_at DESC 
            LIMIT 1
        ");
        $stmt->execute([$email, $otp]);
        $otpRecord = $stmt->fetch();
        
        if (!$otpRecord) {
            sendResponse(false, null, 'Invalid or expired OTP', 400);
        }
        
        // Mark OTP as verified
        $stmt = $pdo->prepare("UPDATE otps SET verified = TRUE WHERE id = ?");
        $stmt->execute([$otpRecord['id']]);
        
        sendResponse(true, [
            'message' => 'OTP verified successfully',
            'email' => $email
        ]);
        
    } catch (Exception $e) {
        error_log("Verify OTP Error: " . $e->getMessage());
        sendResponse(false, null, 'Failed to verify OTP', 500);
    }
}
?>
