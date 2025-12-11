<?php
/**
 * Firebase Custom Token Generator
 * Generates custom Firebase tokens for MySQL-authenticated users
 */

require_once 'config.php';

/**
 * Generate Firebase custom token using JWT
 * 
 * @param string $uid User's unique ID from MySQL
 * @param array $claims Additional claims (optional)
 * @return string Custom token
 */
function generateFirebaseCustomToken($uid, $claims = []) {
    // Get your Firebase service account credentials
    // Download from Firebase Console > Project Settings > Service Accounts
    $serviceAccountPath = __DIR__ . '/firebase-service-account.json';
    
    if (!file_exists($serviceAccountPath)) {
        throw new Exception('Firebase service account file not found. Please download from Firebase Console.');
    }
    
    $serviceAccount = json_decode(file_get_contents($serviceAccountPath), true);
    
    if (!$serviceAccount) {
        throw new Exception('Invalid service account JSON');
    }
    
    // JWT Header
    $header = [
        'alg' => 'RS256',
        'typ' => 'JWT'
    ];
    
    // JWT Payload
    $now = time();
    $payload = [
        'iss' => $serviceAccount['client_email'],
        'sub' => $serviceAccount['client_email'],
        'aud' => 'https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit',
        'iat' => $now,
        'exp' => $now + 3600, // Token expires in 1 hour
        'uid' => $uid,
        'claims' => $claims
    ];
    
    // Encode header and payload
    $base64UrlHeader = base64UrlEncode(json_encode($header));
    $base64UrlPayload = base64UrlEncode(json_encode($payload));
    
    // Create signature
    $signature = '';
    $signatureInput = $base64UrlHeader . '.' . $base64UrlPayload;
    
    openssl_sign(
        $signatureInput,
        $signature,
        $serviceAccount['private_key'],
        OPENSSL_ALGO_SHA256
    );
    
    $base64UrlSignature = base64UrlEncode($signature);
    
    // Create JWT token
    $customToken = $signatureInput . '.' . $base64UrlSignature;
    
    return $customToken;
}

/**
 * Base64 URL encode
 */
function base64UrlEncode($data) {
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

/**
 * Handle Firebase token request
 */
function handleFirebaseTokenRequest($pdo) {
    // Get Authorization header
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    
    if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        sendResponse(false, null, 'Missing or invalid authorization token', 401);
        return;
    }
    
    $token = $matches[1];
    
    // Verify the MySQL auth token
    $tokenParts = explode('.', $token);
    if (count($tokenParts) !== 3) {
        sendResponse(false, null, 'Invalid token format', 401);
        return;
    }
    
    $payload = json_decode(base64_decode($tokenParts[1]), true);
    $userId = $payload['user_id'] ?? null;
    $uid = $payload['uid'] ?? null;
    
    if (!$userId || !$uid) {
        sendResponse(false, null, 'Invalid token payload', 401);
        return;
    }
    
    // Verify token signature (simplified - you should use the same secret as in auth.php)
    $expectedSignature = hash_hmac('sha256', $tokenParts[0] . '.' . $tokenParts[1], 'your_secret_key_here');
    if ($expectedSignature !== $tokenParts[2]) {
        sendResponse(false, null, 'Invalid token signature', 401);
        return;
    }
    
    // Check if token is expired
    $exp = $payload['exp'] ?? 0;
    if ($exp < time()) {
        sendResponse(false, null, 'Token expired', 401);
        return;
    }
    
    try {
        // Generate Firebase custom token
        $firebaseToken = generateFirebaseCustomToken($uid, [
            'email' => $payload['email'] ?? '',
            'provider' => 'mysql'
        ]);
        
        sendResponse(true, [
            'firebase_token' => $firebaseToken,
            'expires_in' => 3600
        ], 'Firebase token generated successfully', 200);
        
    } catch (Exception $e) {
        sendResponse(false, null, 'Failed to generate Firebase token: ' . $e->getMessage(), 500);
    }
}
