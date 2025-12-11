<?php
/**
 * SpinoCare REST API - MRI Analysis Management
 * Created: November 12, 2025
 */

require_once 'config.php';

function handleAnalysisRequest($method, $segments) {
    $pdo = getDBConnection();
    
    // POST /analysis/save
    if ($method === 'POST' && isset($segments[1]) && $segments[1] === 'save') {
        saveAnalysis($pdo);
    }
    // GET /analysis/list/{firebase_uid}
    else if ($method === 'GET' && isset($segments[1]) && $segments[1] === 'list' && isset($segments[2])) {
        getAnalysisList($pdo, $segments[2]);
    }
    // GET /analysis/{entry_id}
    else if ($method === 'GET' && isset($segments[1])) {
        getAnalysisById($pdo, $segments[1]);
    }
    // DELETE /analysis/{entry_id}
    else if ($method === 'DELETE' && isset($segments[1])) {
        deleteAnalysis($pdo, $segments[1]);
    }
    else {
        sendResponse(false, null, 'Invalid analysis endpoint', 404);
    }
}

/**
 * Save MRI analysis entry
 * POST /api/analysis/save
 * Body: {
 *   firebase_uid,
 *   entry_id,
 *   t1_image_url,
 *   t2_image_url,
 *   analysis_result,
 *   confidence,
 *   analysis_mode?,
 *   model_version?,
 *   processing_time_ms?
 * }
 */
function saveAnalysis($pdo) {
    $data = getRequestBody();
    
    // Validate required fields
    $missing = validateFields($data, [
        'firebase_uid', 
        'entry_id', 
        't1_image_url', 
        't2_image_url', 
        'analysis_result', 
        'confidence'
    ]);
    
    if (!empty($missing)) {
        sendResponse(false, null, 'Missing fields: ' . implode(', ', $missing), 400);
    }
    
    try {
        // Get user_id from firebase_uid
        $stmt = $pdo->prepare("SELECT id FROM users WHERE firebase_uid = ?");
        $stmt->execute([$data['firebase_uid']]);
        $user = $stmt->fetch();
        
        if (!$user) {
            sendResponse(false, null, 'User not found. Please register first.', 404);
        }
        
        $user_id = $user['id'];
        
        // Insert analysis entry
        $stmt = $pdo->prepare("
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ");
        
        $stmt->execute([
            $data['entry_id'],
            $user_id,
            $data['t1_image_url'],
            $data['t2_image_url'],
            $data['analysis_result'],
            $data['confidence'],
            isset($data['analysis_mode']) ? $data['analysis_mode'] : 'online',
            isset($data['model_version']) ? $data['model_version'] : 'v1.0',
            isset($data['processing_time_ms']) ? $data['processing_time_ms'] : null
        ]);
        
        // Update statistics
        updateUserStatistics($pdo, $user_id);
        
        sendResponse(true, [
            'analysis_id' => $pdo->lastInsertId(),
            'entry_id' => $data['entry_id'],
            'message' => 'Analysis saved successfully'
        ], null, 201);
        
    } catch (PDOException $e) {
        if ($e->getCode() == 23000) {
            sendResponse(false, null, 'Analysis entry already exists', 409);
        } else {
            sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
        }
    }
}

/**
 * Get analysis list for a user
 * GET /api/analysis/list/{firebase_uid}?limit=10&offset=0
 */
function getAnalysisList($pdo, $firebase_uid) {
    $limit = isset($_GET['limit']) ? intval($_GET['limit']) : 10;
    $offset = isset($_GET['offset']) ? intval($_GET['offset']) : 0;
    
    try {
        // Get user_id
        $stmt = $pdo->prepare("SELECT id FROM users WHERE firebase_uid = ?");
        $stmt->execute([$firebase_uid]);
        $user = $stmt->fetch();
        
        if (!$user) {
            sendResponse(false, null, 'User not found', 404);
        }
        
        // Get analyses
        $stmt = $pdo->prepare("
            SELECT 
                id,
                entry_id,
                t1_image_url,
                t2_image_url,
                analysis_result,
                confidence,
                analysis_mode,
                model_version,
                processing_time_ms,
                created_at
            FROM mri_analysis_entries
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        ");
        $stmt->execute([$user['id'], $limit, $offset]);
        $analyses = $stmt->fetchAll();
        
        // Get total count
        $stmt = $pdo->prepare("SELECT COUNT(*) as total FROM mri_analysis_entries WHERE user_id = ?");
        $stmt->execute([$user['id']]);
        $total = $stmt->fetch()['total'];
        
        sendResponse(true, [
            'analyses' => $analyses,
            'total' => $total,
            'limit' => $limit,
            'offset' => $offset
        ]);
        
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Get specific analysis by entry_id
 * GET /api/analysis/{entry_id}
 */
function getAnalysisById($pdo, $entry_id) {
    try {
        $stmt = $pdo->prepare("
            SELECT 
                mae.*,
                u.firebase_uid,
                u.email
            FROM mri_analysis_entries mae
            JOIN users u ON mae.user_id = u.id
            WHERE mae.entry_id = ?
        ");
        $stmt->execute([$entry_id]);
        $analysis = $stmt->fetch();
        
        if ($analysis) {
            sendResponse(true, $analysis);
        } else {
            sendResponse(false, null, 'Analysis not found', 404);
        }
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Delete analysis entry
 * DELETE /api/analysis/{entry_id}
 */
function deleteAnalysis($pdo, $entry_id) {
    try {
        $stmt = $pdo->prepare("DELETE FROM mri_analysis_entries WHERE entry_id = ?");
        $stmt->execute([$entry_id]);
        
        if ($stmt->rowCount() > 0) {
            sendResponse(true, ['message' => 'Analysis deleted successfully']);
        } else {
            sendResponse(false, null, 'Analysis not found', 404);
        }
    } catch (PDOException $e) {
        sendResponse(false, null, 'Database error: ' . $e->getMessage(), 500);
    }
}

/**
 * Update user statistics after new analysis
 */
function updateUserStatistics($pdo, $user_id) {
    try {
        $stmt = $pdo->prepare("
            INSERT INTO analysis_statistics (
                user_id,
                total_analyses,
                modic_detected_count,
                no_modic_count,
                average_confidence,
                last_analysis_date
            )
            SELECT 
                ?,
                COUNT(*) as total,
                SUM(CASE WHEN analysis_result = 'Modic Change Detected' THEN 1 ELSE 0 END) as modic,
                SUM(CASE WHEN analysis_result = 'No Modic Changes' THEN 1 ELSE 0 END) as no_modic,
                AVG(confidence) as avg_conf,
                MAX(created_at) as last_date
            FROM mri_analysis_entries
            WHERE user_id = ?
            ON DUPLICATE KEY UPDATE
                total_analyses = VALUES(total_analyses),
                modic_detected_count = VALUES(modic_detected_count),
                no_modic_count = VALUES(no_modic_count),
                average_confidence = VALUES(average_confidence),
                last_analysis_date = VALUES(last_analysis_date)
        ");
        $stmt->execute([$user_id, $user_id]);
    } catch (PDOException $e) {
        // Log but don't fail the request
        error_log("Statistics update failed: " . $e->getMessage());
    }
}
?>
