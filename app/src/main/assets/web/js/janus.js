/**
 * Janus Sovereign Workstation Bridge - Core Frontend Engine
 * Zero-Cloud Privacy & Autonomous Mobile Workstation Control
 * RMediaTech LLC
 */

let currentPath = '';
let urlToken = new URLSearchParams(window.location.search).get('token');
let authToken = (urlToken || localStorage.getItem('janus_pairing_token') || '').trim();
if (urlToken) {
    localStorage.setItem('janus_pairing_token', urlToken.trim());
}

let ws = null;
let wsPingInterval = null;
let logcatActive = false;
let allLogcatLines = [];
let activePreviewPath = '';
let activePreviewText = '';

// Auth & Badge Management
function updateAuthBadge() {
    const badge = document.getElementById('auth-pin-display');
    if (badge) {
        badge.innerText = authToken ? authToken : 'Enter PIN';
    }
}

function showAuthModal(errMsg = '') {
    const modal = document.getElementById('auth-modal');
    const errDiv = document.getElementById('auth-error-msg');
    const input = document.getElementById('auth-pin-input');
    if (modal) modal.classList.add('active');
    if (errDiv) {
        errDiv.innerText = errMsg;
        errDiv.style.display = errMsg ? 'block' : 'none';
    }
    if (input) {
        if (authToken && !input.value) input.value = authToken;
        setTimeout(() => input.focus(), 100);
    }
}

function hideAuthModal() {
    const modal = document.getElementById('auth-modal');
    if (modal) modal.classList.remove('active');
}

async function submitAuthPin() {
    const input = document.getElementById('auth-pin-input');
    const errDiv = document.getElementById('auth-error-msg');
    const pin = (input ? input.value : '').trim().toUpperCase();
    if (!pin) {
        if (errDiv) {
            errDiv.innerText = 'Please enter the pairing PIN from your phone.';
            errDiv.style.display = 'block';
        }
        return;
    }

    try {
        if (errDiv) {
            errDiv.innerText = 'Verifying with device...';
            errDiv.style.display = 'block';
        }
        const res = await fetch('/api/v1/auth/status?token=' + encodeURIComponent(pin));
        if (res.ok) {
            const data = await res.json();
            authToken = pin;
            localStorage.setItem('janus_pairing_token', pin);
            updateAuthBadge();
            hideAuthModal();
            updateTelemetryDisplay(data);
            loadFiles('');
            fetchClipboardHistory();
            initWebSocket();
        } else {
            if (errDiv) {
                errDiv.innerText = 'Authentication failed: Invalid PIN. Check the code on your phone.';
                errDiv.style.display = 'block';
            }
        }
    } catch (e) {
        if (errDiv) {
            errDiv.innerText = 'Connection error: Unable to reach device on this network.';
            errDiv.style.display = 'block';
        }
    }
}

function updateTelemetryDisplay(data) {
    const deviceEl = document.getElementById('tel-device');
    const storageEl = document.getElementById('tel-storage');
    if (deviceEl && data.deviceBrand) {
        deviceEl.innerText = `${data.deviceBrand} ${data.deviceName} (Android ${data.androidVersion})`;
    }
    if (storageEl && data.storageFormatted) {
        storageEl.innerText = data.storageFormatted;
    }
}

// Navigation Tabs
function switchTab(tab, tabElement) {
    document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    if (tabElement) {
        tabElement.classList.add('active');
    }
    const targetPanel = document.getElementById('panel-' + tab);
    if (targetPanel) {
        targetPanel.classList.add('active');
    }

    if (tab === 'logcat' && !logcatActive) {
        toggleLogcat();
    }
}

// Status & Telemetry Fetch
async function fetchStatus() {
    if (!authToken) {
        showAuthModal();
        return;
    }
    try {
        const res = await fetch('/api/v1/auth/status?token=' + encodeURIComponent(authToken));
        if (res.status === 401 || res.status === 403) {
            authToken = '';
            localStorage.removeItem('janus_pairing_token');
            updateAuthBadge();
            showAuthModal('PIN rejected or session expired. Please re-enter device Pairing PIN.');
            return;
        }
        if (res.ok) {
            const data = await res.json();
            updateTelemetryDisplay(data);
        }
    } catch (e) {
        console.error('Status fetch failed', e);
    }
}

// Filesystem & Explorer
async function loadFiles(path = '') {
    if (!authToken) {
        showAuthModal();
        return;
    }
    currentPath = path;
    renderBreadcrumbs();
    const tbody = document.getElementById('file-tbody');
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">Loading...</td></tr>';
    try {
        const res = await fetch(`/api/v1/fs/list?path=${encodeURIComponent(path)}&token=${encodeURIComponent(authToken)}`);
        if (res.status === 401 || res.status === 403) {
            showAuthModal('PIN authentication required to access device storage.');
            return;
        }
        const data = await res.json();
        if (data.entries) {
            renderFileTable(data.entries);
        } else {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-danger">Failed to load directory.</td></tr>';
        }
    } catch (e) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-danger">Failed to load directory.</td></tr>';
    }
}

function getParentPath(path) {
    if (!path) return '';
    const parts = path.split('/').filter(Boolean);
    parts.pop();
    return parts.join('/');
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function escapeJs(str) {
    return String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function renderBreadcrumbs() {
    const container = document.getElementById('breadcrumbs');
    if (!container) return;
    const parts = currentPath.split('/').filter(Boolean);
    const parentPath = getParentPath(currentPath);

    const upBtn = currentPath
        ? `<button class="btn btn-xs btn-up" onclick="navigateTo('${escapeJs(parentPath)}')">⬆ Up</button>`
        : '';

    let html = upBtn + `<span class="crumb" onclick="navigateTo('')">root</span>`;
    let accumulated = '';
    for (let i = 0; i < parts.length; i++) {
        const part = parts[i];
        accumulated += (accumulated ? '/' : '') + part;
        const isLast = (i === parts.length - 1);
        if (isLast) {
            html += ` / <span class="crumb">${escapeHtml(part)}</span>`;
        } else {
            html += ` / <span class="crumb" onclick="navigateTo('${escapeJs(accumulated)}')">${escapeHtml(part)}</span>`;
        }
    }
    container.innerHTML = html;
}

function navigateTo(path) {
    loadFiles(path);
}

function renderFileTable(entries) {
    const tbody = document.getElementById('file-tbody');
    if (!tbody) return;
    let parentRow = '';
    if (currentPath) {
        const parentPath = getParentPath(currentPath);
        parentRow = `
            <tr class="parent-dir-row" onclick="navigateTo('${escapeJs(parentPath)}')">
                <td colspan="4">
                    <div class="file-name parent-dir-name">
                        <span>📁</span>
                        <b>.. (Parent Directory)</b>
                    </div>
                </td>
            </tr>
        `;
    }

    if (entries.length === 0) {
        tbody.innerHTML = parentRow + '<tr><td colspan="4" class="text-center text-muted">Empty folder</td></tr>';
        return;
    }

    const rows = entries.map(item => {
        const icon = item.isDirectory ? '📁' : (item.isApk ? '📦' : (item.mimeType.startsWith('image/') ? '🖼️' : (item.mimeType.startsWith('video/') ? '🎬' : (item.mimeType.startsWith('audio/') ? '🎵' : (item.mimeType === 'application/pdf' ? '📄' : '📝')))));
        const badge = item.isApk ? '<span class="apk-badge">APK</span>' : '';
        const clickHandler = item.isDirectory
            ? `onclick="navigateTo('${escapeJs(item.path)}')"`
            : `onclick="previewFile('${escapeJs(item.path)}', '${escapeJs(item.mimeType)}', '${escapeJs(item.name)}', '${escapeJs(item.formattedSize)}')"` ;
        return `
            <tr>
                <td>
                    <div class="file-name" ${clickHandler} title="${item.isDirectory ? 'Open folder' : 'Click to preview'}">
                        <span>${icon}</span>
                        <b>${escapeHtml(item.name)}</b>
                        ${badge}
                    </div>
                </td>
                <td>${item.formattedSize}</td>
                <td class="file-mime">${item.mimeType}</td>
                <td class="text-right">
                    ${item.isApk ? `<button class="btn btn-primary btn-xs btn-action-spacer" onclick="installApk('${escapeJs(item.path)}')">Install APK</button>` : ''}
                    ${!item.isDirectory ? `<button class="btn btn-xs btn-preview" onclick="previewFile('${escapeJs(item.path)}', '${escapeJs(item.mimeType)}', '${escapeJs(item.name)}', '${escapeJs(item.formattedSize)}')">Preview</button>` : ''}
                    ${item.isDirectory ? `<button class="btn btn-xs btn-action-spacer" onclick="downloadZip('${escapeJs(item.path)}')">.ZIP</button>` : `<button class="btn btn-xs btn-action-spacer" onclick="downloadFile('${escapeJs(item.path)}')">Download</button>`}
                    <button class="btn btn-danger btn-xs" onclick="deleteEntry('${escapeJs(item.path)}')">Delete</button>
                </td>
            </tr>
        `;
    }).join('');

    tbody.innerHTML = parentRow + rows;
}

// Preview Modal Lightbox & Handlers
function previewFile(path, mimeType, name, sizeFormatted = '') {
    activePreviewPath = path;
    activePreviewText = '';
    const modal = document.getElementById('preview-modal');
    const title = document.getElementById('preview-filename');
    const icon = document.getElementById('preview-icon');
    const size = document.getElementById('preview-size');
    const body = document.getElementById('preview-body');
    const copyBtn = document.getElementById('preview-copy-btn');

    if (!modal) return;
    title.innerText = name;
    size.innerText = sizeFormatted ? `(${sizeFormatted})` : '';
    copyBtn.classList.add('hidden');
    modal.classList.add('active');

    const inlineUrl = `/api/v1/fs/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(authToken)}&inline=true`;

    if (mimeType.startsWith('image/')) {
        icon.innerText = '🖼️';
        body.innerHTML = `<img src="${inlineUrl}" alt="${escapeHtml(name)}" class="preview-img">`;
    } else if (mimeType.startsWith('video/')) {
        icon.innerText = '🎬';
        body.innerHTML = `<video controls autoplay src="${inlineUrl}" class="preview-video"></video>`;
    } else if (mimeType.startsWith('audio/')) {
        icon.innerText = '🎵';
        body.innerHTML = `<div class="preview-audio-wrapper"><audio controls autoplay src="${inlineUrl}" class="preview-audio"></audio></div>`;
    } else if (mimeType === 'application/pdf') {
        icon.innerText = '📄';
        body.innerHTML = `<iframe src="${inlineUrl}" class="preview-iframe"></iframe>`;
    } else if (mimeType.startsWith('text/') || mimeType === 'application/json' || mimeType === 'application/xml') {
        icon.innerText = '📝';
        body.innerHTML = `<div class="text-accent">Fetching content...</div>`;
        fetch(inlineUrl)
            .then(r => {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.text();
            })
            .then(txt => {
                activePreviewText = txt;
                copyBtn.classList.remove('hidden');
                body.innerHTML = `<pre class="preview-text-content"><code>${escapeHtml(txt)}</code></pre>`;
            })
            .catch(err => {
                body.innerHTML = `<div class="text-danger">Failed to load text preview: ${escapeHtml(err.message)}</div>`;
            });
    } else {
        icon.innerText = '📦';
        body.innerHTML = `
            <div class="preview-fallback-wrapper">
                <p class="preview-fallback-title">Binary or non-previewable file format</p>
                <p class="preview-fallback-mime">MIME: ${escapeHtml(mimeType)}</p>
                <button class="btn btn-primary" onclick="downloadPreviewFile()">Download to Device</button>
            </div>
        `;
    }
}

function closePreviewModal() {
    const modal = document.getElementById('preview-modal');
    if (modal) {
        modal.classList.remove('active');
        const body = document.getElementById('preview-body');
        if (body) body.innerHTML = '';
    }
}

function handlePreviewOverlayClick(event) {
    if (event.target.id === 'preview-modal') {
        closePreviewModal();
    }
}

function downloadPreviewFile() {
    if (activePreviewPath) {
        downloadFile(activePreviewPath);
    }
}

function copyPreviewText() {
    if (!activePreviewText) return;
    navigator.clipboard.writeText(activePreviewText).then(() => {
        const btn = document.getElementById('preview-copy-btn');
        if (btn) {
            const orig = btn.innerText;
            btn.innerText = '✓ Copied!';
            setTimeout(() => { btn.innerText = orig; }, 1800);
        }
    });
}

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closePreviewModal();
});

// Download & Sideload Operations
function downloadFile(path) {
    window.location.href = `/api/v1/fs/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(authToken)}`;
}

function downloadZip(path = currentPath) {
    window.location.href = `/api/v1/fs/zip?path=${encodeURIComponent(path)}&token=${encodeURIComponent(authToken)}`;
}

async function deleteEntry(path) {
    if (!confirm(`Are you sure you want to delete ${path}?`)) return;
    const res = await fetch(`/api/v1/fs/delete?path=${encodeURIComponent(path)}&token=${encodeURIComponent(authToken)}`, { method: 'POST' });
    if (res.status === 401) {
        showAuthModal('PIN authentication required.');
        return;
    }
    loadFiles(currentPath);
}

async function installApk(path) {
    if (!confirm(`Trigger Android Package Installer on device for:\n${path}?`)) return;
    try {
        const res = await fetch(`/api/v1/install?path=${encodeURIComponent(path)}&token=${encodeURIComponent(authToken)}`, {
            method: 'POST'
        });
        const data = await res.json();
        if (res.ok && data.success) {
            alert('Package installer triggered on your Android device! Complete installation on screen.');
        } else {
            alert('Failed to install APK: ' + (data.error || 'Unknown error'));
        }
    } catch (e) {
        alert('Failed to reach device: ' + e.message);
    }
}

function handleFileSelect(e) {
    if (e.target.files && e.target.files.length > 0) {
        uploadFiles(e.target.files);
    }
}

async function uploadFiles(files) {
    if (!authToken) {
        showAuthModal('PIN required to upload files.');
        return;
    }
    for (const file of files) {
        const formData = new FormData();
        formData.append('file', file);
        const isApk = file.name.endsWith('.apk');
        const endpoint = isApk 
            ? `/api/v1/install?token=${encodeURIComponent(authToken)}` 
            : `/api/v1/fs/upload?path=${encodeURIComponent(currentPath)}&fileName=${encodeURIComponent(file.name)}&token=${encodeURIComponent(authToken)}`;

        try {
            const res = await fetch(endpoint, {
                method: 'POST',
                body: formData
            });
            if (res.status === 401) {
                showAuthModal('PIN required to upload files.');
                return;
            }
        } catch (e) {
            console.error('Upload failed', e);
        }
    }
    loadFiles(currentPath);
}

// Clipboard Operations (Bidirectional Bridge)
async function pushClipboard() {
    if (!authToken) {
        showAuthModal();
        return;
    }
    const input = document.getElementById('clip-input');
    const text = input ? input.value : '';
    if (!text.trim()) return;
    const res = await fetch(`/api/v1/clipboard?token=${encodeURIComponent(authToken)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
    });
    if (res.status === 401) {
        showAuthModal('PIN authentication required for clipboard.');
        return;
    }
    if (input) input.value = '';
    fetchClipboardHistory();
}

async function fetchClipboardHistory() {
    if (!authToken) return;
    try {
        const res = await fetch(`/api/v1/clipboard?token=${encodeURIComponent(authToken)}`);
        if (res.ok) {
            const data = await res.json();
            renderClipboardHistory(data.history || []);
        }
    } catch (e) {
        console.error('Clipboard fetch failed', e);
    }
}

function renderClipboardHistory(history) {
    const container = document.getElementById('clip-history');
    if (!container) return;
    if (history.length === 0) {
        container.innerHTML = '<p class="clip-empty">No clipboard entries yet.</p>';
        return;
    }
    container.innerHTML = history.map(item => `
        <div class="clip-item">
            <span class="clip-item-text">${escapeHtml(item.text)}</span>
            <button class="btn btn-xs" onclick="navigator.clipboard.writeText('${escapeJs(item.text)}')">Copy</button>
        </div>
    `).join('');
}

// WebSocket Live Telemetry & Logcat Stream
function initWebSocket() {
    const wsTel = document.getElementById('tel-ws');
    if (!authToken) {
        if (wsTel) {
            wsTel.innerText = 'Auth Required';
            wsTel.className = 'text-muted';
        }
        return;
    }
    if (wsPingInterval) {
        clearInterval(wsPingInterval);
        wsPingInterval = null;
    }
    if (ws) {
        try { ws.close(); } catch (_) {}
    }
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    try {
        ws = new WebSocket(`${proto}//${window.location.host}/ws?token=${encodeURIComponent(authToken)}`);
        ws.onopen = () => {
            if (wsTel) {
                wsTel.innerText = 'WS Live';
                wsTel.className = 'text-success';
            }
            // 2500ms heartbeat keep-alive prevents any socket idle read timeouts
            wsPingInterval = setInterval(() => {
                if (ws && ws.readyState === WebSocket.OPEN) {
                    try { ws.send(JSON.stringify({ action: 'ping' })); } catch (_) {}
                }
            }, 2500);
        };
        ws.onclose = () => {
            if (wsPingInterval) { clearInterval(wsPingInterval); wsPingInterval = null; }
            if (wsTel) {
                wsTel.innerText = 'Reconnecting...';
                wsTel.className = 'text-amber';
            }
            if (authToken) {
                setTimeout(initWebSocket, 2000);
            }
        };
        ws.onerror = () => {
            try { ws.close(); } catch(e) {}
        };
        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'clipboard_update') {
                    fetchClipboardHistory();
                } else if (data.type === 'logcat_line') {
                    appendLogcat(data.line);
                }
            } catch (e) {}
        };
    } catch (e) {
        if (authToken) setTimeout(initWebSocket, 3000);
    }
}

// Background poll ensuring updates even if socket buffers or sleeps
setInterval(() => {
    if (authToken) fetchClipboardHistory();
}, 2000);

// Wireless Logcat
function toggleLogcat() {
    logcatActive = !logcatActive;
    const btn = document.getElementById('logcat-toggle-btn');
    if (btn) btn.innerText = logcatActive ? 'Stop Stream' : 'Resume Stream';
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: logcatActive ? 'start_logcat' : 'stop_logcat' }));
    }
}

function clearLogcat() {
    allLogcatLines = [];
    const consoleDiv = document.getElementById('logcat-console');
    if (consoleDiv) consoleDiv.innerHTML = '';
}

function filterLogcat() {
    const query = (document.getElementById('logcat-filter')?.value || '').toLowerCase();
    const consoleDiv = document.getElementById('logcat-console');
    if (!consoleDiv) return;
    consoleDiv.innerHTML = '';
    const filtered = query 
        ? allLogcatLines.filter(line => line.toLowerCase().includes(query))
        : allLogcatLines;
    
    const fragment = document.createDocumentFragment();
    filtered.slice(-500).forEach(line => {
        const p = document.createElement('div');
        p.innerText = line;
        if (line.includes(' E ') || line.includes('FATAL')) p.className = 'logcat-line-error';
        else if (line.includes(' W ')) p.className = 'logcat-line-warn';
        else if (line.includes(' I ')) p.className = 'logcat-line-info';
        fragment.appendChild(p);
    });
    consoleDiv.appendChild(fragment);
    consoleDiv.scrollTop = consoleDiv.scrollHeight;
}

function appendLogcat(line) {
    allLogcatLines.push(line);
    if (allLogcatLines.length > 2000) allLogcatLines.shift();
    
    const query = (document.getElementById('logcat-filter')?.value || '').toLowerCase();
    if (query && !line.toLowerCase().includes(query)) return;

    const consoleDiv = document.getElementById('logcat-console');
    if (!consoleDiv) return;
    const p = document.createElement('div');
    p.innerText = line;
    if (line.includes(' E ') || line.includes('FATAL')) p.className = 'logcat-line-error';
    else if (line.includes(' W ')) p.className = 'logcat-line-warn';
    else if (line.includes(' I ')) p.className = 'logcat-line-info';
    consoleDiv.appendChild(p);
    consoleDiv.scrollTop = consoleDiv.scrollHeight;
}

// Drag & Drop Setup
function setupDropZone() {
    const dropZone = document.getElementById('drop-zone');
    if (!dropZone) return;
    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        if (e.dataTransfer && e.dataTransfer.files.length > 0) {
            uploadFiles(e.dataTransfer.files);
        }
    });
}

// Event Listeners Initialization on DOM ready
document.addEventListener('DOMContentLoaded', () => {
    setupDropZone();
    updateAuthBadge();

    // Setup tabs
    document.querySelectorAll('.nav-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const target = tab.getAttribute('data-tab');
            switchTab(target, tab);
        });
    });

    // Enter key on PIN input
    const pinInput = document.getElementById('auth-pin-input');
    if (pinInput) {
        pinInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') submitAuthPin();
        });
    }

    if (authToken) {
        fetchStatus();
        loadFiles('');
        fetchClipboardHistory();
        initWebSocket();
    } else {
        showAuthModal();
    }
});
