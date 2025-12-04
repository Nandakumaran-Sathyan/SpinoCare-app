<?php
/**
 * SpinoCare REST API - Main Entry Point
 * Created: November 12, 2025
 * 
 * Endpoints:
 * - POST /api/users/register - Register new user
 * - GET /api/users/{firebase_uid} - Get user details
 * - POST /api/analysis/save - Save MRI analysis
 * - GET /api/analysis/list/{firebase_uid} - Get user's analyses
 * - GET /api/analysis/{entry_id} - Get specific analysis
 * - DELETE /api/analysis/{entry_id} - Delete analysis
 */

require_once 'config.php';

setJSONHeaders();

// Get request method and path
$method = $_SERVER['REQUEST_METHOD'];

// Check if path is provided as query parameter (for testing)
if (isset($_GET['path'])) {
    $path = trim($_GET['path'], '/');
} else {
    $requestUri = $_SERVER['REQUEST_URI'];
    $scriptName = $_SERVER['SCRIPT_NAME'];
    // Remove the script name from URI to get the path
    $path = str_replace(dirname($scriptName), '', parse_url($requestUri, PHP_URL_PATH));
    $path = trim($path, '/');
}

$segments = explode('/', $path);

// Route requests
if ($method === 'OPTIONS') {
    // Handle preflight CORS requests
    http_response_code(200);
    exit;
}

try {
    // OTP endpoints
    if (isset($segments[0]) && $segments[0] === 'otp') {
        require_once 'email.php';
        if (isset($segments[1]) && $segments[1] === 'send' && $method === 'POST') {
            handleSendOTP();
        } else if (isset($segments[1]) && $segments[1] === 'verify' && $method === 'POST') {
            handleVerifyOTP();
        } else {
            sendResponse(false, null, 'Invalid OTP endpoint', 404);
        }
    }
    // Authentication endpoints
    else if (isset($segments[0]) && $segments[0] === 'auth') {
        require_once 'auth.php';
        handleAuthRequest($method, $segments);
    }
    // Firebase token endpoint
    else if (isset($segments[0]) && $segments[0] === 'firebase' && isset($segments[1]) && $segments[1] === 'token') {
        require_once 'firebase_auth.php';
        handleFirebaseTokenRequest($pdo);
    }
    // User endpoints
    else if (isset($segments[0]) && $segments[0] === 'users') {
        require_once 'users.php';
        handleUserRequest($method, $segments);
    }
    // Analysis endpoints
    else if (isset($segments[0]) && $segments[0] === 'analysis') {
        require_once 'analysis.php';
        handleAnalysisRequest($method, $segments);
    }
    // Health check
    else if ($method === 'GET' && (empty($path) || $path === 'index.php')) {
        sendResponse(true, [
            'message' => 'SpinoCare API is running',
            'version' => '2.0',
            'endpoints' => [
                '=== REGISTRATION FLOW ===',
                'POST /spinocare-api/auth/register - Step 1: Register & send OTP',
                'POST /spinocare-api/auth/verify-email - Step 2: Verify OTP & complete registration',
                '=== AUTHENTICATION ===',
                'POST /spinocare-api/auth/login - Login with email and password',
                '=== OTP (Standalone) ===',
                'POST /spinocare-api/otp/send - Send OTP to email',
                'POST /spinocare-api/otp/verify - Verify OTP code',
                '=== OTHER ===',
                'GET /spinocare-api/firebase/token - Get Firebase custom token (requires auth)',
                'POST /spinocare-api/users/register - Legacy: Register user (no password)',
                'GET /spinocare-api/users/{uid} - Get user details',
                'POST /spinocare-api/analysis/save - Save MRI analysis',
                'GET /spinocare-api/analysis/list/{uid} - Get user\'s analyses',
                'GET /spinocare-api/analysis/{entry_id} - Get specific analysis',
                'DELETE /spinocare-api/analysis/{entry_id} - Delete analysis'
            ]
        ]);
    }
    else {
        sendResponse(false, null, 'Endpoint not found', 404);
    }
} catch (Exception $e) {
    sendResponse(false, null, 'Server error: ' . $e->getMessage(), 500);
}
?>
