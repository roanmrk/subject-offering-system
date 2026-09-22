import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import './Login.css';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 1500));
    
    setSubmitted(true);
    setLoading(false);
  };

  return (
    <div className="login-container">
      <div className="login-left">
        <div className="login-brand">
          <div className="brand-icon">
            <i className="fas fa-calendar-alt"></i>
          </div>
          <h1 className="brand-title">Scheduler</h1>
          <p className="brand-subtitle">EARIST College of Computing Studies</p>
        </div>
      </div>

      <div className="login-right">
        <div className="login-card">
          <div className="login-header">
            <h2>Reset Password</h2>
            <p>Enter your email to receive a reset link</p>
          </div>

          {!submitted ? (
            <form onSubmit={handleSubmit} className="login-form">
              <div className="form-group">
                <label>
                  <i className="fas fa-envelope"></i> Email Address
                </label>
                <div className="input-wrapper">
                  <span className="input-icon">
                    <i className="fas fa-envelope"></i>
                  </span>
                  <input
                    type="email"
                    placeholder="enter@earist.edu.ph"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <button type="submit" className="login-btn" disabled={loading}>
                {loading ? (
                  <><i className="fas fa-spinner fa-spin"></i> Sending...</>
                ) : (
                  <><i className="fas fa-paper-plane"></i> Send Reset Link</>
                )}
              </button>

              <div className="login-footer">
                <p>
                  <Link to="/login" className="forgot-link">
                    <i className="fas fa-arrow-left"></i> Back to Login
                  </Link>
                </p>
              </div>
            </form>
          ) : (
            <div className="success-message">
              <div className="success-icon">
                <i className="fas fa-check-circle"></i>
              </div>
              <h3>Email Sent!</h3>
              <p>We've sent a password reset link to <strong>{email}</strong></p>
              <Link to="/login" className="login-btn" style={{ textAlign: 'center', textDecoration: 'none' }}>
                <i className="fas fa-arrow-left"></i> Back to Login
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;