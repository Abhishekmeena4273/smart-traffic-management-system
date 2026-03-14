import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { trafficAPI } from '../services/api';
import { LogIn, Car } from 'lucide-react';

function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        try {
            // For Mock 1, we'll do a simple login (no auth backend yet)
            if (username && password) {
                localStorage.setItem('user', username);
                navigate('/dashboard');
            } else {
                setError('Please enter username and password');
            }
        } catch (err) {
            setError('Login failed');
        }
    };

    return (
        <div style={styles.container}>
            <div style={styles.card}>
                <div style={styles.header}>
                    <Car size={48} color="#4CAF50" />
                    <h1 style={styles.title}>Smart Traffic</h1>
                    <p style={styles.subtitle}>Management System</p>
                </div>
                
                <form onSubmit={handleLogin} style={styles.form}>
                    <input
                        type="text"
                        placeholder="Username"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        style={styles.input}
                    />
                    <input
                        type="password"
                        placeholder="Password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        style={styles.input}
                    />
                    {error && <p style={styles.error}>{error}</p>}
                    <button type="submit" style={styles.button}>
                        <LogIn size={20} style={{marginRight: 8}} />
                        Login
                    </button>
                </form>
                
                <p style={styles.hint}>Demo: Use any username/password</p>
            </div>
        </div>
    );
}

const styles = {
    container: {
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)',
    },
    card: {
        background: 'white',
        padding: '40px',
        borderRadius: '16px',
        boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
        width: '400px',
    },
    header: {
        textAlign: 'center',
        marginBottom: '30px',
    },
    title: {
        margin: '10px 0',
        color: '#1a1a2e',
    },
    subtitle: {
        color: '#666',
        margin: 0,
    },
    form: {
        display: 'flex',
        flexDirection: 'column',
        gap: '15px',
    },
    input: {
        padding: '12px 16px',
        border: '2px solid #e0e0e0',
        borderRadius: '8px',
        fontSize: '16px',
    },
    button: {
        padding: '14px',
        background: '#4CAF50',
        color: 'white',
        border: 'none',
        borderRadius: '8px',
        fontSize: '16px',
        fontWeight: 'bold',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    error: {
        color: '#f44336',
        fontSize: '14px',
        margin: 0,
    },
    hint: {
        textAlign: 'center',
        color: '#999',
        fontSize: '12px',
        marginTop: '20px',
    },
};

export default Login;