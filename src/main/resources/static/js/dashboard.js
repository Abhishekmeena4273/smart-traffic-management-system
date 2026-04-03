var stompClient = null;
var currentLaneData = {};
var isProcessing = false;
var laneTimers = {};
var laneProcessing = { 1: false, 2: false, 3: false, 4: false };
var prevLaneSignal = { 1: null, 2: null, 3: null, 4: null };
var lastServerBoxes = { 1: [], 2: [], 3: [], 4: [] };

var COLORS = {
    car: '#3b82f6', bus: '#ef4444', truck: '#f59e0b',
    motorcycle: '#22c55e', bicycle: '#8b5cf6'
};

/* ── SessionStorage: save/restore video positions ── */
function saveVideoState() {
    for (var i = 1; i <= 4; i++) {
        var video = document.getElementById('video-' + i);
        if (video && !isNaN(video.currentTime)) {
            sessionStorage.setItem('video_pos_' + i, video.currentTime.toString());
        }
        var canvas = document.getElementById('canvas-' + i);
        if (canvas) {
            try {
                sessionStorage.setItem('video_boxes_' + i, JSON.stringify(lastServerBoxes[i] || []));
            } catch (e) {}
        }
    }
    sessionStorage.setItem('video_state_saved', 'true');
}

function restoreVideoState() {
    var saved = sessionStorage.getItem('video_state_saved');
    if (saved !== 'true') return false;

    for (var i = 1; i <= 4; i++) {
        var pos = sessionStorage.getItem('video_pos_' + i);
        if (pos !== null) {
            var video = document.getElementById('video-' + i);
            if (video) {
                video.currentTime = parseFloat(pos);
            }
        }
        try {
            var boxesJson = sessionStorage.getItem('video_boxes_' + i);
            if (boxesJson) {
                lastServerBoxes[i] = JSON.parse(boxesJson);
            }
        } catch (e) {}
    }
    return true;
}

// Save state before navigating away
window.addEventListener('beforeunload', function () {
    saveVideoState();
});

/* ── WebSocket for signal state + server detection boxes ── */
function connectWebSocket() {
    try {
        var socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        stompClient.connect({}, function () {
            stompClient.subscribe('/topic/live-status', function (message) {
                var data = JSON.parse(message.body);
                handleUpdate(data);
            });
        }, function () { setTimeout(connectWebSocket, 3000); });
    } catch (e) { setTimeout(connectWebSocket, 3000); }
}

function handleUpdate(payload) {
    var lanes = payload.lanes || [];
    var serverBoxes = payload.detectionBoxes || {};

    lanes.forEach(function (lane) {
        currentLaneData[lane.laneId] = lane;
        updateLaneCard(lane);

        // Draw server-side boxes if we don't have frontend boxes for this lane
        var boxes = serverBoxes[String(lane.laneId)] || serverBoxes[lane.laneId] || [];
        if (boxes.length > 0) {
            lastServerBoxes[lane.laneId] = boxes;
            if (!laneProcessing[lane.laneId]) {
                var canvas = document.getElementById('canvas-' + lane.laneId);
                var vid = document.getElementById('video-' + lane.laneId);
                if (canvas && vid) {
                    canvas.width = vid.clientWidth;
                    canvas.height = vid.clientHeight;
                    drawBoxes(canvas, boxes, vid);
                }
            }
        }
    });
}

/* ── Lane card ── */
function updateLaneCard(lane) {
    var signalText = document.getElementById('signal-text-' + lane.laneId);
    if (signalText) {
        signalText.textContent = lane.signal;
        signalText.className = 'signal-state state-' + lane.signal.toLowerCase();
    }

    var card = document.getElementById('lane-' + lane.laneId);
    if (card) {
        var lights = card.querySelectorAll('.signal-light');
        lights.forEach(function (l) { l.classList.remove('active'); });
        if (lane.signal === 'RED') card.querySelector('.red-light').classList.add('active');
        if (lane.signal === 'YELLOW') card.querySelector('.yellow-light').classList.add('active');
        if (lane.signal === 'GREEN') card.querySelector('.green-light').classList.add('active');
        card.classList.toggle('alert-high', lane.density > 0.85);

        // Signal-based video control — always pause, freeze-step advances via currentTime
        var video = document.getElementById('video-' + lane.laneId);
        if (video) {
            video.pause();
        }
    }

    var timerEl = document.getElementById('timer-' + lane.laneId);
    var timerTotalEl = document.getElementById('timer-total-' + lane.laneId);
    if (timerEl) timerEl.textContent = lane.timerSeconds;
    if (timerTotalEl) timerTotalEl.textContent = lane.totalTimerSeconds;

    var el;
    el = document.getElementById('vehicles-' + lane.laneId); if (el) el.textContent = lane.vehicleCount;
    el = document.getElementById('density-' + lane.laneId); if (el) el.textContent = (lane.density * 100).toFixed(1) + '%';

    var badge = card ? card.querySelector('.density-badge') : null;
    if (badge) {
        badge.textContent = (lane.density * 100).toFixed(1) + '%';
        if (lane.density > 0.8) badge.style.background = 'rgba(220,38,38,0.2)';
        else if (lane.density > 0.5) badge.style.background = 'rgba(245,158,11,0.2)';
        else badge.style.background = 'rgba(148,163,184,0.15)';
    }

    if (lane.videoSource) {
        var vid = document.getElementById('video-' + lane.laneId);
        if (vid && vid.getAttribute('src') !== lane.videoSource) {
            vid.src = lane.videoSource;
            vid.load();
            if (lane.signal !== 'RED') vid.pause();
        }
    }

    var dirContainer = document.getElementById('directions-' + lane.laneId);
    if (dirContainer) {
        var arrows = dirContainer.querySelectorAll('.dir-arrow');
        arrows.forEach(function(arrow) {
            var dir = arrow.getAttribute('data-dir');
            var isActive = false;
            
            if (dir === 'straight' && lane.canGoStraight) {
                isActive = true;
            } else if (dir === 'left' && lane.canTurnLeft) {
                isActive = true;
            } else if (dir === 'right' && lane.canTurnRight) {
                isActive = true;
            }
            
            arrow.classList.toggle('active', isActive);
        });
    }
}

/* ═══════════════════════════════════════════════════════════
   FREEZE-STEP DETECTION (frontend, frame-accurate display)
   ═══════════════════════════════════════════════════════════ */

var captureCanvas = document.createElement('canvas');

function detectLane(laneId) {
    if (!isProcessing) return;
    if (laneProcessing[laneId]) return;
    laneProcessing[laneId] = true;

    var video = document.getElementById('video-' + laneId);
    if (!video || video.readyState < 2) {
        laneProcessing[laneId] = false;
        laneTimers[laneId] = setTimeout(function () { detectLane(laneId); }, 500);
        return;
    }

    // Capture current frame (works even when paused)
    var vw = video.videoWidth || 320;
    var vh = video.videoHeight || 240;
    var maxW = 640;
    var scale = (vw > maxW) ? maxW / vw : 1;
    var cw = Math.round(vw * scale);
    var ch = Math.round(vh * scale);

    captureCanvas.width = cw;
    captureCanvas.height = ch;

    var ctx = captureCanvas.getContext('2d');
    try {
        ctx.drawImage(video, 0, 0, cw, ch);
    } catch (e) {
        laneProcessing[laneId] = false;
        laneTimers[laneId] = setTimeout(function () { detectLane(laneId); }, 500);
        return;
    }

    captureCanvas.toBlob(function (blob) {
        if (!blob) {
            laneProcessing[laneId] = false;
            laneTimers[laneId] = setTimeout(function () { detectLane(laneId); }, 500);
            return;
        }

        var formData = new FormData();
        formData.append('file', blob, 'lane' + laneId + '.jpg');

        fetch('/api/detect', {
            method: 'POST',
            body: formData
        })
        .then(function (r) { return r.json(); })
        .then(function (result) {
            var boxes = result.vehicles || [];
            lastServerBoxes[laneId] = boxes;

            // Draw boxes — canvas matches VIDEO rendered size (not container)
            var drawCanvas = document.getElementById('canvas-' + laneId);
            if (drawCanvas) {
                drawCanvas.width = video.clientWidth;
                drawCanvas.height = video.clientHeight;
                drawBoxes(drawCanvas, boxes, video);
            }

            // Advance ONE frame only if signal is GREEN
            var vid = document.getElementById('video-' + laneId);
            var signal = currentLaneData[laneId] ? currentLaneData[laneId].signal : 'GREEN';
            if (signal === 'GREEN' && vid && vid.duration) {
                var frameDur = 1 / 25;
                var next = vid.currentTime + frameDur;
                if (next >= vid.duration) next = 0;
                vid.currentTime = next;
            }

            saveVideoState();

            // Continue detection — always (regardless of signal)
            if (signal === 'GREEN' && vid) {
                // Wait for seek, then continue
                var seekDone = false;
                vid.onseeked = function () {
                    if (seekDone) return;
                    seekDone = true;
                    vid.onseeked = null;
                    laneProcessing[laneId] = false;
                    detectLane(laneId);
                };
                setTimeout(function () {
                    if (seekDone) return;
                    seekDone = true;
                    vid.onseeked = null;
                    laneProcessing[laneId] = false;
                    detectLane(laneId);
                }, 200);
            } else {
                // RED signal — don't advance, just repeat detection on same frame
                laneProcessing[laneId] = false;
                laneTimers[laneId] = setTimeout(function () { detectLane(laneId); }, 500);
            }
        })
        .catch(function () {
            laneProcessing[laneId] = false;
            laneTimers[laneId] = setTimeout(function () { detectLane(laneId); }, 1000);
        });
    }, 'image/jpeg', 0.6);
}

/* ── Calculate actual video rendered dimensions (object-fit: contain) ── */
function getVideoRenderedRect(video) {
    var container = video.parentElement;
    var cw = container.clientWidth;
    var ch = container.clientHeight;
    var vw = video.videoWidth || cw;
    var vh = video.videoHeight || ch;

    // Calculate scale for object-fit: contain
    var scale = Math.min(cw / vw, ch / vh);
    var renderW = vw * scale;
    var renderH = vh * scale;

    // Calculate offset (letterboxing centering)
    var offsetX = (cw - renderW) / 2;
    var offsetY = (ch - renderH) / 2;

    return { x: offsetX, y: offsetY, width: renderW, height: renderH, scale: scale };
}

/* ── Draw bounding boxes ── */
function drawBoxes(canvas, vehicles, video) {
    var ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (!vehicles || vehicles.length === 0) return;

    // Get actual video rendered dimensions to account for object-fit: contain
    var rect = video ? getVideoRenderedRect(video) : { x: 0, y: 0, width: canvas.width, height: canvas.height, scale: 1 };

    vehicles.forEach(function (v) {
        var color = COLORS[v.type] || '#ffffff';

        // Scale from detection coordinates to video rendered coordinates
        var x = rect.x + (v.x * rect.width);
        var y = rect.y + (v.y * rect.height);
        var w = v.width * rect.width;
        var h = v.height * rect.height;

        if (w < 3 || h < 3) return;

        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.strokeRect(x, y, w, h);

        var label = v.type + ' ' + Math.round(v.confidence * 100) + '%';
        ctx.font = 'bold 11px sans-serif';
        var tw = ctx.measureText(label).width + 8;

        ctx.fillStyle = color;
        ctx.fillRect(x, Math.max(0, y - 17), tw, 17);
        ctx.fillStyle = '#fff';
        ctx.fillText(label, x + 4, Math.max(13, y - 4));
    });
}

/* ── Start / Stop ── */
function setProcessingState(processing) {
    isProcessing = processing;
    var btnStart = document.getElementById('btnStart');
    var btnStop = document.getElementById('btnStop');
    var badge = document.getElementById('statusBadge');

    if (processing) {
        if (btnStart) btnStart.style.display = 'none';
        if (btnStop) btnStop.style.display = '';
        if (badge) { badge.textContent = 'Running'; badge.className = 'badge badge-success'; }

        // Restore saved positions or start fresh
        var restored = restoreVideoState();

        for (var i = 1; i <= 4; i++) {
            var video = document.getElementById('video-' + i);
            if (video) {
                video.pause();
                if (!restored) {
                    video.currentTime = 0;
                }
            }
        }

        // Restore saved boxes immediately
        if (restored) {
            for (var i = 1; i <= 4; i++) {
                var boxes = lastServerBoxes[i];
                if (boxes && boxes.length > 0) {
                    var canvas = document.getElementById('canvas-' + i);
                    var vid = document.getElementById('video-' + i);
                    if (canvas && vid) {
                        canvas.width = vid.clientWidth;
                        canvas.height = vid.clientHeight;
                        drawBoxes(canvas, boxes, vid);
                    }
                }
            }
        }

        // Start detection for each lane, staggered
        for (var i = 1; i <= 4; i++) {
            (function (lane, delay) {
                laneTimers[lane] = setTimeout(function () { detectLane(lane); }, delay);
            })(i, (i - 1) * 500);
        }
    } else {
        if (btnStart) btnStart.style.display = '';
        if (btnStop) btnStop.style.display = 'none';
        if (badge) { badge.textContent = 'Stopped'; badge.className = 'badge badge-secondary'; }
        for (var i = 1; i <= 4; i++) {
            if (laneTimers[i]) { clearTimeout(laneTimers[i]); delete laneTimers[i]; }
            laneProcessing[i] = false;
            var canvas = document.getElementById('canvas-' + i);
            if (canvas) canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
        }
    }
}

/* ── Auto-size video containers to match video resolution ── */
function autoSizeVideoContainers() {
    for (var i = 1; i <= 4; i++) {
        (function (laneId) {
            var video = document.getElementById('video-' + laneId);
            if (!video) return;

            function resize() {
                var vw = video.videoWidth;
                var vh = video.videoHeight;
                if (!vw || !vh) return;
                var container = video.closest('.video-container');
                if (!container) return;
                var panel = container.parentElement.querySelector('.signal-panel');
                var panelW = panel ? panel.clientWidth : 90;
                var availableW = container.parentElement.clientWidth - panelW;
                var targetH = Math.max(200, Math.round(availableW * vh / vw));
                targetH = Math.min(targetH, 500);
                container.style.height = targetH + 'px';
            }

            if (video.readyState >= 1) {
                resize();
            } else {
                video.addEventListener('loadedmetadata', resize);
            }
        })(i);
    }
}

/* ── Init ── */
document.addEventListener('DOMContentLoaded', function () {
    connectWebSocket();

    // Pause all videos immediately on load
    for (var i = 1; i <= 4; i++) {
        var v = document.getElementById('video-' + i);
        if (v) { v.pause(); v.currentTime = 0; }
    }

    var serverProcessing = document.body.getAttribute('data-processing') === 'true';
    if (serverProcessing) {
        setProcessingState(true);
    }

    var btnStart = document.getElementById('btnStart');
    var btnStop = document.getElementById('btnStop');
    var btnOverride = document.getElementById('btnOverride');
    var manualLane = document.getElementById('manualLane');

    if (btnStart) {
        btnStart.addEventListener('click', function () {
            btnStart.disabled = true; btnStart.textContent = 'Starting...';
            // Clear saved state when starting fresh
            for (var i = 1; i <= 4; i++) {
                sessionStorage.removeItem('video_pos_' + i);
                sessionStorage.removeItem('video_boxes_' + i);
            }
            sessionStorage.removeItem('video_state_saved');
            fetch('/api/video/start', { method: 'POST' })
                .then(function (r) { return r.json(); })
                .then(function () { setProcessingState(true); btnStart.disabled = false; btnStart.textContent = 'Start Processing'; })
                .catch(function () { btnStart.disabled = false; btnStart.textContent = 'Start Processing'; });
        });
    }

    if (btnStop) {
        btnStop.addEventListener('click', function () {
            btnStop.disabled = true; btnStop.textContent = 'Stopping...';
            // Clear saved state when stopping
            for (var i = 1; i <= 4; i++) {
                sessionStorage.removeItem('video_pos_' + i);
                sessionStorage.removeItem('video_boxes_' + i);
            }
            sessionStorage.removeItem('video_state_saved');
            fetch('/api/video/stop', { method: 'POST' })
                .then(function (r) { return r.json(); })
                .then(function () { setProcessingState(false); btnStop.disabled = false; btnStop.textContent = 'Stop Processing'; })
                .catch(function () { btnStop.disabled = false; btnStop.textContent = 'Stop Processing'; });
        });
    }

    if (btnOverride) {
        btnOverride.addEventListener('click', function () {
            var lane = manualLane.value;
            if (!lane) { alert('Select a lane first'); return; }
            fetch('/api/signal/manual?lane=' + lane, { method: 'POST' })
                .then(function () { manualLane.value = ''; });
        });
    }

    // Auto-size video containers to match video resolution
    autoSizeVideoContainers();

    // Upload / toggle
    for (var i = 1; i <= 4; i++) {
        (function (laneId) {
            var fileInput = document.getElementById('file-input-' + laneId);
            if (fileInput) {
                fileInput.addEventListener('change', function (e) {
                    var file = e.target.files[0]; if (!file) return;
                    var formData = new FormData(); formData.append('file', file);
                    fetch('/api/video/upload/' + laneId, { method: 'POST', body: formData })
                        .then(function (r) { return r.json(); })
                        .then(function (data) {
                            var video = document.getElementById('video-' + laneId);
                            if (video) { video.src = data.source; video.load(); video.pause(); }
                            var toggle = document.getElementById('toggle-' + laneId);
                            if (toggle) toggle.checked = false;
                            var tt = toggle ? toggle.parentElement.querySelector('.toggle-text') : null;
                            if (tt) tt.textContent = 'Uploaded';
                        });
                });
            }
            var toggle = document.getElementById('toggle-' + laneId);
            if (toggle) {
                toggle.addEventListener('change', function () {
                    var mode = this.checked ? 'default' : 'uploaded';
                    fetch('/api/video/switch/' + laneId + '?mode=' + mode, { method: 'POST' })
                        .then(function (r) { return r.json(); })
                        .then(function (data) {
                            var video = document.getElementById('video-' + laneId);
                            if (video) { video.src = data.source; video.load(); video.pause(); }
                            var tt = toggle.parentElement.querySelector('.toggle-text');
                            if (tt) tt.textContent = mode === 'default' ? 'Default' : 'Uploaded';
                        });
                });
            }
        })(i);
    }
});
