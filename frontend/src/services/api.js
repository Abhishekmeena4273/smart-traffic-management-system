import axios from 'axios';

const API_BASE = 'http://localhost:8080/api';

export const trafficAPI = {
    // Get all traffic logs
    getHistory: () => axios.get(`${API_BASE}/traffic/history`),
    
    // Send new traffic data (from AI)
    updateTraffic: (data) => axios.post(`${API_BASE}/traffic/update`, data),
    
    // Login
    login: (credentials) => axios.post(`${API_BASE}/auth/login`, credentials),
    
    // Register
    register: (userData) => axios.post(`${API_BASE}/auth/register`, userData),
};