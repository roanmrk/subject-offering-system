import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api';
import PageBackground from './Background/PageBackground';
import './Login.css';

const Login = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await api.post('/api/admin/users/login', {
        email,
        password
      });

      if (response.data.success) {
        const userData = response.data.user;
        const token = response.data.token;

        localStorage.setItem('user', JSON.stringify(userData));
        localStorage.setItem('token', token);

        if (userData.role === 'admin') navigate('/admin-dashboard');
        else if (userData.role === 'faculty') navigate('/faculty-dashboard');
        else navigate('/dashboard');
      } else {
        setError(response.data.message || 'Invalid credentials');
      }
    } catch (err) {
      if (err.response?.status === 401) {
        setError('Invalid email or password. Please try again.');
      } else {
        setError('Unable to reach server. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      {/* GhostFibers full-page background */}
      <PageBackground variant="dark" />

      {/* Content — flex split into left and right */}
      <div style={{
        position: 'relative',
        zIndex: 1,
        display: 'flex',
        width: '100%',
        minHeight: '100vh'
      }}>

        {/* LEFT — Branding panel (transparent, fibers show through) */}
        <div className="login-left">
          <div className="login-brand">
            <div className="brand-icon">
              <i className="fas fa-calendar-alt"></i>
            </div>
            <h1 className="brand-title">Scheduler</h1>
            <p className="brand-subtitle">EARIST College of Computing Studies</p>
            <div className="brand-features">
              <div className="brand-feature">
                <span className="feature-icon"><i className="fas fa-check-circle"></i></span>
                <span>Conflict-Free Scheduling</span>
              </div>
              <div className="brand-feature">
                <span className="feature-icon"><i className="fas fa-project-diagram"></i></span>
                <span>Graph Coloring Engine</span>
              </div>
              <div className="brand-feature">
                <span className="feature-icon"><i className="fas fa-dna"></i></span>
                <span>Genetic Optimization</span>
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT — Login form (card only, transparent panel) */}
        <div className="login-right">
          <div className="login-card">
            <div className="login-header">
              <h2>Welcome Back</h2>
              <p>Sign in to access the scheduling system</p>
            </div>

            <form onSubmit={handleLogin} className="login-form">
              <div className="form-group">
                <label htmlFor="login-email">
                  <i className="fas fa-envelope"></i> Email Address
                </label>
                <div className="input-wrapper">
                  <span className="input-icon"><i className="fas fa-envelope"></i></span>
                  <input
                    id="login-email"
                    type="email"
                    placeholder="enter@earist.edu.ph"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="login-password">
                  <i className="fas fa-lock"></i> Password
                </label>
                <div className="input-wrapper">
                  <span className="input-icon"><i className="fas fa-lock"></i></span>
                  <input
                    id="login-password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Enter your password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                  <button
                    type="button"
                    className="toggle-password"
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    <i className={showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'}></i>
                  </button>
                </div>
              </div>

              <div className="form-options">
                <label className="remember-me">
                  <input type="checkbox" /> Remember me
                </label>
                <Link to="/forgot-password" className="forgot-link">Forgot password?</Link>
              </div>

              {error && (
                <div className="login-error">
                  <i className="fas fa-exclamation-circle"></i> {error}
                </div>
              )}

              <button type="submit" className="login-btn" disabled={loading}>
                {loading ? (
                  <><i className="fas fa-spinner fa-spin"></i> Signing in...</>
                ) : (
                  <><i className="fas fa-sign-in-alt"></i> Sign In</>
                )}
              </button>
            </form>

            <div className="demo-credentials">
              <p><i className="fas fa-key"></i> Demo Credentials:</p>
              <div className="credential-grid">
                <div className="credential-item">
                  <span className="cred-role">Admin</span>
                  <span className="cred-email">admin@earist.edu.ph</span>
                  <span className="cred-pass">admin123</span>
                </div>
                <div className="credential-item">
                  <span className="cred-role">Faculty</span>
                  <span className="cred-email">faculty@earist.edu.ph</span>
                  <span className="cred-pass">faculty123</span>
                </div>
              </div>
            </div>

            <div className="login-footer">
              <p>© 2026 EARIST — College of Computing Studies</p>
            </div>
          </div>
        </div>

      </div>
    </div>
  );
};

export default Login;