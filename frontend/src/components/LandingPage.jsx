import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import PageBackground from './Background/PageBackground';
import './LandingPage.css';

const LandingPage = () => {
  const [isVisible, setIsVisible] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    setIsVisible(true);
  }, []);

  const goToLogin = () => navigate('/login');
  const goToHome = () => window.scrollTo({ top: 0, behavior: 'smooth' });
  const scrollToFeatures = () => document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
  const scrollToAbout = () => document.getElementById('about')?.scrollIntoView({ behavior: 'smooth' });
  const scrollToContact = () => document.getElementById('contact')?.scrollIntoView({ behavior: 'smooth' });

  return (
    <div className="landing-container" style={{ position: 'relative', background: '#0a0a0a' }}>
      <PageBackground variant="dark" />

      <div style={{ position: 'relative', zIndex: 1, color: '#fcf9f2' }}>
        <nav className="landing-nav" style={{
          background: 'rgba(10, 10, 10, 0.6)',
          backdropFilter: 'blur(12px)',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)'
        }}>
          <div className="nav-inner">
            <div className="nav-logo">
              <i className="fas fa-calendar-alt logo-icon" style={{ color: '#fcf9f2' }}></i>
              <span className="logo-text" style={{ color: '#fcf9f2' }}>Scheduler</span>
              <span className="logo-badge" style={{ background: 'rgba(255, 255, 255, 0.1)', color: '#d0d0d0' }}>
                EARIST CCS
              </span>
            </div>
            <div className="nav-links">
              <button className="nav-link-btn" onClick={goToHome} style={{ color: '#d0d0d0' }}>
                <i className="fas fa-home"></i> Home
              </button>
              <button className="nav-link-btn" onClick={scrollToFeatures} style={{ color: '#d0d0d0' }}>Features</button>
              <button className="nav-link-btn" onClick={scrollToAbout} style={{ color: '#d0d0d0' }}>About</button>
              <button className="nav-link-btn" onClick={scrollToContact} style={{ color: '#d0d0d0' }}>Contact</button>
              <button className="nav-cta" onClick={goToLogin} style={{ background: '#fcf9f2', color: '#1a1a1a' }}>
                <i className="fas fa-sign-in-alt"></i> Sign In
              </button>
            </div>
          </div>
        </nav>

        <section className="hero-section">
          <div className="hero-grid">
            <div className={`hero-content ${isVisible ? 'fade-in-left' : ''}`}>
              <div className="hero-badge" style={{ background: 'rgba(255, 255, 255, 0.08)', color: '#d0d0d0' }}>
                <span className="badge-dot"></span>
                v2.0 — Academic Scheduling System
              </div>

              <h1 className="hero-title" style={{ color: '#fcf9f2' }}>
                Subject Offering &{' '}
                <span className="outline-text" style={{
                  color: 'transparent',
                  WebkitTextStroke: '2px #fcf9f2',
                  textStroke: '2px #fcf9f2'
                }}>Faculty Loading</span>
              </h1>

              <p className="hero-subtitle" style={{ color: '#a3a3a3' }}>
                Generate conflict-free academic schedules using Decision Tree, Graph Coloring,
                and Genetic Algorithms. Built for EARIST College of Computing Studies.
              </p>

              <div className="hero-stats">
                <div className="stat-item">
                  <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#fcf9f2" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginBottom: '4px' }}>
                    <circle cx="12" cy="12" r="10"/>
                    <path d="m16 9-5.5 5.5L8 12"/>
                  </svg>
                  <span className="stat-label" style={{ color: '#a3a3a3' }}>Automated</span>
                </div>
                <div className="stat-divider" style={{ background: 'rgba(255, 255, 255, 0.15)' }}></div>
                <div className="stat-item">
                  <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#fcf9f2" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginBottom: '4px' }}>
                    <circle cx="12" cy="12" r="10"/>
                    <path d="m16 9-5.5 5.5L8 12"/>
                  </svg>
                  <span className="stat-label" style={{ color: '#a3a3a3' }}>Optimized</span>
                </div>
                <div className="stat-divider" style={{ background: 'rgba(255, 255, 255, 0.15)' }}></div>
                <div className="stat-item">
                  <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#fcf9f2" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginBottom: '4px' }}>
                    <circle cx="12" cy="12" r="10"/>
                    <path d="m16 9-5.5 5.5L8 12"/>
                  </svg>
                  <span className="stat-label" style={{ color: '#a3a3a3' }}>Validated</span>
                </div>
              </div>
            </div>

            <div className={`hero-preview ${isVisible ? 'fade-in-right' : ''}`}>
              <div className="preview-card" style={{
                position: 'relative',
                overflow: 'hidden',
                background: 'rgba(20, 20, 20, 0.75)',
                backdropFilter: 'blur(12px)',
                border: '1px solid rgba(255, 255, 255, 0.1)'
              }}>
                <div className="preview-header" style={{ background: 'rgba(10, 10, 10, 0.6)', borderBottom: '1px solid rgba(255, 255, 255, 0.08)' }}>
                  <div className="preview-dots">
                    <span className="dot red"></span>
                    <span className="dot yellow"></span>
                    <span className="dot green"></span>
                  </div>
                  <span className="preview-title" style={{ color: '#d0d0d0' }}>
                    <i className="fas fa-chart-simple"></i> Admin Dashboard
                  </span>
                </div>

                <div className="preview-body">
                  <div style={{
                    textAlign: 'center',
                    padding: '48px 24px',
                    background: 'rgba(255, 255, 255, 0.04)',
                    borderRadius: '12px',
                    marginBottom: '16px',
                    border: '1px solid rgba(255, 255, 255, 0.08)'
                  }}>
                    <i className="fas fa-calendar-check" style={{ fontSize: '56px', color: '#fcf9f2', marginBottom: '16px', display: 'block' }}></i>
                    <p style={{ fontSize: '16px', fontWeight: '700', color: '#fcf9f2', marginBottom: '8px', letterSpacing: '-0.3px' }}>
                      Intelligent Optimization Engine
                    </p>
                    <p style={{ fontSize: '13px', color: '#a3a3a3', lineHeight: '1.6' }}>
                      Decision Tree · Graph Coloring · Genetic Algorithm
                    </p>
                  </div>

                  <div className="preview-status">
                    <span className="status-success" style={{ color: '#4caf50' }}>
                      <i className="fas fa-check-circle"></i> System Ready
                    </span>
                    <span className="status-time" style={{ color: '#a3a3a3' }}>
                      <i className="fas fa-shield-alt"></i> Conflict-Free
                    </span>
                  </div>

                  <button className="preview-generate-btn" onClick={goToLogin} style={{ background: '#fcf9f2', color: '#1a1a1a' }}>
                    <i className="fas fa-play"></i> Get Started
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="features-section" id="features" style={{ background: 'transparent' }}>
          <div className="features-inner">
            <div className="features-header">
              <span className="features-badge" style={{ background: 'rgba(255, 255, 255, 0.08)', color: '#d0d0d0' }}>Features</span>
              <h2 style={{ color: '#fcf9f2' }}>Built for Academic Excellence</h2>
              <p style={{ color: '#a3a3a3' }}>Three powerful algorithms working together to create optimal schedules</p>
            </div>
            <div className="features-grid">
              <div className="feature-card" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <div className="feature-icon" style={{ color: '#fcf9f2' }}>
                  <i className="fas fa-code-branch"></i>
                </div>
                <h3 style={{ color: '#fcf9f2' }}>Decision Tree</h3>
                <p style={{ color: '#a3a3a3' }}>Intelligently matches faculty expertise with subject requirements and evaluates curriculum prerequisites.</p>
              </div>
              <div className="feature-card" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <div className="feature-icon" style={{ color: '#fcf9f2' }}>
                  <i className="fas fa-palette"></i>
                </div>
                <h3 style={{ color: '#fcf9f2' }}>Graph Coloring</h3>
                <p style={{ color: '#a3a3a3' }}>Eliminates scheduling conflicts by assigning optimal time slots to subject sections.</p>
              </div>
              <div className="feature-card" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <div className="feature-icon" style={{ color: '#fcf9f2' }}>
                  <i className="fas fa-dna"></i>
                </div>
                <h3 style={{ color: '#fcf9f2' }}>Genetic Algorithm</h3>
                <p style={{ color: '#a3a3a3' }}>Optimizes resource utilization and faculty workload distribution through evolutionary computation.</p>
              </div>
            </div>
          </div>
        </section>

        <section className="about-section" id="about" style={{ background: 'transparent' }}>
          <div className="about-inner">
            <div className="about-header">
              <span className="about-badge" style={{ background: 'rgba(255, 255, 255, 0.08)', color: '#d0d0d0' }}>About</span>
              <h2 style={{ color: '#fcf9f2' }}>Empowering EARIST with Smart Scheduling</h2>
              <p style={{ color: '#a3a3a3' }}>Learn more about our mission and technology</p>
            </div>
            <div className="about-content">
              <div className="about-text">
                <h3 style={{ color: '#fcf9f2' }}>Our Mission</h3>
                <p style={{ color: '#a3a3a3' }}>
                  The Subject Offering and Faculty Loading System is designed to revolutionize
                  academic scheduling at EARIST College of Computing Studies. By combining
                  cutting-edge algorithms with intuitive design, we aim to eliminate scheduling
                  conflicts, optimize resource utilization, and enhance the overall educational
                  experience for both students and faculty.
                </p>
                <br />
                <h3 style={{ color: '#fcf9f2' }}>Our Technology</h3>
                <p style={{ color: '#a3a3a3' }}>
                  Built with a hybrid approach, our system leverages <strong style={{ color: '#fcf9f2' }}>Decision Trees</strong>
                  {' '}for intelligent faculty matching, <strong style={{ color: '#fcf9f2' }}>Graph Coloring</strong> for conflict-free
                  scheduling, and <strong style={{ color: '#fcf9f2' }}>Genetic Algorithms</strong> for optimal resource allocation.
                  This powerful combination ensures that every schedule generated is efficient,
                  balanced, and conflict-free.
                </p>
                <br />
                <div className="about-stats">
                  <div className="about-stat">
                    <span className="about-stat-number" style={{ color: '#fcf9f2' }}>3</span>
                    <span className="about-stat-label" style={{ color: '#a3a3a3' }}>Algorithms</span>
                  </div>
                  <div className="about-stat">
                    <span className="about-stat-number" style={{ color: '#fcf9f2' }}>100%</span>
                    <span className="about-stat-label" style={{ color: '#a3a3a3' }}>Conflict-Free</span>
                  </div>
                  <div className="about-stat">
                    <span className="about-stat-number" style={{ color: '#fcf9f2' }}>24/7</span>
                    <span className="about-stat-label" style={{ color: '#a3a3a3' }}>Availability</span>
                  </div>
                </div>
              </div>
              <div className="about-image">
                <div className="about-icon-large" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                  <i className="fas fa-university" style={{ color: '#fcf9f2' }}></i>
                </div>
                <div className="about-icon-large" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                  <i className="fas fa-chart-line" style={{ color: '#fcf9f2' }}></i>
                </div>
                <div className="about-icon-large" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                  <i className="fas fa-users" style={{ color: '#fcf9f2' }}></i>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="contact-section" id="contact" style={{ background: 'transparent' }}>
          <div className="contact-inner">
            <div className="contact-header">
              <span className="contact-badge" style={{ background: 'rgba(255, 255, 255, 0.08)', color: '#d0d0d0' }}>Contact</span>
              <h2 style={{ color: '#fcf9f2' }}>Get in Touch</h2>
              <p style={{ color: '#a3a3a3' }}>Have questions? Reach out to us anytime.</p>
            </div>
            <div className="contact-grid">
              <div className="contact-card" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <div className="contact-icon" style={{ color: '#fcf9f2' }}>
                  <i className="fas fa-envelope"></i>
                </div>
                <h3 style={{ color: '#fcf9f2' }}>Email</h3>
                <p style={{ color: '#a3a3a3' }}>ccs@earist.edu.ph</p>
                <p style={{ color: '#a3a3a3' }}>scheduler@earist.edu.ph</p>
              </div>
              <div className="contact-card" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <div className="contact-icon" style={{ color: '#fcf9f2' }}>
                  <i className="fas fa-phone"></i>
                </div>
                <h3 style={{ color: '#fcf9f2' }}>Phone</h3>
                <p style={{ color: '#a3a3a3' }}>(02) 1234-5678</p>
                <p style={{ color: '#a3a3a3' }}>Mon - Fri, 8AM - 5PM</p>
              </div>
              <div className="contact-card" style={{ background: 'rgba(20, 20, 20, 0.75)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <div className="contact-icon" style={{ color: '#fcf9f2' }}>
                  <i className="fas fa-map-marker-alt"></i>
                </div>
                <h3 style={{ color: '#fcf9f2' }}>Location</h3>
                <p style={{ color: '#a3a3a3' }}>EARIST Manila</p>
                <p style={{ color: '#a3a3a3' }}>Nagtahan, Sampaloc, Manila</p>
              </div>
            </div>
          </div>
        </section>

        <footer className="landing-footer" style={{
          background: 'rgba(10, 10, 10, 0.9)',
          borderTop: '1px solid rgba(255, 255, 255, 0.08)'
        }}>
          <div className="footer-inner">
            <div className="footer-brand">
              <i className="fas fa-calendar-alt logo-icon" style={{ color: '#fcf9f2' }}></i>
              <span className="logo-text" style={{ color: '#fcf9f2' }}>Scheduler</span>
              <p style={{ color: '#6b6b6b' }}>© 2026 EARIST — College of Computing Studies</p>
            </div>
            <div className="footer-links">
              <button className="footer-link-btn" onClick={goToHome} style={{ color: '#a3a3a3' }}>
                <i className="fas fa-home"></i> Home
              </button>
              <button className="footer-link-btn" onClick={scrollToFeatures} style={{ color: '#a3a3a3' }}>Features</button>
              <button className="footer-link-btn" onClick={scrollToAbout} style={{ color: '#a3a3a3' }}>About</button>
              <button className="footer-link-btn" onClick={scrollToContact} style={{ color: '#a3a3a3' }}>Contact</button>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
};

export default LandingPage;