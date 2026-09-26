<?php
declare(strict_types=1);

$config = [
    'site_title'           => 'Sunucu Ceza Listesi',
    'server_name'          => 'Minecraft Sunucusu',
    'server_ip'            => 'play.sunucum.com',
    'api_scheme'           => 'http',
    'api_host'             => '127.0.0.1',
    'api_port'             => 8080,
    'base_path'            => '/api/hbansystem',
    'api_key'              => getenv('HBANSYSTEM_API_KEY') ?: 'BURAYA_API_KEY_YAZIN',
    'hmac_secret'          => getenv('HBANSYSTEM_HMAC_SECRET') ?: 'CHANGE_ME',
    'previous_hmac_secret' => '',
    'require_https'        => false,
    'per_page'             => 15,
    'timeout_sec'          => 3,
    'avatar_api'           => 'https://mc-heads.net/avatar/%s/32'
];

if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_GET['action'] ?? '') === 'sync') {
    handle_inbound_sync();
    exit;
}

function handle_inbound_sync(): void {
    global $config;

    header('Content-Type: application/json; charset=UTF-8');

    $tsHeader = $_SERVER['HTTP_X_HBAN_TIMESTAMP'] ?? '';
    $nonce = $_SERVER['HTTP_X_HBAN_NONCE'] ?? '';
    $reqId = $_SERVER['HTTP_X_HBAN_REQUEST_ID'] ?? '';
    $signature = $_SERVER['HTTP_X_HBAN_SIGNATURE'] ?? '';

    $ts = (int) $tsHeader;
    if (abs(time() - $ts) > 300) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Zaman aşımı veya geçersiz zaman damgası.']);
        return;
    }

    $rawBody = file_get_contents('php://input');
    $bodyHash = hash('sha256', $rawBody !== false ? $rawBody : '');
    $path = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH) ?: '/';

    $canonical = "POST\n{$path}\n{$ts}\n{$nonce}\n{$bodyHash}";

    $valid = false;
    $primary = $config['hmac_secret'];
    if (!empty($primary) && hash_equals(hash_hmac('sha256', $canonical, $primary), $signature)) {
        $valid = true;
    } elseif (!empty($config['previous_hmac_secret']) && hash_equals(hash_hmac('sha256', $canonical, $config['previous_hmac_secret']), $signature)) {
        $valid = true;
    }

    if (!$valid) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Geçersiz HMAC imzası.']);
        return;
    }

    $payload = json_decode($rawBody ?: '{}', true);
    $events = $payload['events'] ?? [];

    http_response_code(200);
    echo json_encode(['success' => true, 'processed' => count($events)]);
}

function hban_api_call(string $endpoint, array $query = []): array {
    global $config;

    $scheme = $config['require_https'] ? 'https' : $config['api_scheme'];
    $url = sprintf(
        '%s://%s:%d%s/%s',
        $scheme,
        $config['api_host'],
        $config['api_port'],
        rtrim($config['base_path'], '/'),
        ltrim($endpoint, '/')
    );

    if (!empty($query)) {
        $url .= '?' . http_build_query($query);
    }

    $parsed = parse_url($url);
    $path = $parsed['path'] ?? '/';
    $timestamp = time();
    $nonce = bin2hex(random_bytes(16));
    $requestId = sprintf('%s-%s-%s-%s', bin2hex(random_bytes(4)), bin2hex(random_bytes(2)), bin2hex(random_bytes(2)), bin2hex(random_bytes(6)));
    $emptyHash = hash('sha256', '');

    $canonical = "GET\n{$path}\n{$timestamp}\n{$nonce}\n{$emptyHash}";
    $signature = hash_hmac('sha256', $canonical, $config['hmac_secret']);

    $headers = [
        'Authorization: Bearer ' . $config['api_key'],
        'X-API-Key: ' . $config['api_key'],
        'X-HBan-Timestamp: ' . $timestamp,
        'X-HBan-Nonce: ' . $nonce,
        'X-HBan-Request-ID: ' . $requestId,
        'X-HBan-Signature: ' . $signature,
        'Accept: application/json',
        'Accept-Encoding: gzip',
        'User-Agent: HBanSystem-WebPortal/1.0'
    ];

    if (function_exists('curl_init')) {
        $ch = curl_init();
        curl_setopt_array($ch, [
            CURLOPT_URL            => $url,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $config['timeout_sec'],
            CURLOPT_CONNECTTIMEOUT => $config['timeout_sec'],
            CURLOPT_HTTPHEADER     => $headers,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_ENCODING       => 'gzip',
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2
        ]);

        $body = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $curlError = curl_error($ch);
        curl_close($ch);

        if ($body === false || $status !== 200) {
            $msg = $curlError ?: ($status > 0 ? "HTTP Durum Kodu: $status" : "Sunucuya ulaşılamadı.");
            return ['success' => false, 'error' => $msg, 'results' => [], 'total' => 0, 'total_pages' => 1];
        }
    } else {
        $context = stream_context_create([
            'http' => [
                'method'  => 'GET',
                'header'  => implode("\r\n", $headers),
                'timeout' => (float) $config['timeout_sec']
            ],
            'ssl' => [
                'verify_peer'      => true,
                'verify_peer_name' => true
            ]
        ]);
        $body = @file_get_contents($url, false, $context);
        if ($body === false) {
            return ['success' => false, 'error' => 'Sunucu ile bağlantı kurulamadı.', 'results' => [], 'total' => 0, 'total_pages' => 1];
        }
    }

    $data = json_decode((string) $body, true);
    if (!is_array($data) || empty($data['success'])) {
        return [
            'success'     => false,
            'error'       => $data['error'] ?? 'API geçersiz bir yanıt döndürdü.',
            'results'     => [],
            'total'       => 0,
            'total_pages' => 1
        ];
    }

    return $data;
}

$tab = $_GET['tab'] ?? 'bans';
if (!in_array($tab, ['bans', 'mutes', 'all'], true)) {
    $tab = 'bans';
}

$page = max(1, (int) ($_GET['page'] ?? 1));
$search = trim(strip_tags((string) ($_GET['search'] ?? '')));
$scope = ($_GET['scope'] ?? 'active') === 'all' ? 'all' : 'active';

$apiEndpoint = match ($tab) {
    'mutes' => 'mutes',
    'all'   => 'punishments',
    default => 'bans'
};

$queryParams = [
    'page'   => $page,
    'limit'  => $config['per_page'],
    'active' => ($scope === 'active') ? 'true' : 'false'
];

if ($search !== '') {
    $queryParams['search'] = $search;
}

$apiResponse = hban_api_call($apiEndpoint, $queryParams);
$punishments = $apiResponse['results'] ?? [];
$totalItems  = (int) ($apiResponse['total'] ?? 0);
$totalPages = max(1, (int) ($apiResponse['total_pages'] ?? 1));
$hasError    = !$apiResponse['success'];
$errorMessage = $apiResponse['error'] ?? '';

function e(?string $value): string {
    return htmlspecialchars((string) ($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function render_type_badge(string $type): string {
    $type = strtoupper($type);
    $map = [
        'BAN'         => ['Kalıcı Ban', 'badge-ban'],
        'TEMPBAN'     => ['Süreli Ban', 'badge-tempban'],
        'IP_BAN'      => ['IP Ban', 'badge-ipban'],
        'TEMP_IP_BAN' => ['Süreli IP Ban', 'badge-ipban'],
        'MUTE'        => ['Kalıcı Mute', 'badge-mute'],
        'TEMP_MUTE'   => ['Süreli Mute', 'badge-tempmute'],
        'KICK'        => ['Atılma', 'badge-warn'],
        'WARN'        => ['Uyarı', 'badge-warn'],
    ];

    $info = $map[$type] ?? [$type, 'badge-default'];
    return sprintf('<span class="badge %s">%s</span>', $info[1], e($info[0]));
}

function render_status_badge(string $status): string {
    return match ($status) {
        'Aktif'        => '<span class="status-pill status-active"><span class="dot"></span>Aktif</span>',
        'Kaldırıldı'   => '<span class="status-pill status-revoked"><span class="dot"></span>Affedildi</span>',
        'Süresi Doldu' => '<span class="status-pill status-expired"><span class="dot"></span>Süresi Bitti</span>',
        default        => sprintf('<span class="status-pill">%s</span>', e($status))
    };
}
?>
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><?= e($config['site_title']) ?></title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <style>
        *, *::before, *::after {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        :root {
            --bg-canvas: #0c0e12;
            --bg-surface: #14171f;
            --bg-surface-hover: #1a1e27;
            --bg-elevated: #1e232e;
            --border-subtle: #242936;
            --border-strong: #333a4d;
            --text-main: #f1f3f7;
            --text-secondary: #9098a9;
            --text-muted: #5e6678;
            --accent: #38bdf8;
            --accent-dim: rgba(56, 189, 248, 0.12);
            --danger: #f43f5e;
            --danger-dim: rgba(244, 63, 94, 0.12);
            --warning: #f59e0b;
            --warning-dim: rgba(245, 158, 11, 0.12);
            --success: #10b981;
            --success-dim: rgba(16, 185, 129, 0.12);
            --purple: #a855f7;
            --purple-dim: rgba(168, 85, 247, 0.12);
            --radius-sm: 6px;
            --radius-md: 10px;
            --radius-lg: 14px;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            background-color: var(--bg-canvas);
            color: var(--text-main);
            min-height: 100vh;
            line-height: 1.5;
            -webkit-font-smoothing: antialiased;
            padding: 32px 16px;
        }

        .wrap {
            max-width: 1240px;
            margin: 0 auto;
        }

        .topbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 20px 24px;
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            margin-bottom: 24px;
            gap: 16px;
            flex-wrap: wrap;
        }

        .brand-col {
            display: flex;
            align-items: center;
            gap: 14px;
        }

        .server-avatar {
            width: 44px;
            height: 44px;
            background: var(--bg-elevated);
            border: 1px solid var(--border-strong);
            border-radius: var(--radius-md);
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 1.1rem;
            color: var(--accent);
            letter-spacing: -0.5px;
        }

        .brand-text h1 {
            font-size: 1.25rem;
            font-weight: 700;
            color: var(--text-main);
            letter-spacing: -0.3px;
        }

        .brand-text .meta {
            font-size: 0.85rem;
            color: var(--text-secondary);
        }

        .ip-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: var(--bg-elevated);
            border: 1px solid var(--border-subtle);
            padding: 8px 14px;
            border-radius: var(--radius-md);
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.85rem;
            color: var(--text-main);
            cursor: pointer;
            user-select: none;
            transition: border-color 0.15s, background-color 0.15s;
        }

        .ip-badge:hover {
            border-color: var(--border-strong);
            background: var(--bg-surface-hover);
        }

        .ip-badge svg {
            width: 14px;
            height: 14px;
            fill: none;
            stroke: currentColor;
            stroke-width: 2;
        }

        .controls {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
            margin-bottom: 18px;
            flex-wrap: wrap;
        }

        .nav-tabs {
            display: flex;
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 4px;
            gap: 4px;
        }

        .nav-tab {
            padding: 8px 16px;
            font-size: 0.88rem;
            font-weight: 500;
            color: var(--text-secondary);
            text-decoration: none;
            border-radius: var(--radius-sm);
            transition: color 0.15s, background-color 0.15s;
        }

        .nav-tab:hover {
            color: var(--text-main);
        }

        .nav-tab.active {
            background: var(--bg-elevated);
            color: var(--text-main);
            font-weight: 600;
        }

        .filter-group {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }

        .scope-toggle {
            display: flex;
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 4px;
            gap: 4px;
        }

        .scope-btn {
            font-size: 0.8rem;
            font-weight: 500;
            padding: 7px 12px;
            border-radius: var(--radius-sm);
            text-decoration: none;
            color: var(--text-muted);
            transition: all 0.15s;
        }

        .scope-btn.active {
            background: var(--bg-elevated);
            color: var(--text-main);
        }

        .search-box {
            position: relative;
            display: flex;
            align-items: center;
        }

        .search-box input {
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            color: var(--text-main);
            font-size: 0.88rem;
            padding: 8px 36px 8px 12px;
            width: 220px;
            outline: none;
            transition: border-color 0.15s, width 0.2s;
        }

        .search-box input:focus {
            border-color: var(--accent);
            width: 260px;
        }

        .search-box button {
            position: absolute;
            right: 10px;
            background: none;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            display: flex;
            align-items: center;
            padding: 0;
        }

        .search-box button:hover {
            color: var(--text-main);
        }

        .alert-box {
            background: var(--danger-dim);
            border: 1px solid var(--danger);
            color: var(--text-main);
            padding: 14px 18px;
            border-radius: var(--radius-md);
            margin-bottom: 20px;
            font-size: 0.9rem;
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .table-wrap {
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            overflow: hidden;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 0.88rem;
        }

        th {
            background: var(--bg-elevated);
            color: var(--text-secondary);
            font-size: 0.76rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            padding: 12px 18px;
            border-bottom: 1px solid var(--border-subtle);
        }

        td {
            padding: 14px 18px;
            border-bottom: 1px solid var(--border-subtle);
            color: var(--text-main);
            vertical-align: middle;
        }

        tbody tr {
            transition: background-color 0.12s;
            cursor: pointer;
        }

        tbody tr:hover {
            background-color: var(--bg-surface-hover);
        }

        tbody tr:last-child td {
            border-bottom: none;
        }

        .player-entry {
            display: inline-flex;
            align-items: center;
            gap: 10px;
            font-weight: 600;
            color: var(--text-main);
        }

        .player-entry img {
            width: 28px;
            height: 28px;
            border-radius: 4px;
            image-rendering: pixelated;
            background: var(--bg-elevated);
        }

        .staff-entry {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            color: var(--text-secondary);
            font-size: 0.85rem;
        }

        .staff-entry img {
            width: 20px;
            height: 20px;
            border-radius: 3px;
            image-rendering: pixelated;
        }

        .reason-cell {
            max-width: 280px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            color: var(--text-secondary);
        }

        .badge {
            display: inline-block;
            font-size: 0.75rem;
            font-weight: 600;
            padding: 3px 8px;
            border-radius: var(--radius-sm);
            line-height: 1.2;
            letter-spacing: 0.02em;
        }

        .badge-ban { background: var(--danger-dim); color: var(--danger); border: 1px solid rgba(244, 63, 94, 0.25); }
        .badge-tempban { background: var(--warning-dim); color: var(--warning); border: 1px solid rgba(245, 158, 11, 0.25); }
        .badge-ipban { background: var(--danger-dim); color: #fb7185; border: 1px solid rgba(251, 113, 133, 0.25); }
        .badge-mute { background: var(--purple-dim); color: var(--purple); border: 1px solid rgba(168, 85, 247, 0.25); }
        .badge-tempmute { background: var(--accent-dim); color: var(--accent); border: 1px solid rgba(56, 189, 248, 0.25); }
        .badge-warn { background: var(--warning-dim); color: var(--warning); border: 1px solid rgba(245, 158, 11, 0.25); }
        .badge-default { background: var(--bg-elevated); color: var(--text-secondary); border: 1px solid var(--border-strong); }

        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 0.78rem;
            font-weight: 500;
            color: var(--text-muted);
        }

        .status-pill .dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: currentColor;
        }

        .status-active { color: var(--success); }
        .status-revoked { color: var(--danger); }
        .status-expired { color: var(--text-muted); }

        .date-cell {
            font-size: 0.82rem;
            color: var(--text-secondary);
            font-variant-numeric: tabular-nums;
        }

        .empty-state {
            padding: 56px 20px;
            text-align: center;
            color: var(--text-muted);
            font-size: 0.95rem;
        }

        .pagination {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 20px;
            border-top: 1px solid var(--border-subtle);
            font-size: 0.85rem;
            color: var(--text-secondary);
        }

        .page-actions {
            display: flex;
            gap: 6px;
        }

        .page-btn {
            background: var(--bg-elevated);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            padding: 6px 12px;
            border-radius: var(--radius-sm);
            text-decoration: none;
            font-size: 0.82rem;
            font-weight: 500;
            transition: all 0.15s;
        }

        .page-btn:hover:not(.disabled) {
            border-color: var(--border-strong);
            background: var(--bg-surface-hover);
        }

        .page-btn.disabled {
            opacity: 0.4;
            pointer-events: none;
        }

        .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.7);
            backdrop-filter: blur(4px);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            padding: 16px;
        }

        .modal-overlay.open {
            display: flex;
        }

        .modal {
            background: var(--bg-surface);
            border: 1px solid var(--border-strong);
            border-radius: var(--radius-lg);
            width: 100%;
            max-width: 520px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
            overflow: hidden;
            animation: modalIn 0.15s ease-out;
        }

        @keyframes modalIn {
            from { opacity: 0; transform: scale(0.96); }
            to { opacity: 1; transform: scale(1); }
        }

        .modal-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 20px;
            background: var(--bg-elevated);
            border-bottom: 1px solid var(--border-subtle);
        }

        .modal-head h3 {
            font-size: 1rem;
            font-weight: 600;
            color: var(--text-main);
        }

        .modal-close {
            background: none;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            font-size: 1.2rem;
            line-height: 1;
            padding: 4px;
        }

        .modal-close:hover {
            color: var(--text-main);
        }

        .modal-body {
            padding: 20px;
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .detail-row {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            padding-bottom: 10px;
            border-bottom: 1px solid var(--border-subtle);
            font-size: 0.88rem;
            gap: 16px;
        }

        .detail-row:last-child {
            border-bottom: none;
            padding-bottom: 0;
        }

        .detail-label {
            color: var(--text-muted);
            font-size: 0.8rem;
            text-transform: uppercase;
            font-weight: 600;
            letter-spacing: 0.04em;
        }

        .detail-value {
            color: var(--text-main);
            text-align: right;
            word-break: break-word;
        }

        .detail-mono {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.82rem;
            color: var(--accent);
        }

        .footer {
            margin-top: 32px;
            text-align: center;
            font-size: 0.8rem;
            color: var(--text-muted);
        }

        @media (max-width: 860px) {
            body { padding: 16px 12px; }
            .topbar { flex-direction: column; align-items: flex-start; }
            .controls { flex-direction: column; align-items: stretch; }
            .filter-group { justify-content: space-between; }
            .search-box input { width: 100%; }
            .search-box input:focus { width: 100%; }

            table, thead, tbody, th, td, tr { display: block; }
            thead { display: none; }
            tbody tr {
                padding: 14px 16px;
                border-bottom: 1px solid var(--border-subtle);
            }
            td {
                padding: 6px 0;
                border: none;
                display: flex;
                justify-content: space-between;
                align-items: center;
                font-size: 0.85rem;
            }
            td::before {
                content: attr(data-label);
                color: var(--text-muted);
                font-size: 0.78rem;
                font-weight: 600;
                text-transform: uppercase;
            }
            .reason-cell { max-width: none; white-space: normal; text-align: right; }
        }
    </style>
</head>
<body>

<div class="wrap">
    <header class="topbar">
        <div class="brand-col">
            <div class="server-avatar">HB</div>
            <div class="brand-text">
                <h1><?= e($config['server_name']) ?></h1>
                <div class="meta">Ceza ve Güvenlik Takip Portalı</div>
            </div>
        </div>
        <div class="ip-badge" id="ipCopyBtn" title="Kopyalamak için tıkla" data-ip="<?= e($config['server_ip']) ?>">
            <svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
            <span id="ipText"><?= e($config['server_ip']) ?></span>
        </div>
    </header>

    <?php if ($hasError): ?>
        <div class="alert-box">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
            <div>
                <strong>Bağlantı Kurulamadı:</strong> <?= e($errorMessage) ?>
                <div style="font-size: 0.8rem; margin-top: 3px; color: var(--text-secondary);">
                    Lütfen sunucu ayarlarını, API anahtarını ve HMAC sırrını kontrol edin.
                </div>
            </div>
        </div>
    <?php endif; ?>

    <div class="controls">
        <nav class="nav-tabs">
            <?php
            $makeUrl = function(string $newTab) use ($search, $scope) {
                $p = ['tab' => $newTab];
                if ($search !== '') $p['search'] = $search;
                if ($scope === 'all') $p['scope'] = 'all';
                return '?' . http_build_query($p);
            };
            ?>
            <a href="<?= $makeUrl('bans') ?>" class="nav-tab <?= $tab === 'bans' ? 'active' : '' ?>">Yasaklamalar</a>
            <a href="<?= $makeUrl('mutes') ?>" class="nav-tab <?= $tab === 'mutes' ? 'active' : '' ?>">Susturmalar</a>
            <a href="<?= $makeUrl('all') ?>" class="nav-tab <?= $tab === 'all' ? 'active' : '' ?>">Tüm Cezalar</a>
        </nav>

        <div class="filter-group">
            <div class="scope-toggle">
                <?php
                $scopeUrl = function(string $newScope) use ($tab, $search) {
                    $p = ['tab' => $tab, 'scope' => $newScope];
                    if ($search !== '') $p['search'] = $search;
                    return '?' . http_build_query($p);
                };
                ?>
                <a href="<?= $scopeUrl('active') ?>" class="scope-btn <?= $scope === 'active' ? 'active' : '' ?>">Aktif</a>
                <a href="<?= $scopeUrl('all') ?>" class="scope-btn <?= $scope === 'all' ? 'active' : '' ?>">Tüm Geçmiş</a>
            </div>

            <form class="search-box" method="GET" action="">
                <input type="hidden" name="tab" value="<?= e($tab) ?>">
                <input type="hidden" name="scope" value="<?= e($scope) ?>">
                <input type="text" name="search" placeholder="Oyuncu adı ara..." value="<?= e($search) ?>" autocomplete="off" spellcheck="false">
                <button type="submit" aria-label="Ara">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                </button>
            </form>
        </div>
    </div>

    <div class="table-wrap">
        <?php if (empty($punishments)): ?>
            <div class="empty-state">
                <?= $search !== '' ? 'Aramanızla eşleşen ceza kaydı bulunamadı.' : 'Bu kategoride kayıtlı ceza bulunmuyor.' ?>
            </div>
        <?php else: ?>
            <table>
                <thead>
                    <tr>
                        <th>Oyuncu</th>
                        <th>Tür</th>
                        <th>Sebep</th>
                        <th>Yetkili</th>
                        <th>Tarih</th>
                        <th>Kalan / Bitiş</th>
                        <th>Durum</th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ($punishments as $row): 
                        $avatarUrl = sprintf($config['avatar_api'], urlencode((string)($row['player'] ?? 'Steve')));
                        $staffAvatar = sprintf($config['avatar_api'], urlencode((string)($row['staff'] ?? 'Console')));
                        $rowJson = htmlspecialchars((string) json_encode($row, JSON_UNESCAPED_UNICODE), ENT_QUOTES, 'UTF-8');
                    ?>
                    <tr class="punishment-row" data-detail="<?= $rowJson ?>">
                        <td data-label="Oyuncu">
                            <div class="player-entry">
                                <img src="<?= $avatarUrl ?>" alt="" loading="lazy" onerror="this.src='https://mc-heads.net/avatar/Steve/32'">
                                <span><?= e($row['player'] ?? 'Bilinmiyor') ?></span>
                            </div>
                        </td>
                        <td data-label="Tür"><?= render_type_badge((string)($row['type'] ?? 'BAN')) ?></td>
                        <td data-label="Sebep" class="reason-cell" title="<?= e($row['reason'] ?? '') ?>">
                            <?= e($row['reason'] ?? 'Belirtilmedi') ?>
                        </td>
                        <td data-label="Yetkili">
                            <div class="staff-entry">
                                <img src="<?= $staffAvatar ?>" alt="" loading="lazy" onerror="this.src='https://mc-heads.net/avatar/Console/32'">
                                <span><?= e($row['staff'] ?? 'Yetkili') ?></span>
                            </div>
                        </td>
                        <td data-label="Tarih" class="date-cell"><?= e($row['created_at_formatted'] ?? '-') ?></td>
                        <td data-label="Kalan / Bitiş" class="date-cell">
                            <?= e($row['duration'] ?? '-') ?>
                            <?php if (!empty($row['expires_at_formatted']) && $row['expires_at_formatted'] !== 'Asla'): ?>
                                <span style="color: var(--text-muted); font-size: 0.75rem;">(<?= e($row['expires_at_formatted']) ?>)</span>
                            <?php endif; ?>
                        </td>
                        <td data-label="Durum"><?= render_status_badge((string)($row['status'] ?? 'Bilinmiyor')) ?></td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>

            <div class="pagination">
                <div>
                    Toplam <b><?= number_format($totalItems) ?></b> ceza kaydı (Sayfa <?= $page ?> / <?= $totalPages ?>)
                </div>
                <div class="page-actions">
                    <?php
                    $pageUrl = function(int $targetPage) use ($tab, $scope, $search) {
                        $p = ['tab' => $tab, 'scope' => $scope, 'page' => $targetPage];
                        if ($search !== '') $p['search'] = $search;
                        return '?' . http_build_query($p);
                    };
                    ?>
                    <a href="<?= $pageUrl(1) ?>" class="page-btn <?= $page <= 1 ? 'disabled' : '' ?>">« İlk</a>
                    <a href="<?= $pageUrl($page - 1) ?>" class="page-btn <?= $page <= 1 ? 'disabled' : '' ?>">‹ Önceki</a>
                    <a href="<?= $pageUrl($page + 1) ?>" class="page-btn <?= $page >= $totalPages ? 'disabled' : '' ?>">Sonraki ›</a>
                    <a href="<?= $pageUrl($totalPages) ?>" class="page-btn <?= $page >= $totalPages ? 'disabled' : '' ?>">Son »</a>
                </div>
            </div>
        <?php endif; ?>
    </div>

    <footer class="footer">
        HBanSystem &bull; Geliştirici: Ardelys
    </footer>
</div>

<div class="modal-overlay" id="detailModal">
    <div class="modal">
        <div class="modal-head">
            <h3>Ceza Detayı</h3>
            <button class="modal-close" id="modalCloseBtn">&times;</button>
        </div>
        <div class="modal-body" id="modalContent"></div>
    </div>
</div>

<script>
(function() {
    'use strict';

    const ipBtn = document.getElementById('ipCopyBtn');
    const ipText = document.getElementById('ipText');
    if (ipBtn && ipText) {
        const originalIp = ipText.textContent;
        ipBtn.addEventListener('click', function() {
            navigator.clipboard.writeText(ipBtn.dataset.ip).then(function() {
                ipText.textContent = 'Kopyalandı!';
                setTimeout(function() { ipText.textContent = originalIp; }, 1500);
            }).catch(function() {});
        });
    }

    const modal = document.getElementById('detailModal');
    const modalContent = document.getElementById('modalContent');
    const modalCloseBtn = document.getElementById('modalCloseBtn');

    function closeModal() {
        if (modal) modal.classList.remove('open');
    }

    if (modalCloseBtn) modalCloseBtn.addEventListener('click', closeModal);
    if (modal) {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) closeModal();
        });
    }
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') closeModal();
    });

    document.querySelectorAll('.punishment-row').forEach(function(row) {
        row.addEventListener('click', function() {
            const raw = row.getAttribute('data-detail');
            if (!raw) return;
            try {
                const data = JSON.parse(raw);
                renderModal(data);
            } catch (err) {}
        });
    });

    function esc(s) {
        if (!s) return '-';
        const d = document.createElement('div');
        d.textContent = s;
        return d.innerHTML;
    }

    function renderModal(d) {
        if (!modalContent) return;
        let html = '';

        if (d.id) {
            html += '<div class="detail-row"><span class="detail-label">Ceza ID</span><span class="detail-value detail-mono">#' + esc(String(d.id)) + '</span></div>';
        }
        html += '<div class="detail-row"><span class="detail-label">Oyuncu</span><span class="detail-value"><b>' + esc(d.player) + '</b></span></div>';
        if (d.uuid) {
            html += '<div class="detail-row"><span class="detail-label">UUID</span><span class="detail-value detail-mono" style="font-size:0.75rem;">' + esc(d.uuid) + '</span></div>';
        }
        html += '<div class="detail-row"><span class="detail-label">Tür</span><span class="detail-value">' + esc(d.type) + '</span></div>';
        html += '<div class="detail-row"><span class="detail-label">Sebep</span><span class="detail-value">' + esc(d.reason) + '</span></div>';
        html += '<div class="detail-row"><span class="detail-label">Yetkili</span><span class="detail-value">' + esc(d.staff) + '</span></div>';
        html += '<div class="detail-row"><span class="detail-label">Verilme Tarihi</span><span class="detail-value">' + esc(d.created_at_formatted) + '</span></div>';
        html += '<div class="detail-row"><span class="detail-label">Bitiş Tarihi</span><span class="detail-value">' + esc(d.expires_at_formatted) + '</span></div>';
        html += '<div class="detail-row"><span class="detail-label">Süre</span><span class="detail-value">' + esc(d.duration) + '</span></div>';
        html += '<div class="detail-row"><span class="detail-label">Durum</span><span class="detail-value">' + esc(d.status) + '</span></div>';

        modalContent.innerHTML = html;
        modal.classList.add('open');
    }
})();
</script>
</body>
</html>
