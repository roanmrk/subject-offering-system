import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api';
import PageBackground from './Background/PageBackground';
import './Dashboard.css';

const FacultyProfile = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [profile, setProfile] = useState({
    firstName: '',
    lastName: '',
    email: '',
    specialization: '',
    phone: '',
    officeHours: '',
    certifications: ''
  });
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState('');

  useEffect(() => {
    const userData = localStorage.getItem('user');
    if (userData) {
      const parsed = JSON.parse(userData);
      setUser(parsed);
      setProfile({
        firstName: parsed.firstName || '',
        lastName: parsed.lastName || '',
        email: parsed.email || '',
        specialization: parsed.specialization || '',
        phone: parsed.phone || '',
        officeHours: parsed.officeHours || '',
        certifications: parsed.certifications || ''
      });
    } else {
      navigate('/login');
    }
    setLoading(false);
  }, [navigate]);

  const handleChange = (e) => {
    setProfile({
      ...profile,
      [e.target.name]: e.target.value
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setSuccess('');
    try {
      const updatedUser = { ...user, ...profile };
      localStorage.setItem('user', JSON.stringify(updatedUser));
      setUser(updatedUser);

      setSuccess('Profile updated successfully!');
      setEditing(false);
    } catch (error) {
      console.error('Error updating profile:', error);
      alert('Failed to update profile. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setProfile({
      firstName: user?.firstName || '',
      lastName: user?.lastName || '',
      email: user?.email || '',
      specialization: user?.specialization || '',
      phone: user?.phone || '',
      officeHours: user?.officeHours || '',
      certifications: user?.certifications || ''
    });
    setEditing(false);
  };

  const handleLogout = () => {
    localStorage.removeItem('user');
    navigate('/login');
  };

  if (loading) {
    return <div className="loading-state"><div className="spinner"></div><p>Loading profile...</p></div>;
  }

  return (
    <div className="dashboard-container" style={{ position: 'relative' }}>
      <PageBackground variant="dark" />

      <div style={{ position: 'relative', zIndex: 1 }}>
        {/* Top Nav */}
        <nav className="dashboard-nav">
          <div className="nav-left">
            <Link to="/" className="nav-back">
              <i className="fas fa-arrow-left"></i> Back to Home
            </Link>
            <span className="nav-title">
              <i className="fas fa-user-circle"></i> My Profile
            </span>
          </div>
          <div className="nav-right">
            <span className="status-dot"></span>
            <span className="status-text">System Online</span>
            {user && (
              <span className="user-badge">
                <i className="fas fa-user"></i> {user.firstName || user.name} ({user.role})
              </span>
            )}
            <button className="logout-btn" onClick={handleLogout}>
              <i className="fas fa-sign-out-alt"></i> Logout
            </button>
          </div>
        </nav>

        {/* Main Content */}
        <main className="dashboard-main">
          <header className="dashboard-header">
            <h1>👤 My Profile</h1>
            <p>View and manage your personal information</p>
          </header>

          {success && (
            <div className="success-alert">
              <i className="fas fa-check-circle"></i> {success}
            </div>
          )}

          {/* Profile Card */}
          <div className="profile-card">
            <div className="profile-header">
              <div className="profile-avatar">
                <i className="fas fa-user-circle"></i>
              </div>
              <div className="profile-title">
                <h2>{profile.firstName} {profile.lastName}</h2>
                <p className="profile-role">{user?.role?.toUpperCase()}</p>
                <p className="profile-dept">College of Computing Studies - EARIST</p>
              </div>
              <div className="profile-actions">
                {!editing ? (
                  <button className="btn-primary" onClick={() => setEditing(true)}>
                    <i className="fas fa-edit"></i> Edit Profile
                  </button>
                ) : (
                  <div className="profile-actions-group">
                    <button className="btn-primary" onClick={handleSave} disabled={saving}>
                      {saving ? (
                        <><i className="fas fa-spinner fa-spin"></i> Saving...</>
                      ) : (
                        <><i className="fas fa-save"></i> Save</>
                      )}
                    </button>
                    <button className="btn-secondary" onClick={handleCancel}>
                      <i className="fas fa-times"></i> Cancel
                    </button>
                  </div>
                )}
              </div>
            </div>

            <div className="profile-body">
              <div className="profile-info-grid">
                {/* Personal Information */}
                <div className="profile-section">
                  <h3><i className="fas fa-user"></i> Personal Information</h3>
                  <div className="profile-field">
                    <label>First Name</label>
                    {editing ? (
                      <input
                        type="text"
                        name="firstName"
                        value={profile.firstName}
                        onChange={handleChange}
                        placeholder="First Name"
                      />
                    ) : (
                      <p>{profile.firstName || 'Not specified'}</p>
                    )}
                  </div>
                  <div className="profile-field">
                    <label>Last Name</label>
                    {editing ? (
                      <input
                        type="text"
                        name="lastName"
                        value={profile.lastName}
                        onChange={handleChange}
                        placeholder="Last Name"
                      />
                    ) : (
                      <p>{profile.lastName || 'Not specified'}</p>
                    )}
                  </div>
                  <div className="profile-field">
                    <label>Email</label>
                    {editing ? (
                      <input
                        type="email"
                        name="email"
                        value={profile.email}
                        onChange={handleChange}
                        placeholder="Email"
                        disabled
                      />
                    ) : (
                      <p>{profile.email}</p>
                    )}
                    <span className="field-note">Email cannot be changed</span>
                  </div>
                </div>

                {/* Professional Information */}
                <div className="profile-section">
                  <h3><i className="fas fa-briefcase"></i> Professional Information</h3>
                  <div className="profile-field">
                    <label>Specialization</label>
                    {editing ? (
                      <input
                        type="text"
                        name="specialization"
                        value={profile.specialization}
                        onChange={handleChange}
                        placeholder="e.g. Algorithms, Data Structures"
                      />
                    ) : (
                      <p>{profile.specialization || 'Not specified'}</p>
                    )}
                  </div>
                  <div className="profile-field">
                    <label>Certifications</label>
                    {editing ? (
                      <input
                        type="text"
                        name="certifications"
                        value={profile.certifications}
                        onChange={handleChange}
                        placeholder="e.g. Java Certified, Python Certified"
                      />
                    ) : (
                      <p>{profile.certifications || 'Not specified'}</p>
                    )}
                  </div>
                  <div className="profile-field">
                    <label>Phone Number</label>
                    {editing ? (
                      <input
                        type="text"
                        name="phone"
                        value={profile.phone}
                        onChange={handleChange}
                        placeholder="e.g. 0912-345-6789"
                      />
                    ) : (
                      <p>{profile.phone || 'Not specified'}</p>
                    )}
                  </div>
                  <div className="profile-field">
                    <label>Office Hours</label>
                    {editing ? (
                      <input
                        type="text"
                        name="officeHours"
                        value={profile.officeHours}
                        onChange={handleChange}
                        placeholder="e.g. Mon-Fri 9:00 AM - 5:00 PM"
                      />
                    ) : (
                      <p>{profile.officeHours || 'Not specified'}</p>
                    )}
                  </div>
                </div>
              </div>

              {/* Account Info */}
              <div className="profile-section account-info">
                <h3><i className="fas fa-shield-alt"></i> Account Information</h3>
                <div className="account-details">
                  <div className="account-field">
                    <span className="account-label">Role</span>
                    <span className="account-value role-badge faculty">{user?.role}</span>
                  </div>
                  <div className="account-field">
                    <span className="account-label">Department</span>
                    <span className="account-value">CCS - College of Computing Studies</span>
                  </div>
                  <div className="account-field">
                    <span className="account-label">Status</span>
                    <span className="account-value status-active">● Active</span>
                  </div>
                </div>
              </div>

              {/* Restricted Access Note */}
              <div className="restricted-note">
                <i className="fas fa-lock"></i>
                <div>
                  <strong>Limited Access</strong>
                  <p>You can only edit your personal and professional information. For changes to your schedule or courses, please contact the department chair or admin.</p>
                </div>
              </div>
            </div>
          </div>

          {/* Quick Links */}
          <div className="quick-links">
            <Link to="/faculty-dashboard" className="quick-link">
              <i className="fas fa-calendar-alt"></i>
              <span>My Schedule</span>
            </Link>
            <Link to="/" className="quick-link">
              <i className="fas fa-home"></i>
              <span>Home</span>
            </Link>
            <button className="quick-link" onClick={handleLogout}>
              <i className="fas fa-sign-out-alt"></i>
              <span>Logout</span>
            </button>
          </div>
        </main>
      </div>
    </div>
  );
};

export default FacultyProfile;