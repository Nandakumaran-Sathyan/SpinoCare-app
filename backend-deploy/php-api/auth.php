<?php
/**
 * SpinoCare REST API - Authentication
 * Created: November 30, 2025
 * Handles registration with OTP verification and login
 */

require_once 'config.php';
require_once 'email.php';

function handleAuthRequest($method, $segments) {
    $pdo = getDBConnection();
    
    // POST /auth/register - Register new user with password
    if ($method === 'POST' && isset($segments[1]) && $segments[1] === 'register') {
        registerUserWithPassword($pdo);
    }
    // POST /auth/login - Login with email and password
    else if ($method === 'POST' && isset($segments[1]) && $segments[1] === 'login') {
        loginUser($pdo);
    }
    // POST /auth/verify-email - Complete registration after OTP verification
    else if ($method === 'POST' && isset($segments[1]) && $segments[1] === 'verify-email') {
        verifyEmailAndCompleteRegistration($pdo);
    }
    else {
        sendResponse(false, null, 'Invalid auth endpoint', 404);
    }
}

/**
 * Step 1: Validate registration data and send OTP (DO NOT create user yet)
 * POST /auth/register
 * Body: { email, password, display_name?, phone_number? }
 */
function registerUserWithPassword($pdo) {
    $data = getRequestBody();
    
    // Validate required fields
    $missing = validateFields($data, ['email', 'password']);
    if (!empty($missing)) {
        sendResponse(false, null, 'Missing fields: ' . implode(', ', $missing), 400);
    }
    
    $email = filter_var($data['email'], FILTER_VALIDATE_EMAIL);
    if (!$email) {
        sendResponse(false, null, 'Invalid email format', 400);
    }
    
    $password = $data['password'];
    $display_name = isset($data['display_name']) ? $data['display_name'] : null;
    $phone_number = isset($data['phone_number']) ? $data['phone_number'] : null;
    
    // Validate password strength
    if (strlen($password) < 6) {
        sendResponse(false, null, 'Password must be at least 6 characters', 400);
    }
    
    try {
        // Check if email already exists in verified users
        $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ?");
        $stmt->execute([$email]);
        $existingUser = $stmt->fetch();
        
        if ($existingUser) {
            sendResponse(false, null, 'Email already registered. Please login.', 400);
        }
        
        // Hash password
        $password_hash = password_hash($password, PASSWORD_BCRYPT);
        
        // Generate OTP
        $otp = generateOTP();
        
        // Store OTP with registration data (temporary storage)
        $stmt = $pdo->prepare("DELETE FROM otps WHERE email = ?");
        $stmt->execute([$email]);
        
        // Store registration data in OTP table temporarily
        $registrationData = json_encode([
            'email' => $email,
            'password_hash' => $password_hash,
            'display_name' => $display_name,
            'phone_number' => $phone_number
        ]);
        
        $stmt = $pdo->prepare("
            INSERT INTO otps (email, otp, expires_at, verified, created_at) 
            VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), FALSE, NOW())
        ");
        $stmt->execute([$email, $otp]);
        
        // Temporarily store registration data in a session-like way using email as key
        // We'll retrieve it during verification
        $stmt = $pdo->prepare("
            UPDATE otps SET verified = 0 WHERE email = ? AND otp = ?
        ");
        $stmt->execute([$email, $otp]);
        
        // Store registration data in a comment or separate temp table
        // For now, we'll use a simple approach: store it in session or pass it back
        
        // Send OTP email
        $emailResult = sendOTPEmail($email, $otp);
        
        if (!$emailResult['success']) {
            sendResponse(false, null, 'Failed to send verification email: ' . $emailResult['error'], 500);
        }
        
        sendResponse(true, [
            'email' => $email,
            'message' => 'Verification code sent to your email. Please verify to complete registration.',
            'otp_expires_in_minutes' => 10,
            // Pass registration data to be sent back during verification
            'registration_data' => [
                'email' => $email,
                'display_name' => $display_name,
                'phone_number' => $phone_number,
                'password_hash' => $password_hash
            ]
        ], null, 200);
        
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Step 2: Verify OTP and CREATE user in database
 * POST /auth/verify-email
 * Body: { email, otp, password_hash, display_name?, phone_number? }
 */
function verifyEmailAndCompleteRegistration($pdo) {
    $data = getRequestBody();
    
    // Validate required fields
    $missing = validateFields($data, ['email', 'otp', 'password_hash']);
    if (!empty($missing)) {
        sendResponse(false, null, 'Missing fields: ' . implode(', ', $missing), 400);
    }
    
    $email = filter_var($data['email'], FILTER_VALIDATE_EMAIL);
    $otp = $data['otp'];
    $password_hash = $data['password_hash'];
    $display_name = isset($data['display_name']) ? $data['display_name'] : null;
    $phone_number = isset($data['phone_number']) ? $data['phone_number'] : null;
    
    if (!$email) {
        sendResponse(false, null, 'Invalid email format', 400);
    }
    
    try {
        // Verify OTP
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
        
        // Check if user already exists (shouldn't happen, but safety check)
        $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ?");
        $stmt->execute([$email]);
        if ($stmt->fetch()) {
            sendResponse(false, null, 'User already exists', 400);
        }
        
        // Mark OTP as verified
        $stmt = $pdo->prepare("UPDATE otps SET verified = TRUE WHERE id = ?");
        $stmt->execute([$otpRecord['id']]);
        
        // Generate proper firebase_uid
        $firebase_uid = 'user_' . md5($email . time());
        
        // NOW create the user in database (only after OTP verification)
        $stmt = $pdo->prepare("
            INSERT INTO users (firebase_uid, email, password_hash, display_name, phone_number, email_verified, is_active, last_login, created_at)
            VALUES (?, ?, ?, ?, ?, TRUE, TRUE, NOW(), NOW())
        ");
        $stmt->execute([$firebase_uid, $email, $password_hash, $display_name, $phone_number]);
        
        $user_id = $pdo->lastInsertId();
        
        // Get complete user info
        $userInfo = [
            'id' => $user_id,
            'firebase_uid' => $firebase_uid,
            'email' => $email,
            'display_name' => $display_name,
            'phone_number' => $phone_number
        ];
        
        // Generate auth token
        $token = generateToken($userInfo);
        
        // Send welcome email
        if ($display_name) {
            sendWelcomeEmail($email, $display_name);
        }
        
        sendResponse(true, [
            'message' => 'Email verified successfully. Registration complete!',
            'token' => $token,
            'user_id' => $user_id,
            'uid' => $firebase_uid,
            'email' => $email,
            'display_name' => $display_name,
            'phone_number' => $phone_number
        ], null, 201);
        
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Login with email and password
 * POST /auth/login
 * Body: { email, password }
 */
function loginUser($pdo) {
    $data = getRequestBody();
    
    // Validate required fields
    $missing = validateFields($data, ['email', 'password']);
    if (!empty($missing)) {
        sendResponse(false, null, 'Missing fields: ' . implode(', ', $missing), 400);
    }
    
    $email = filter_var($data['email'], FILTER_VALIDATE_EMAIL);
    $password = $data['password'];
    
    if (!$email) {
        sendResponse(false, null, 'Invalid email format', 400);
    }
    
    try {
        // Get user by email
        $stmt = $pdo->prepare("
            SELECT id, firebase_uid, email, password_hash, display_name, phone_number, email_verified, is_active
            FROM users 
            WHERE email = ?
        ");
        $stmt->execute([$email]);
        $user = $stmt->fetch();
        
        if (!$user) {
            sendResponse(false, null, 'Invalid email or password', 401);
        }
        
        // All users in DB are verified (we only create after OTP verification)
        // But keep this check for safety
        if (!$user['email_verified']) {
            sendResponse(false, null, 'Email not verified. Please complete registration.', 403);
        }
        
        // Check if account is active
        if (!$user['is_active']) {
            sendResponse(false, null, 'Account is deactivated. Contact support.', 403);
        }
        
        // Verify password
        if (!password_verify($password, $user['password_hash'])) {
            sendResponse(false, null, 'Invalid email or password', 401);
        }
        
        // Update last login
        $stmt = $pdo->prepare("UPDATE users SET last_login = NOW() WHERE id = ?");
        $stmt->execute([$user['id']]);
        
        // Remove password_hash from response
        unset($user['password_hash']);
        unset($user['is_active']);
        
        // Generate auth token
        $token = generateToken($user);
        
        sendResponse(true, [
            'token' => $token,
            'user' => $user
        ]);
        
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Generate JWT-like token
 */
function generateToken($user) {
    $header = base64_encode(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
    $payload = base64_encode(json_encode([
        'user_id' => $user['id'],
        'firebase_uid' => $user['firebase_uid'],
        'email' => $user['email'],
        'exp' => time() + (86400 * 30) // 30 days
    ]));
    
    $signature = hash_hmac('sha256', "$header.$payload", 'spinocare_secret_key_change_this', true);
    $signature = base64_encode($signature);
    
    return "$header.$payload.$signature";
}

/**
 * Verify token
 */
function verifyToken($token) {
    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        return false;
    }
    
    list($header, $payload, $signature) = $parts;
    
    $expectedSignature = base64_encode(hash_hmac('sha256', "$header.$payload", 'spinocare_secret_key_change_this', true));
    
    if ($signature !== $expectedSignature) {
        return false;
    }
    
    $payloadData = json_decode(base64_decode($payload), true);
    
    if (!$payloadData || !isset($payloadData['exp']) || $payloadData['exp'] < time()) {
        return false;
    }
    
    return $payloadData;
}
?>
