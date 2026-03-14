import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { trafficAPI } from '../services/api';
import { Car, LogOut, Activity, Clock } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

function Dashboard() {
    const [trafficData, setTrafficData] = useState([]);
    const [currentCount, setCurrentCount] = useState(0);
    const [signalState, setSignalState] = useState('RED');
    const navigate = useNavigate();

    // Fetch data every 3 seconds
    useEffect(() => {
                const fetchData = async () => {
            try {
                const response = await trafficAPI.getHistory();
                const data = response.data;
                setTrafficData(data);
                
                if (data.length > 0) {
                    const latest = data[data.length - 1];
                    setCurrentCount(latest.vehicleCount);
                    
                    // DEBUG: Log what we're receiving
                    console.log("📊 Latest Traffic Data:", latest);
                    console.log("📊 Signal State from Backend:", latest.signalState);
                    
                    // More robust signal detection
                    const signalText = latest.signalState || "RED";
                    if (signalText.includes("GREEN")) {
                        setSignalState("GREEN");
                    } else if (signalText.includes("YELLOW")) {
                        setSignalState("YELLOW");
                    } else {
                        setSignalState("RED");
                    }
                }
            } catch (err) {
                console.error('❌ Error fetching data:', err);
            }
        };
        fetchData();
        const interval = setInterval(fetchData, 3000);
        return () => clearInterval(interval);
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('user');
        navigate('/');
    };

    return (
        <div style={styles.container}>
            {/* Header */}
            <div style={styles.header}>
                <div style={styles.logo}>
                    <Car size={32} color="#4CAF50" />
                    <h1 style={styles.headerTitle}>Smart Traffic Dashboard</h1>
                </div>
                <button onClick={handleLogout} style={styles.logoutBtn}>
                    <LogOut size={20} />
                    Logout
                </button>
            </div>

            {/* Main Content */}
            <div style={styles.content}>
                {/* Signal Status Card with RED-YELLOW-GREEN */}
                <div style={{
                    ...styles.card,
                    background: signalState.includes('GREEN') 
                        ? 'linear-gradient(135deg, rgba(76, 175, 80, 0.2), rgba(76, 175, 80, 0.1))' 
                        : signalState.includes('YELLOW')
                        ? 'linear-gradient(135deg, rgba(255, 193, 7, 0.2), rgba(255, 193, 7, 0.1))'
                        : 'linear-gradient(135deg, rgba(244, 67, 54, 0.2), rgba(244, 67, 54, 0.1))',
                    border: signalState.includes('GREEN') 
                        ? '1px solid rgba(76, 175, 80, 0.5)' 
                        : signalState.includes('YELLOW')
                        ? '1px solid rgba(255, 193, 7, 0.5)'
                        : '1px solid rgba(244, 67, 54, 0.5)',
                }}>
                    <h3 style={styles.cardTitle}>🚦 Current Signal State</h3>
                    <div style={styles.signalLight}>
                        {/* RED Light */}
                        <div style={{
                            ...styles.light,
                            background: signalState.includes('RED') && !signalState.includes('YELLOW') && !signalState.includes('GREEN') ? '#f44336' : '#333',
                            boxShadow: signalState.includes('RED') && !signalState.includes('YELLOW') && !signalState.includes('GREEN')
                                ? '0 0 30px rgba(244, 67, 54, 0.8), 0 0 60px rgba(244, 67, 54, 0.4)' 
                                : 'none',
                            opacity: signalState.includes('RED') && !signalState.includes('YELLOW') && !signalState.includes('GREEN') ? 1 : 0.3,
                            transition: 'all 0.3s ease',
                        }}></div>
                        
                        {/* YELLOW Light */}
                        <div style={{
                            ...styles.light,
                            background: signalState.includes('YELLOW') ? '#FFC107' : '#333',
                            boxShadow: signalState.includes('YELLOW')
                                ? '0 0 30px rgba(255, 193, 7, 0.8), 0 0 60px rgba(255, 193, 7, 0.4)' 
                                : 'none',
                            opacity: signalState.includes('YELLOW') ? 1 : 0.3,
                            transition: 'all 0.3s ease',
                        }}></div>
                        
                        {/* GREEN Light */}
                        <div style={{
                            ...styles.light,
                            background: signalState.includes('GREEN') ? '#4CAF50' : '#333',
                            boxShadow: signalState.includes('GREEN')
                                ? '0 0 30px rgba(76, 175, 80, 0.8), 0 0 60px rgba(76, 175, 80, 0.4)' 
                                : 'none',
                            opacity: signalState.includes('GREEN') ? 1 : 0.3,
                            transition: 'all 0.3s ease',
                        }}></div>
                    </div>
                    <h2 style={{
                        ...styles.signalText,
                        color: signalState.includes('GREEN') ? '#4CAF50' 
                            : signalState.includes('YELLOW') ? '#FFC107' 
                            : '#f44336',
                    }}>
                        {signalState.includes('GREEN') ? 'GREEN' 
                        : signalState.includes('YELLOW') ? 'YELLOW' 
                        : 'RED'}
                    </h2>
                    <p style={{textAlign: 'center', color: '#888', fontSize: '13px', marginTop: '8px'}}>
                        {signalState.includes('GREEN') ? '🚗 Lane Open - Traffic Flowing' 
                        : signalState.includes('YELLOW') ? '⚠️ Transition - Prepare to Stop/Go' 
                        : '🛑 Lane Closed - Wait for Green'}
                    </p>
                </div>

                {/* Video Feed Card */}
                <div style={{...styles.card, gridColumn: 'span 3'}}>
                    <h3 style={styles.cardTitle}>
                        📹 Live Traffic Feed
                    </h3>
                    <div style={styles.videoContainer}>
                        <img 
                            src="http://localhost:5000/video_feed" 
                            alt="Traffic Feed"
                            style={styles.videoFeed}
                        />
                    </div>
                    <p style={styles.videoNote}>
                        🎯 AI Detection Active | Real-time Vehicle Counting
                    </p>
                </div>

                {/* Live Traffic Chart */}
                <div style={{...styles.card, gridColumn: 'span 3'}}>
                    <h3 style={styles.cardTitle}>📈 Vehicle Count - Last 10 Updates</h3>
                    <div style={{height: '200px', width: '100%'}}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={trafficData.slice(-10)}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                                <XAxis 
                                    dataKey="timestamp" 
                                    stroke="#888" 
                                    fontSize={11}
                                    tickFormatter={(time) => new Date(time).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                                />
                                <YAxis stroke="#888" fontSize={11} />
                                <Tooltip 
                                    contentStyle={{
                                        background: 'rgba(30,30,50,0.95)',
                                        border: '1px solid rgba(255,255,255,0.1)',
                                        borderRadius: '8px',
                                        color: '#fff'
                                    }}
                                />
                                <Line 
                                    type="monotone" 
                                    dataKey="vehicleCount" 
                                    stroke="#4CAF50" 
                                    strokeWidth={3}
                                    dot={{fill: '#4CAF50', strokeWidth: 2, r: 4}}
                                    activeDot={{r: 6, stroke: '#fff', strokeWidth: 2}}
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Vehicle Count Card */}
                <div style={styles.card}>
                    <h3 style={styles.cardTitle}>
                        <Activity size={20} style={{marginRight: 8}} />
                        Vehicles Detected
                    </h3>
                    <p style={styles.bigNumber}>{currentCount}</p>
                    <p style={styles.cardSubtitle}>Lane: NORTH</p>
                </div>

                {/* Wait Time Card */}
                <div style={styles.card}>
                    <h3 style={styles.cardTitle}>
                        <Clock size={20} style={{marginRight: 8}} />
                        System Status
                    </h3>
                    <p style={styles.status}>🟢 AI Service Connected</p>
                    <p style={styles.status}>🟢 Backend Running</p>
                    <p style={styles.status}>🟢 Database Active</p>
                </div>
            </div>

            {/* Traffic History Table */}
            <div style={styles.tableContainer}>
                <h3 style={styles.tableTitle}>Recent Traffic Logs</h3>
                <table style={styles.table}>
                    <thead>
                        <tr>
                            <th style={styles.th}>Time</th>
                            <th style={styles.th}>Lane</th>
                            <th style={styles.th}>Vehicles</th>
                            <th style={styles.th}>Signal</th>
                        </tr>
                    </thead>
                    <tbody>
                        {trafficData.slice(-5).reverse().map((log, index) => (
                            <tr key={index} style={styles.tr}>
                                <td style={styles.td}>{new Date(log.timestamp).toLocaleTimeString()}</td>
                                <td style={styles.td}>{log.laneId}</td>
                                <td style={styles.td}>{log.vehicleCount}</td>
                                <td style={styles.td}>{log.signalState}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

const styles = {
    container: {
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #0f0f23 0%, #1a1a3a 100%)',
        color: '#fff',
        fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '20px 40px',
        background: 'rgba(255,255,255,0.1)',
        backdropFilter: 'blur(10px)',
        borderBottom: '1px solid rgba(255,255,255,0.1)',
    },
    logo: {
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
    },
    headerTitle: {
        margin: 0,
        color: '#fff',
        fontSize: '24px',
        fontWeight: '600',
    },
    logoutBtn: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        padding: '10px 20px',
        background: 'linear-gradient(135deg, #ff6b6b, #ee5a5a)',
        color: 'white',
        border: 'none',
        borderRadius: '8px',
        cursor: 'pointer',
        fontSize: '14px',
        fontWeight: '500',
        transition: 'transform 0.2s',
    },
    content: {
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '24px',
        padding: '40px',
    },
    card: {
        background: 'rgba(255,255,255,0.08)',
        backdropFilter: 'blur(10px)',
        padding: '24px',
        borderRadius: '16px',
        border: '1px solid rgba(255,255,255,0.1)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
    },
    cardTitle: {
        margin: '0 0 16px 0',
        color: '#aaa',
        display: 'flex',
        alignItems: 'center',
        fontSize: '14px',
        fontWeight: '500',
        textTransform: 'uppercase',
        letterSpacing: '0.5px',
    },
    bigNumber: {
        fontSize: '56px',
        fontWeight: 'bold',
        color: '#fff',
        margin: '8px 0',
        textShadow: '0 0 20px rgba(76, 175, 80, 0.5)',
    },
    cardSubtitle: {
        color: '#888',
        margin: '8px 0 0 0',
        fontSize: '14px',
    },
    signalLight: {
    display: 'flex',
    flexDirection: 'column', // Vertical arrangement like real traffic light
    gap: '12px',
    alignItems: 'center',
    margin: '24px 0',
    },
    light: {
        width: '60px',
        height: '60px',
        borderRadius: '50%',
        background: '#333',
        border: '3px solid #555',
        transition: 'all 0.3s ease',
    },
    signalText: {
        textAlign: 'center',
        color: '#fff',
        fontSize: '36px',
        fontWeight: 'bold',
        margin: 0,
        textShadow: '0 0 10px rgba(255,255,255,0.5)',
    },
    status: {
        margin: '10px 0',
        color: '#aaa',
        fontSize: '14px',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
    },
    tableContainer: {
        margin: '0 40px 40px 40px',
        background: 'rgba(255,255,255,0.08)',
        backdropFilter: 'blur(10px)',
        padding: '24px',
        borderRadius: '16px',
        border: '1px solid rgba(255,255,255,0.1)',
    },
    tableTitle: {
        margin: '0 0 16px 0',
        color: '#fff',
        fontSize: '18px',
        fontWeight: '600',
    },
    table: {
        width: '100%',
        borderCollapse: 'collapse',
    },
    th: {
        textAlign: 'left',
        padding: '14px 12px',
        borderBottom: '1px solid rgba(255,255,255,0.1)',
        color: '#aaa',
        fontSize: '13px',
        textTransform: 'uppercase',
        letterSpacing: '0.5px',
    },
    tr: {
        borderBottom: '1px solid rgba(255,255,255,0.05)',
        transition: 'background 0.2s',
    },
    td: {
        padding: '14px 12px',
        color: '#ddd',
        fontSize: '14px',
    },
    videoContainer: {
        width: '100%',
        height: '400px',
        background: '#000',
        borderRadius: '12px',
        overflow: 'hidden',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        border: '2px solid rgba(76, 175, 80, 0.3)',
    },
    videoFeed: {
        width: '100%',
        height: '100%',
        objectFit: 'contain',
    },
    videoNote: {
        textAlign: 'center',
        color: '#888',
        fontSize: '13px',
        marginTop: '12px',
        fontStyle: 'italic',
    },

    intersectionContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '20px',
    padding: '20px',
    },
    lane: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '10px',
        padding: '15px 30px',
        background: 'rgba(255,255,255,0.05)',
        borderRadius: '12px',
        minWidth: '120px',
    },
    northLane: {
        border: '2px dashed rgba(76, 175, 80, 0.3)',
    },
    southLane: {
        border: '2px dashed rgba(255,255,255,0.1)',
    },
    trafficLight: {
        width: '40px',
        height: '40px',
        borderRadius: '50%',
        transition: 'all 0.3s ease',
    },
    intersection: {
        width: '100px',
        height: '100px',
        background: 'rgba(100,100,100,0.2)',
        borderRadius: '20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        border: '2px solid rgba(255,255,255,0.2)',
    },
    intersectionCenter: {
        width: '60px',
        height: '60px',
        background: 'rgba(76, 175, 80, 0.2)',
        borderRadius: '50%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    laneLabel: {
        color: '#aaa',
        fontSize: '12px',
        fontWeight: '500',
    },
};

export default Dashboard;
// Responsive styles for smaller screens
if (window.innerWidth < 1200) {
    styles.content = {
        ...styles.content,
        gridTemplateColumns: '1fr',
        padding: '20px',
    };
    styles.card = {
        ...styles.card,
        gridColumn: 'span 1 !important',
    };
}