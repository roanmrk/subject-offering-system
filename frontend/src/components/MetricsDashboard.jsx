import React, { useState, useEffect } from 'react';
import api from '../api';
import { useToast } from './Toast';
import './Dashboard.css';

const generateAcademicYears = () => {
  const currentYear = new Date().getFullYear();
  const years = [];
  for (let i = -2; i <= 5; i++) {
    const startYear = currentYear + i;
    years.push(`${startYear}-${startYear + 1}`);
  }
  return years;
};

const MetricsDashboard = () => {
  const { toast } = useToast();
  const [metrics, setMetrics] = useState(null);
  const [reports, setReports] = useState({
    facultyWorkload: [],
    roomUtilization: [],
    conflictStats: { total: 0, conflictFree: 0, withConflicts: 0 }
  });
  const [loading, setLoading] = useState(true);
  const [semester, setSemester] = useState('1st Semester');
  const currentYear = new Date().getFullYear();
  const [academicYear, setAcademicYear] = useState(`${currentYear}-${currentYear + 1}`);

  useEffect(() => {
    fetchAll();
  }, [semester, academicYear]);

  const fetchAll = async () => {
    setLoading(true);
    await Promise.all([fetchMetrics(), fetchReports()]);
    setLoading(false);
  };

  const fetchMetrics = async () => {
    try {
      const response = await api.get(
        `/api/admin/metrics?semester=${encodeURIComponent(semester)}&academicYear=${academicYear}`
      );
      setMetrics(response.data);
    } catch (error) {
      console.error('Error fetching metrics:', error);
      setMetrics(null);
    }
  };

  const fetchReports = async () => {
    try {
      const [facultyRes, roomsRes] = await Promise.all([
        api.get('/api/admin/faculty'),
        api.get('/api/admin/rooms')
      ]);

      const faculty = facultyRes.data || [];
      const rooms = roomsRes.data || [];

      let schedules = [];
      try {
        const scheduleRes = await api.get(
          `/api/schedule/view?semester=${encodeURIComponent(semester)}&academicYear=${academicYear}`
        );
        schedules = scheduleRes.data.entries || [];
      } catch (e) {
        console.log('No schedules found');
      }

      const facultyWorkload = faculty.map(f => {
        const facultyName = `${f.firstName} ${f.lastName}`;
        const sections = schedules.filter(s => s.faculty === facultyName).length;
        return { name: facultyName, sections, load: f.maxLoadUnits || 24 };
      }).filter(f => f.sections > 0);

      const roomUtilization = rooms.map(r => {
        const usage = schedules.filter(s => s.room === r.roomCode).length;
        return { name: r.roomCode, type: r.roomType, capacity: r.capacity, usage };
      });

      const totalSchedules = schedules.length;
      const conflictFree = schedules.filter(s => s.isConflictFree).length;
      const withConflicts = totalSchedules - conflictFree;

      setReports({
        facultyWorkload,
        roomUtilization,
        conflictStats: { total: totalSchedules, conflictFree, withConflicts }
      });
    } catch (error) {
      console.error('Error fetching reports:', error);
    }
  };

  const getColor = (value, target) => {
    if (value >= target) return '#4caf50';
    if (value >= target * 0.8) return '#e65100';
    return '#991b1b';
  };

  const getWorkloadColor = (sections, maxLoad) => {
    const percentage = maxLoad > 0 ? ((sections * 3) / maxLoad) * 100 : 0;
    if (percentage > 80) return '#991b1b';
    if (percentage > 60) return '#e65100';
    if (percentage > 40) return '#ff6b35';
    return '#4caf50';
  };

  const getUtilizationColor = (usage) => {
    if (usage > 10) return '#991b1b';
    if (usage > 5) return '#e65100';
    if (usage > 2) return '#ff6b35';
    return '#4caf50';
  };

  if (loading) {
    return <div className="loading-state"><div className="spinner"></div><p>Loading analytics...</p></div>;
  }

  const hasMetrics = metrics && metrics.totalOfferings > 0;
  const hasSchedule = reports.conflictStats.total > 0;

  if (!hasMetrics && !hasSchedule) {
    return (
      <div>
        <div className="admin-header">
          <h2><i className="fas fa-chart-line"></i> Reports & Metrics</h2>
          <div className="report-filters">
            <select value={semester} onChange={(e) => setSemester(e.target.value)}>
              <option value="1st Semester">1st Semester</option>
              <option value="2nd Semester">2nd Semester</option>
              <option value="Summer">Summer</option>
            </select>
            <select value={academicYear} onChange={(e) => setAcademicYear(e.target.value)}>
              {generateAcademicYears().map(year => (
                <option key={year} value={year}>{year}</option>
              ))}
            </select>
            <button className="btn-export" onClick={fetchAll}>
              <i className="fas fa-sync"></i> Refresh
            </button>
          </div>
        </div>
        <div className="empty-state">
          <div className="empty-icon"><i className="fas fa-chart-line"></i></div>
          <h3>No Analytics Available</h3>
          <p>Generate a schedule first, then return to this tab.</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-chart-line"></i> Reports & Metrics</h2>
        <div className="report-filters">
          <select value={semester} onChange={(e) => setSemester(e.target.value)}>
            <option value="1st Semester">1st Semester</option>
            <option value="2nd Semester">2nd Semester</option>
            <option value="Summer">Summer</option>
          </select>
          <select value={academicYear} onChange={(e) => setAcademicYear(e.target.value)}>
            {generateAcademicYears().map(year => (
              <option key={year} value={year}>{year}</option>
            ))}
          </select>
          <button className="btn-export" onClick={fetchAll}>
            <i className="fas fa-sync"></i> Refresh
          </button>
        </div>
      </div>

      {/* ============================================================
          THESIS METRICS ROW
          ============================================================ */}
      {hasMetrics && (
        <>
          <h3 style={{ marginBottom: '16px', color: '#1a1a1a' }}>
            <i className="fas fa-bullseye"></i> Thesis Metrics
          </h3>
          <div className="admin-stats-grid">
            <div className="admin-stat-card" style={{ borderColor: getColor(metrics.conflictFreeRate, 95) }}>
              <div className="admin-stat-icon">
                <i className="fas fa-shield-alt" style={{ color: getColor(metrics.conflictFreeRate, 95) }}></i>
              </div>
              <div>
                <div className="admin-stat-value" style={{ color: getColor(metrics.conflictFreeRate, 95) }}>
                  {metrics.conflictFreeRate}%
                </div>
                <div className="admin-stat-label">CFSR (Target ≥95%)</div>
              </div>
            </div>
            <div className="admin-stat-card" style={{ borderColor: getColor(metrics.fwor || 0, 80) }}>
              <div className="admin-stat-icon">
                <i className="fas fa-balance-scale" style={{ color: getColor(metrics.fwor || 0, 80) }}></i>
              </div>
              <div>
                <div className="admin-stat-value" style={{ color: getColor(metrics.fwor || 0, 80) }}>
                  {metrics.fwor || 0}%
                </div>
                <div className="admin-stat-label">FWOR (Target ≥80%)</div>
              </div>
            </div>
            <div className="admin-stat-card" style={{ borderColor: getColor(metrics.rur || 0, 85) }}>
              <div className="admin-stat-icon">
                <i className="fas fa-building" style={{ color: getColor(metrics.rur || 0, 85) }}></i>
              </div>
              <div>
                <div className="admin-stat-value" style={{ color: getColor(metrics.rur || 0, 85) }}>
                  {metrics.rur || 0}%
                </div>
                <div className="admin-stat-label">RUR (Target ≥85%)</div>
              </div>
            </div>
            <div className="admin-stat-card">
              <div className="admin-stat-icon"><i className="fas fa-list"></i></div>
              <div>
                <div className="admin-stat-value">{metrics.totalOfferings}</div>
                <div className="admin-stat-label">Total Offerings</div>
              </div>
            </div>
            <div className="admin-stat-card" style={{ borderColor: metrics.conflictCount > 0 ? '#991b1b' : '#4caf50' }}>
              <div className="admin-stat-icon">
                <i className={`fas ${metrics.conflictCount > 0 ? 'fa-exclamation-triangle' : 'fa-check-circle'}`}
                   style={{ color: metrics.conflictCount > 0 ? '#991b1b' : '#4caf50' }}></i>
              </div>
              <div>
                <div className="admin-stat-value" style={{ color: metrics.conflictCount > 0 ? '#991b1b' : '#4caf50' }}>
                  {metrics.conflictCount}
                </div>
                <div className="admin-stat-label">Conflicts</div>
              </div>
            </div>
            <div className="admin-stat-card">
              <div className="admin-stat-icon"><i className="fas fa-percent"></i></div>
              <div>
                <div className="admin-stat-value">{metrics.sectionCoverage}%</div>
                <div className="admin-stat-label">Section Coverage</div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* ============================================================
          OPERATIONAL ANALYTICS
          ============================================================ */}
      <h3 style={{ marginTop: '32px', marginBottom: '16px', color: '#1a1a1a' }}>
        <i className="fas fa-chart-bar"></i> Operational Analytics
      </h3>

      <div className="reports-grid">
        {/* Faculty Workload */}
        <div className="report-card">
          <h3><i className="fas fa-users"></i> Faculty Workload</h3>
          <div className="report-body">
            {reports.facultyWorkload.length === 0 ? (
              <p style={{ color: '#999', textAlign: 'center' }}>No faculty data. Generate a schedule first.</p>
            ) : (
              <div className="workload-list">
                {reports.facultyWorkload.map((faculty, index) => {
                  const percentage = faculty.load > 0
                    ? Math.round(((faculty.sections * 3) / faculty.load) * 100)
                    : 0;
                  return (
                    <div key={index} className="workload-item">
                      <div className="workload-header">
                        <span className="workload-name">{faculty.name}</span>
                        <span className="workload-count">
                          {faculty.sections} sections
                          <span style={{ marginLeft: '8px', fontWeight: 'bold', color: getWorkloadColor(faculty.sections, faculty.load) }}>
                            ({percentage}%)
                          </span>
                        </span>
                      </div>
                      <div className="workload-bar">
                        <div className="workload-fill" style={{
                          width: `${Math.min(percentage, 100)}%`,
                          backgroundColor: getWorkloadColor(faculty.sections, faculty.load)
                        }}></div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Room Utilization */}
        <div className="report-card">
          <h3><i className="fas fa-building"></i> Room Utilization</h3>
          <div className="report-body">
            {reports.roomUtilization.length === 0 ? (
              <p style={{ color: '#999', textAlign: 'center' }}>No room data available</p>
            ) : (
              <div className="utilization-list">
                {reports.roomUtilization.map((room, index) => (
                  <div key={index} className="utilization-item">
                    <div className="utilization-header">
                      <span className="utilization-name">
                        {room.name} <span className="room-type-badge">{room.type}</span>
                      </span>
                      <span className="utilization-count">
                        {room.usage} sections
                      </span>
                    </div>
                    <div className="utilization-bar">
                      <div className="utilization-fill" style={{
                        width: `${Math.min((room.usage / 10) * 100, 100)}%`,
                        backgroundColor: getUtilizationColor(room.usage)
                      }}></div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Conflict Analysis */}
        <div className="report-card">
          <h3><i className="fas fa-exclamation-triangle"></i> Conflict Analysis</h3>
          <div className="report-body">
            {reports.conflictStats.total === 0 ? (
              <p style={{ color: '#999', textAlign: 'center' }}>No schedule data. Generate a schedule first.</p>
            ) : (
              <div className="conflict-stats">
                <div className="conflict-stat-item">
                  <div className="conflict-stat-circle" style={{ borderColor: '#4caf50', color: '#4caf50' }}>
                    {Math.round((reports.conflictStats.conflictFree / reports.conflictStats.total) * 100)}%
                  </div>
                  <span className="conflict-stat-label">Conflict-Free</span>
                </div>
                <div className="conflict-stat-item">
                  <div className="conflict-stat-circle" style={{
                    borderColor: reports.conflictStats.withConflicts > 0 ? '#991b1b' : '#4caf50',
                    color: reports.conflictStats.withConflicts > 0 ? '#991b1b' : '#4caf50'
                  }}>
                    {reports.conflictStats.withConflicts}
                  </div>
                  <span className="conflict-stat-label">Conflicts</span>
                </div>
                <div className="conflict-stat-item">
                  <div className="conflict-stat-circle" style={{ borderColor: '#1976d2', color: '#1976d2' }}>
                    {reports.conflictStats.total}
                  </div>
                  <span className="conflict-stat-label">Total</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default MetricsDashboard;