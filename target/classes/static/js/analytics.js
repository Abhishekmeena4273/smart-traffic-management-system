var charts = {};
var selectedSessionId = null;
var liveInterval = null;

function fetchAndRender() {
    fetch('/api/analytics/summary')
        .then(function (r) { return r.json(); })
        .then(function (data) {
            updateStats(data);
            renderCharts(data);
            renderSessionList(data.sessions || []);
        })
        .catch(function (err) {
            console.error('Failed to fetch analytics:', err);
        });
}

function fetchSessionAndRender(sessionId) {
    fetch('/api/analytics/session/' + sessionId)
        .then(function (r) { return r.json(); })
        .then(function (data) {
            updateStats(data);
            renderCharts(data);
        })
        .catch(function (err) {
            console.error('Failed to fetch session data:', err);
        });
}

function updateStats(data) {
    var el;

    el = document.getElementById('stat-busiest');
    if (el) el.textContent = data.busiestLane ? 'Lane ' + data.busiestLane : '-';

    el = document.getElementById('stat-density');
    if (el) el.textContent = ((data.averageDensity || 0) * 100).toFixed(1) + '%';
}

function renderCharts(data) {
    Chart.defaults.color = '#94a3b8';
    Chart.defaults.borderColor = '#334155';

    renderTimelineChart(data.timeline || []);
    renderVehicleTypeChart(data.vehicleTypeDistribution || {});
    renderDensityChart(data.densityPerLane || {});
    renderVehiclesLaneChart(data.vehiclesPerLane || {});
}

function destroyChart(id) {
    if (charts[id]) {
        charts[id].destroy();
        delete charts[id];
    }
}

function renderTimelineChart(timeline) {
    var canvas = document.getElementById('timelineChart');
    if (!canvas) return;
    destroyChart('timeline');

    var labels = timeline.map(function (t) { return t.timestamp; });
    var lane1 = timeline.map(function (t) { return t.lane1Count; });
    var lane2 = timeline.map(function (t) { return t.lane2Count; });
    var lane3 = timeline.map(function (t) { return t.lane3Count; });
    var lane4 = timeline.map(function (t) { return t.lane4Count; });

    if (labels.length === 0) {
        labels = ['No data yet'];
        lane1 = [0]; lane2 = [0]; lane3 = [0]; lane4 = [0];
    }

    charts['timeline'] = new Chart(canvas, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: 'Lane 1 (N)', data: lane1, borderColor: '#3b82f6', backgroundColor: 'rgba(59,130,246,0.1)', tension: 0.3, fill: true },
                { label: 'Lane 2 (E)', data: lane2, borderColor: '#ef4444', backgroundColor: 'rgba(239,68,68,0.1)', tension: 0.3, fill: true },
                { label: 'Lane 3 (S)', data: lane3, borderColor: '#22c55e', backgroundColor: 'rgba(34,197,94,0.1)', tension: 0.3, fill: true },
                { label: 'Lane 4 (W)', data: lane4, borderColor: '#eab308', backgroundColor: 'rgba(234,179,8,0.1)', tension: 0.3, fill: true }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'top', labels: { usePointStyle: true, padding: 15 } } },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(148,163,184,0.1)' } },
                x: { grid: { display: false } }
            }
        }
    });
}

function renderVehicleTypeChart(distribution) {
    var canvas = document.getElementById('vehicleTypeChart');
    if (!canvas) return;
    destroyChart('type');

    var labels = Object.keys(distribution);
    var values = Object.values(distribution);

    if (labels.length === 0) {
        labels = ['No data'];
        values = [1];
    }

    var colors = ['#3b82f6', '#ef4444', '#f59e0b', '#22c55e', '#8b5cf6', '#ec4899'];

    charts['type'] = new Chart(canvas, {
        type: 'doughnut',
        data: {
            labels: labels.map(function (l) { return l.charAt(0).toUpperCase() + l.slice(1); }),
            datasets: [{
                data: values,
                backgroundColor: colors.slice(0, labels.length),
                borderColor: '#1e293b',
                borderWidth: 3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'right', labels: { usePointStyle: true, padding: 12 } } },
            cutout: '55%'
        }
    });
}

function renderDensityChart(densityPerLane) {
    var canvas = document.getElementById('densityChart');
    if (!canvas) return;
    destroyChart('density');

    var labels = ['Lane 1 (N)', 'Lane 2 (E)', 'Lane 3 (S)', 'Lane 4 (W)'];
    var values = [
        (densityPerLane[1] || 0) * 100,
        (densityPerLane[2] || 0) * 100,
        (densityPerLane[3] || 0) * 100,
        (densityPerLane[4] || 0) * 100
    ];

    var colors = values.map(function (v) {
        if (v > 70) return '#ef4444';
        if (v > 40) return '#f59e0b';
        return '#22c55e';
    });

    charts['density'] = new Chart(canvas, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{ label: 'Density %', data: values, backgroundColor: colors, borderRadius: 6, barThickness: 40 }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, max: 100, grid: { color: 'rgba(148,163,184,0.1)' }, ticks: { callback: function (v) { return v + '%'; } } },
                x: { grid: { display: false } }
            }
        }
    });
}

function renderVehiclesLaneChart(vehiclesPerLane) {
    var canvas = document.getElementById('vehiclesLaneChart');
    if (!canvas) return;
    destroyChart('vehicles');

    var labels = ['Lane 1 (N)', 'Lane 2 (E)', 'Lane 3 (S)', 'Lane 4 (W)'];
    var values = [
        vehiclesPerLane[1] || 0,
        vehiclesPerLane[2] || 0,
        vehiclesPerLane[3] || 0,
        vehiclesPerLane[4] || 0
    ];

    charts['vehicles'] = new Chart(canvas, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{ label: 'Vehicles', data: values, backgroundColor: ['#3b82f6', '#ef4444', '#22c55e', '#eab308'], borderRadius: 6, barThickness: 40 }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } },
            scales: {
                x: { beginAtZero: true, grid: { color: 'rgba(148,163,184,0.1)' } },
                y: { grid: { display: false } }
            }
        }
    });
}

/* ── Session Sidebar ── */
function renderSessionList(sessions) {
    var container = document.getElementById('sessionList');
    if (!container) return;

    if (!sessions || sessions.length === 0) {
        container.innerHTML = '<p class="sidebar-empty">No sessions yet</p>';
        return;
    }

    var html = '';
    sessions.forEach(function (s) {
        var isActive = selectedSessionId === s.id;
        var timeShort = s.startTime.replace(/.*\s/, '').substring(0, 5);
        html += '<button class="sidebar-item' + (isActive ? ' active' : '') + '" data-session-id="' + s.id + '">';
        html += '<span>' + s.startTime + '</span>';
        html += '</button>';
    });
    container.innerHTML = html;

    container.querySelectorAll('.sidebar-item').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var id = parseInt(this.getAttribute('data-session-id'));
            selectSession(id);
        });
    });
}

function selectSession(sessionId) {
    if (liveInterval) {
        clearInterval(liveInterval);
        liveInterval = null;
    }

    selectedSessionId = sessionId;

    document.getElementById('btnLiveData').classList.remove('active');
    document.getElementById('analyticsTitle').textContent = 'Session #' + sessionId + ' Analytics';

    // Update sidebar active state
    document.querySelectorAll('.session-sidebar .sidebar-item').forEach(function (btn) {
        btn.classList.remove('active');
    });
    var target = document.querySelector('.sidebar-item[data-session-id="' + sessionId + '"]');
    if (target) target.classList.add('active');

    fetchSessionAndRender(sessionId);
}

function selectLiveData() {
    selectedSessionId = null;

    document.getElementById('btnLiveData').classList.add('active');
    document.getElementById('analyticsTitle').textContent = 'Traffic Analytics';

    // Update sidebar active state
    document.querySelectorAll('.session-sidebar .sidebar-item').forEach(function (btn) {
        btn.classList.remove('active');
    });
    document.getElementById('btnLiveData').classList.add('active');

    fetchAndRender();
    startLiveInterval();
}

function startLiveInterval() {
    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(function () {
        if (selectedSessionId === null) {
            fetchAndRender();
        }
    }, 10000);
}

document.addEventListener('DOMContentLoaded', function () {
    fetchAndRender();
    startLiveInterval();

    var btnLiveData = document.getElementById('btnLiveData');
    if (btnLiveData) {
        btnLiveData.addEventListener('click', selectLiveData);
    }

    var btnRefresh = document.getElementById('btnRefresh');
    if (btnRefresh) {
        btnRefresh.addEventListener('click', function () {
            btnRefresh.textContent = 'Refreshing...';
            btnRefresh.disabled = true;
            if (selectedSessionId !== null) {
                fetchSessionAndRender(selectedSessionId);
            } else {
                fetchAndRender();
            }
            setTimeout(function () {
                btnRefresh.textContent = 'Refresh Data';
                btnRefresh.disabled = false;
            }, 500);
        });
    }
});
