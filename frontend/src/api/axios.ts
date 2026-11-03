import axios from 'axios';

const API_BASE_URL = 'http://localhost:8085/api'; // API Gateway port

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const tokenExpiresAt = localStorage.getItem('tokenExpiresAt');

    // If token exists but is expired, clear auth session immediately
    if (token && tokenExpiresAt) {
      const exp = parseInt(tokenExpiresAt, 10);
      if (!isNaN(exp) && Date.now() >= exp) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('tokenExpiresAt');
        if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.href = '/login?expired=true';
        }
        return Promise.reject(new Error('Session expired. Please log in again.'));
      }
    }

    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status;

      if (status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('tokenExpiresAt');
        if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      } else if (status === 403) {
        const serverMsg = error.response.data?.message;
        error.userMessage = serverMsg && serverMsg !== 'Forbidden' && serverMsg !== 'Insufficient privileges'
          ? serverMsg
          : 'Access Denied: You do not have permission to access this resource.';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
