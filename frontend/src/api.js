import axios from 'axios';

// ============================================================
// BASE URL CONFIGURATION
// ============================================================
// In DEVELOPMENT (npm start on localhost:3000):
//   → React runs on 3000, Spring Boot runs on 8080
//   → We must call http://localhost:8080 explicitly
//
// In PRODUCTION (bundled inside Spring Boot JAR):
//   → Both frontend and backend share the SAME origin
//   → We use window.location.origin so it works on any domain
// ============================================================

const getBaseURL = () => {
  if (window.location.port === '3000') {
    return 'http://localhost:8080';
  }
  return window.location.origin;
};

const api = axios.create({
  baseURL: getBaseURL(),
});

// ============================================================
// REQUEST INTERCEPTOR — Attach JWT token
// ============================================================
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ============================================================
// RESPONSE INTERCEPTOR — Handle 401 Unauthorized
// ============================================================
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('user');
      localStorage.removeItem('token');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;