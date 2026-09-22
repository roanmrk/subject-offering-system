import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api';
import PageBackground from './Background/PageBackground';
import './Dashboard.css';

const FacultyTimetable = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [schedule, setSchedule] = useState([]);
  const [loading, setLoading] = useState(true);
  const [timeSlots, setTimeSlots] = useState([]);

  const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  useEffect(() => {
    const userData = localStorage.getItem('user');
    if (userData) {
      setUser(JSON.parse(userData));
    } else {
      navigate('/login');
    }
    fetchTimeSlots();
    fetchMySchedule();
  }, [navigate]);

  const fetchTimeSlots = async () => {
    try {
      const response = await api.get('/api/admin/timeslots');
      const uniqueTimes = [...new Set(
        response.data.map(t => (t.startTime || '').substring(0, 5))
      )].filter(t => t).sort();

      if (uniqueTimes.length > 0) {
        setTimeSlots(uniqueTimes);
      } else {
        setTimeSlots(['07:00', '08:00', '09:00', '10:00', '11:00',
                     '13:00', '14:00', '15:00', '16:00']);
      }
    } catch (error) {
      console.error('Error fetching timeslots:', error);
      setTimeSlots(['07:00', '08:00', '09:00', '10:00', '11:00',
                   '13:00', '14:00', '15:00', '16:00']);
    }
  };

  const fetchMySchedule = async () => {
    setLoading(true);
    try {
      const userData = JSON.parse(localStorage.getItem('user'));
      const facultyName = `${userData.firstName || ''} ${userData.lastName || ''}`.trim()
        || userData.name || '';

      const response = await api.get(
        '/api/schedule/view?semester=1st%20Semester&academicYear=2026-2027'
      );

      const mySchedule = (response.data.entries || []).filter(
        entry => entry.faculty === facultyName
      );

      setSchedule(mySchedule);
    } catch (error) {
      console.error('Error fetching schedule:', error);
      setSchedule([]);
    } finally {
      setLoading(false);
    }
  };

  const getScheduleAt = (day, time) => {
    return schedule.find(entry => {
      if (entry.day !== day) return false;
      const startTime = (entry.time || '').split(' - ')[0].trim().substring(0, 5);
      return startTime === time;
    });
  };

  const handleLogout = () => {
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    navigate('/login');
  };

  if (loading) {
    return <div className="loading-state"><div className="spinner"></div><p>Loading timetable...</p></div>;
  }

  return (
    <div className="dashboard-container" style={{ position: 'relative' }}>
      <PageBackground variant="dark" />

      <div style={{ position: 'relative', zIndex: 1 }}>
        <nav className="dashboard-nav">
          <div className="nav-left">
            <Link to="/faculty-dashboard" className="nav-back">
              <i className="fas fa-arrow-left"></i> Back to Dashboard
            </Link>
            <span className="nav-title">
              <i className="fas fa-calendar-week"></i> My Timetable
            </span>
          </div>
          <div className="nav-right">
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
            <h1>📅 Weekly Timetable</h1>
            <p>Your teaching schedule at a glance</p>
          </header>

          <div className="table-card" style={{ overflowX: 'auto', marginBottom: '24px' }}>
            <div className="table-header">
              <h2><i className="fas fa-calendar-alt"></i> Weekly View</h2>
            </div>
            <div style={{ padding: '16px' }}>
              <table style={{
                width: '100%', borderCollapse: 'collapse',
                background: 'rgba(20, 20, 20, 0.5)', borderRadius: '12px', overflow: 'hidden'
              }}>
                <thead>
                  <tr>
                    <th style={{
                      padding: '12px', background: 'rgba(10, 10, 10, 0.9)', color: '#fcf9f2',
                      border: '1px solid rgba(255, 255, 255, 0.1)', minWidth: '80px'
                    }}>
                      Time
                    </th>
                    {days.map(day => (
                      <th key={day} style={{
                        padding: '12px', background: 'rgba(10, 10, 10, 0.9)', color: '#fcf9f2',
                        border: '1px solid rgba(255, 255, 255, 0.1)', textAlign: 'center', minWidth: '110px'
                      }}>
                        {day}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {timeSlots.map(time => (
                    <tr key={time}>
                      <td style={{
                        padding: '12px', background: 'rgba(255, 255, 255, 0.04)',
                        border: '1px solid rgba(255, 255, 255, 0.08)', fontWeight: '600',
                        textAlign: 'center', color: '#fcf9f2'
                      }}>
                        {time}
                      </td>
                      {days.map(day => {
                        const entry = getScheduleAt(day, time);
                        return (
                          <td key={`${day}-${time}`} style={{
                            padding: '6px', border: '1px solid rgba(255, 255, 255, 0.08)',
                            background: entry ? 'rgba(100, 181, 246, 0.1)' : 'transparent',
                            textAlign: 'center', fontSize: '11px', verticalAlign: 'middle'
                          }}>
                            {entry ? (
                              <div style={{
                                background: 'linear-gradient(135deg, #1976D2, #0D47A1)', color: '#fff',
                                padding: '6px', borderRadius: '6px',
                                fontSize: '10px', fontWeight: '500'
                              }}>
                                <div style={{ fontWeight: 'bold', marginBottom: '2px' }}>
                                  {entry.courseCode}
                                </div>
                                <div>{entry.section}</div>
                                <div style={{ opacity: 0.9 }}>{entry.room}</div>
                              </div>
                            ) : (
                              <span style={{ color: 'rgba(255, 255, 255, 0.2)' }}>—</span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="table-card">
            <div className="table-header">
              <h2><i className="fas fa-list"></i> Teaching Load Details</h2>
            </div>
            <div className="table-container">
              <table className="schedule-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Course</th>
                    <th>Section</th>
                    <th>Day</th>
                    <th>Time</th>
                    <th>Room</th>
                  </tr>
                </thead>
                <tbody>
                  {schedule.length === 0 ? (
                    <tr>
                      <td colSpan="6" style={{ textAlign: 'center', padding: '40px', color: '#a3a3a3' }}>
                        No classes assigned yet
                      </td>
                    </tr>
                  ) : (
                    schedule.map((entry, index) => (
                      <tr key={index}>
                        <td>{index + 1}</td>
                        <td><span className="course-code">{entry.courseCode}</span></td>
                        <td>{entry.section}</td>
                        <td>{entry.day}</td>
                        <td>{entry.time}</td>
                        <td>{entry.room}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              <span className="table-footer-text">
                <i className="fas fa-info-circle"></i> Total: {schedule.length} classes
              </span>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};

export default FacultyTimetable;