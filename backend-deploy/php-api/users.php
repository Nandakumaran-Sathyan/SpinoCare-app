<?php
/**
 * SpinoCare REST API - User Management
 * Created: November 12, 2025
 */

require_once 'config.php';

function handleUserRequest($method, $segments) {
    $pdo = getDBConnection();
    
    // POST /users/register
    if ($method === 'POST' && isset($segments[1]) && $segments[1] === 'register') {
        registerUser($pdo);
    }
    // GET /users/{firebase_uid}
    else if ($method === 'GET' && isset($segments[1])) {
        getUserByFirebaseUID($pdo, $segments[1]);
    }
    else {
        sendResponse(false, null, 'Invalid user endpoint', 404);
    }
}

/**
 * Register or update user
 * POST /api/users/register
 * Body: { firebase_uid, email, display_name?, phone_number? }
 */
function registerUser($pdo) {
    $data = getRequestBody();
    
    // Validate required fields
    $missing = validateFields($data, ['firebase_uid', 'email']);
    if (!empty($missing)) {
        sendResponse(false, null, 'Missing fields: ' . implode(', ', $missing), 400);
    }
    
    $firebase_uid = $data['firebase_uid'];
    $email = $data['email'];
    $display_name = isset($data['display_name']) ? $data['display_name'] : null;
    $phone_number = isset($data['phone_number']) ? $data['phone_number'] : null;
    
    try {
        // Check if user already exists
        $stmt = $pdo->prepare("SELECT id FROM users WHERE firebase_uid = ?");
        $stmt->execute([$firebase_uid]);
        $existingUser = $stmt->fetch();
        
        if ($existingUser) {
            // Update existing user
            $stmt = $pdo->prepare("
                UPDATE users 
                SET email = ?, display_name = ?, phone_number = ?, last_login = NOW()
                WHERE firebase_uid = ?
            ");
            $stmt->execute([$email, $display_name, $phone_number, $firebase_uid]);
            
            sendResponse(true, [
                'user_id' => $existingUser['id'],
                'message' => 'User updated successfully'
            ]);
        } else {
            // Insert new user
            $stmt = $pdo->prepare("
                INSERT INTO users (firebase_uid, email, display_name, phone_number, last_login)
                VALUES (?, ?, ?, ?, NOW())
            ");
            $stmt->execute([$firebase_uid, $email, $display_name, $phone_number]);
            
            sendResponse(true, [
                'user_id' => $pdo->lastInsertId(),
                'message' => 'User registered successfully'
            ], null, 201);
        }
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Get user by Firebase UID
 * GET /api/users/{firebase_uid}
 */
function getUserByFirebaseUID($pdo, $firebase_uid) {
    try {
        $stmt = $pdo->prepare("
            SELECT id, firebase_uid, email, display_name, phone_number, 
                   created_at, last_login, is_active
            FROM users 
            WHERE firebase_uid = ?
        ");
        $stmt->execute([$firebase_uid]);
        $user = $stmt->fetch();
        
        if ($user) {
            sendResponse(true, $user);
        } else {
            sendResponse(false, null, 'User not found', 404);
        }
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}
?>
