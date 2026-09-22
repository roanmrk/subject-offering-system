import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api';
import PageBackground from './Background/PageBackground';
import './Dashboard.css';

const FacultyDashboard = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [mySchedule, setMySchedule] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editingProfile, setEditingProfile] = useState(false);
  const [profile, setProfile] = useState({
    firstName: '', lastName: '', email: '', specialization: '', phone: ''
  });

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
        phone: parsed.phone || ''
      });
    } else {
      navigate('/login');
    }
    fetchMySchedule();
  }, []);

  const fetchMySchedule = async () => {
    setLoading(true);
    try {
      const userData = JSON.parse(localStorage.getItem('user'));
      if (!userData) { navigate('/login'); return; }

      const facultyName = userData.name || `${userData.firstName || ''} ${userData.lastName || ''}`.trim();
      if (!facultyName) { setMySchedule([]); return; }

      const response = await api.get('/api/schedule/view?semester=1st%20Semester&academicYear=2026-2027');
      const mySchedule = (response.data.entries || []).filter(entry => entry.faculty === facultyName);
      setMySchedule(mySchedule);
    } catch (error) {
      console.error('Error fetching schedule:', error);
      setMySchedule([]);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('user');
    navigate('/login');
  };

  const handleProfileUpdate = async () => {
    try {
      await api.put(`/api/faculty/profile/${user?.id}`, profile);
      alert('Profile updated successfully!');
      setEditingProfile(false);
      const updatedUser = { ...user, ...profile };
      localStorage.setItem('user', JSON.stringify(updatedUser));
      setUser(updatedUser);
    } catch (error) {
      console.error('Error updating profile:', error);
      alert('Failed to update profile');
    }
  };

  const totalHours = mySchedule.reduce((sum) => sum + 3, 0);
  const totalStudents = mySchedule.reduce((sum, cls) => sum + (cls.students || 0), 0);

  if (loading) {
    return <div className="loading-state"><div className="spinner"></div><p>Loading your schedule...</p></div>;
  }

  return (
    <div className="dashboard-container" style={{ position: 'relative' }}>
      <PageBackground variant="dark" />

      <div style={{ position: 'relative', zIndex: 1 }}>
        <nav className="dashboard-nav">
          <div className="nav-left">
            <Link to="/" className="nav-back">
              <i className="fas fa-arrow-left"></i> Back to Home
            </Link>
            <span className="nav-title">
              <i className="fas fa-chalkboard-teacher"></i> Faculty Dashboard
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

        <main className="dashboard-main">
          <header className="dashboard-header">
            <h1>👋 Welcome, {user?.firstName || user?.name || 'Faculty'}!</h1>
            <p>Your teaching schedule for the current semester</p>
          </header>

          <div className="stats-grid" style={{ marginBottom: '28px' }}>
            <div className="stat-card">
              <div className="stat-icon"><i className="fas fa-book"></i></div>
              <div>
                <div className="stat-label">Courses</div>
                <div className="stat-value">{mySchedule.length}</div>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-icon"><i className="fas fa-users"></i></div>
              <div>
                <div className="stat-label">Total Students</div>
                <div className="stat-value">{totalStudents}</div>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-icon"><i className="fas fa-clock"></i></div>
              <div>
                <div className="stat-label">Teaching Hours</div>
                <div className="stat-value">{totalHours} hrs</div>
              </div>
            </div>
            <div className="stat-card" style={{ borderColor: '#4caf50' }}>
              <div className="stat-icon"><i className="fas fa-check-circle" style={{ color: '#81c784' }}></i></div>
              <div>
                <div className="stat-label">Status</div>
                <div className="stat-value" style={{ color: '#81c784' }}>Active</div>
              </div>
            </div>
          </div>

          <div className="table-card">
            <div className="table-header">
              <h2><i className="fas fa-table"></i> My Teaching Schedule</h2>
              <span style={{ fontSize: '12px', color: '#a3a3a3' }}>
                <i className="fas fa-info-circle"></i> View only - Contact admin for changes
              </span>
            </div>
            <div className="table-container">
              <table className="schedule-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Course Code</th>
                    <th>Course Name</th>
                    <th>Section</th>
                    <th>Day</th>
                    <th>Time</th>
                    <th>Room</th>
                    <th>Students</th>
                  </tr>
                </thead>
                <tbody>
                  {mySchedule.length === 0 ? (
                    <tr>
                      <td colSpan="8" style={{ textAlign: 'center', padding: '40px', color: '#a3a3a3' }}>
                        No classes assigned yet.
                      </td>
                    </tr>
                  ) : (
                    mySchedule.map((entry, index) => (
                      <tr key={entry.id || index}>
                        <td>{index + 1}</td>
                        <td><span className="course-code">{entry.courseCode}</span></td>
                        <td>{entry.courseName}</td>
                        <td>{entry.section}</td>
                        <td>{entry.day}</td>
                        <td>{entry.time}</td>
                        <td>{entry.room}</td>
                        <td>{entry.students || 0}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              <span className="table-footer-text">
                <i className="fas fa-info-circle"></i> Total: {mySchedule.length} classes • {totalHours} hours • {totalStudents} students
              </span>
            </div>
          </div>

          <div className="table-card" style={{ marginTop: '24px' }}>
            <div className="table-header">
              <h2><i className="fas fa-user"></i> My Profile</h2>
              {!editingProfile ? (
                <button className="btn-secondary" onClick={() => setEditingProfile(true)}>
                  <i className="fas fa-edit"></i> Edit Profile
                </button>
              ) : (
                <button className="btn-secondary" onClick={() => setEditingProfile(false)}>
                  <i className="fas fa-times"></i> Cancel
                </button>
              )}
            </div>
            <div style={{ padding: '20px' }}>
              {!editingProfile ? (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div><strong>Name:</strong> {user?.firstName || user?.name || 'N/A'} {user?.lastName || ''}</div>
                  <div><strong>Email:</strong> {user?.email || 'N/A'}</div>
                  <div><strong>Role:</strong> {user?.role || 'Faculty'}</div>
                  <div><strong>Specialization:</strong> {user?.specialization || 'Not specified'}</div>
                  <div><strong>Department:</strong> CCS</div>
                  <div><strong>Phone:</strong> {user?.phone || 'Not specified'}</div>
                </div>
              ) : (
                <div className="admin-form">
                  <input type="text" placeholder="First Name" value={profile.firstName} onChange={(e) => setProfile({...profile, firstName: e.target.value})} />
                  <input type="text" placeholder="Last Name" value={profile.lastName} onChange={(e) => setProfile({...profile, lastName: e.target.value})} />
                  <input type="email" placeholder="Email" value={profile.email} onChange={(e) => setProfile({...profile, email: e.target.value})} />
                  <input type="text" placeholder="Specialization" value={profile.specialization} onChange={(e) => setProfile({...profile, specialization: e.target.value})} />
                  <input type="text" placeholder="Phone" value={profile.phone} onChange={(e) => setProfile({...profile, phone: e.target.value})} />
                  <button className="btn-primary" onClick={handleProfileUpdate}>
                    <i className="fas fa-save"></i> Save Profile
                  </button>
                </div>
              )}
              <p style={{ marginTop: '12px', fontSize: '13px', color: '#a3a3a3' }}>
                <i className="fas fa-info-circle"></i> For major changes (schedule, courses), please contact the admin.
              </p>
            </div>
          </div>

          <div style={{
            marginTop: '24px', padding: '16px 20px',
            background: 'rgba(255, 179, 0, 0.1)', borderRadius: '8px',
            border: '1px solid rgba(255, 179, 0, 0.3)',
            display: 'flex', alignItems: 'center', gap: '12px'
          }}>
            <i className="fas fa-lock" style={{ color: '#ffb300', fontSize: '20px' }}></i>
            <div>
              <strong style={{ color: '#ffb300' }}>Access Restricted</strong>
              <p style={{ margin: '0', fontSize: '13px', color: '#a3a3a3' }}>
                You have view-only access to your schedule. To request changes, please contact the department chair or admin.
              </p>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};

export default FacultyDashboard;