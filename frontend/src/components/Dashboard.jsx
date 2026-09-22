import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import PageBackground from './Background/PageBackground';
import './Dashboard.css';

const Dashboard = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [schedule, setSchedule] = useState(null);
  const [error, setError] = useState(null);
  const [selectedSemester, setSelectedSemester] = useState('1st Semester');
  const [selectedYear, setSelectedYear] = useState('2026-2027');
  const [user, setUser] = useState(null);
  const [showConflicts, setShowConflicts] = useState(false);

  useEffect(() => {
    const userData = localStorage.getItem('user');
    if (userData) {
      setUser(JSON.parse(userData));
    }
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('user');
    navigate('/login');
  };

  const generateSchedule = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.post(
        `/api/schedule/generate?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}`
      );

      setSchedule(response.data);

      const conflicts = response.data.conflictCount || 0;
      const cfsr = response.data.metrics?.cfsr || 0;

      if (conflicts > 0) {
        alert(`⚠️ Schedule generated with ${conflicts} real conflicts.\nCFSR: ${cfsr}%`);
      } else {
        alert(`✅ Schedule generated! No conflicts.\nCFSR: ${cfsr}%`);
      }
    } catch (err) {
      setError(err.message || 'Failed to generate schedule');
      alert('❌ Failed to generate schedule');
    } finally {
      setLoading(false);
    }
  };

  const viewSchedule = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get(
        `/api/schedule/view?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}`
      );

      const data = response.data;

      if (data.entries && data.entries.length > 0) {
        setSchedule(data);

        if (data.conflictCount > 0) {
          alert(`⚠️ ${data.conflictCount} conflicts found in existing schedule!`);
        } else {
          alert('✅ Existing schedule loaded! No conflicts found.');
        }
      } else {
        alert('No existing schedule found. Please generate a new one.');
      }
    } catch (err) {
      setError(err.message || 'Failed to view schedule');
      alert('❌ Failed to view schedule: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const testApi = async () => {
    try {
      const response = await api.get('/api/schedule/test');
      alert(response.data);
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const exportToCSV = () => {
    if (!schedule || !schedule.entries || schedule.entries.length === 0) {
      alert('No schedule data to export!');
      return;
    }

    const headers = ['#', 'Course Code', 'Course Name', 'Section', 'Day', 'Time', 'Room', 'Faculty', 'Status'];

    const rows = schedule.entries.map((entry, index) => [
      index + 1,
      entry.courseCode,
      entry.courseName,
      entry.section,
      entry.day,
      entry.time,
      entry.room,
      entry.faculty,
      entry.isConflictFree ? 'Conflict Free' : 'Conflict'
    ]);

    let csvContent = headers.join(',') + '\n';
    rows.forEach(row => {
      const escapedRow = row.map(field => {
        if (typeof field === 'string' && field.includes(',')) {
          return `"${field}"`;
        }
        return field;
      });
      csvContent += escapedRow.join(',') + '\n';
    });

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `schedule_${selectedSemester}_${selectedYear}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const exportToPDF = () => {
    if (!schedule || !schedule.entries || schedule.entries.length === 0) {
      alert('No schedule data to export!');
      return;
    }

    const doc = new jsPDF('landscape', 'mm', 'a4');
    const pageWidth = doc.internal.pageSize.getWidth();

    doc.setFont('helvetica');

    doc.setFontSize(18);
    doc.setTextColor(26, 26, 26);
    doc.text('Subject Offering & Faculty Loading Schedule', pageWidth / 2, 15, { align: 'center' });

    doc.setFontSize(11);
    doc.setTextColor(107, 107, 107);
    doc.text(`${selectedSemester} - ${selectedYear} | EARIST College of Computing Studies`, pageWidth / 2, 23, { align: 'center' });

    doc.setFontSize(9);
    doc.setTextColor(150, 150, 150);
    doc.text(`Generated: ${new Date().toLocaleString()}`, pageWidth / 2, 30, { align: 'center' });

    const cleanEntries = schedule.entries.map(entry => ({
      courseCode: (entry.courseCode || '').replace(/[^a-zA-Z0-9]/g, ''),
      courseName: (entry.courseName || '').replace(/[^\w\s\-]/g, '').substring(0, 30),
      section: (entry.section || '').replace(/[^\w\s\-]/g, ''),
      day: entry.day || '',
      time: entry.time || '',
      room: (entry.room || '').replace(/[^a-zA-Z0-9]/g, ''),
      faculty: (entry.faculty || '').replace(/[^\w\s\-]/g, '').substring(0, 25),
      isConflictFree: entry.isConflictFree || false
    }));

    const tableHeaders = ['#', 'Course Code', 'Course Name', 'Section', 'Day', 'Time', 'Room', 'Faculty', 'Status'];
    const tableRows = cleanEntries.map((entry, index) => [
      index + 1,
      entry.courseCode,
      entry.courseName,
      entry.section,
      entry.day,
      entry.time,
      entry.room,
      entry.faculty,
      entry.isConflictFree ? 'Conflict Free' : 'Conflict'
    ]);

    autoTable(doc, {
      head: [tableHeaders],
      body: tableRows,
      startY: 38,
      theme: 'grid',
      styles: {
        fontSize: 7,
        cellPadding: 2,
        textColor: [26, 26, 26],
        font: 'helvetica',
        overflow: 'linebreak',
      },
      headStyles: {
        fillColor: [26, 26, 26],
        textColor: [255, 255, 255],
        fontSize: 7,
        fontStyle: 'bold',
        halign: 'center',
        font: 'helvetica',
      },
      alternateRowStyles: {
        fillColor: [252, 249, 242],
      },
      columnStyles: {
        0: { cellWidth: 8, halign: 'center' },
        1: { cellWidth: 22, halign: 'center' },
        2: { cellWidth: 40 },
        3: { cellWidth: 22, halign: 'center' },
        4: { cellWidth: 22, halign: 'center' },
        5: { cellWidth: 28, halign: 'center' },
        6: { cellWidth: 18, halign: 'center' },
        7: { cellWidth: 32 },
        8: { cellWidth: 25, halign: 'center' },
      },
    });

    const finalY = doc.lastAutoTable.finalY + 6;
    doc.setFontSize(8);
    doc.setTextColor(107, 107, 107);

    const conflictCount = cleanEntries.filter(e => !e.isConflictFree).length;

    const dayDistribution = {};
    const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    cleanEntries.forEach(entry => {
      const day = entry.day || 'Unknown';
      dayDistribution[day] = (dayDistribution[day] || 0) + 1;
    });
    const daySummary = days.map(d => `${d.substring(0, 3)}: ${dayDistribution[d] || 0}`).join(' | ');

    const totalMs = schedule.timings?.totalMs || schedule.generationTimeMs || 0;

    doc.text(
      `Total: ${cleanEntries.length} sections | Conflicts: ${conflictCount} | Generated: ${totalMs}ms`,
      pageWidth / 2,
      finalY,
      { align: 'center' }
    );

    doc.setFontSize(7);
    doc.setTextColor(150, 150, 150);
    doc.text(`Distribution: ${daySummary}`, pageWidth / 2, finalY + 5, { align: 'center' });

    doc.save(`schedule_${selectedSemester}_${selectedYear}.pdf`);
  };

  const getFilteredEntries = () => {
    if (!schedule || !schedule.entries) return [];
    if (showConflicts) {
      return schedule.entries.filter(entry => !entry.isConflictFree);
    }
    return schedule.entries;
  };

  const filteredEntries = getFilteredEntries();
  const totalMs = schedule?.timings?.totalMs || schedule?.generationTimeMs || 0;

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
              <i className="fas fa-chart-simple"></i> Dashboard
            </span>
          </div>
          <div className="nav-right">
            <span className="status-dot"></span>
            <span className="status-text">System Online</span>
            {user && (
              <span className="user-badge">
                <i className="fas fa-user"></i> {user.name} ({user.role})
              </span>
            )}
            <button className="logout-btn" onClick={handleLogout}>
              <i className="fas fa-sign-out-alt"></i> Logout
            </button>
          </div>
        </nav>

        <main className="dashboard-main">
          <header className="dashboard-header">
            <h1>Subject Offering & Faculty Loading</h1>
            <p>Generate and manage conflict-free academic schedules</p>
          </header>

          <div className="controls-card">
            <div className="controls-row">
              <div className="control-group">
                <label htmlFor="semester-select">
                  <i className="fas fa-calendar"></i> Semester
                </label>
                <select
                  id="semester-select"
                  value={selectedSemester}
                  onChange={(e) => setSelectedSemester(e.target.value)}
                >
                  <option value="1st Semester">1st Semester</option>
                  <option value="2nd Semester">2nd Semester</option>
                  <option value="Summer">Summer</option>
                </select>
              </div>

              <div className="control-group">
                <label htmlFor="year-select">
                  <i className="fas fa-clock"></i> Academic Year
                </label>
                <select
                  id="year-select"
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(e.target.value)}
                >
                  <option value="2024-2025">2024-2025</option>
                  <option value="2025-2026">2025-2026</option>
                  <option value="2026-2027">2026-2027</option>
                  <option value="2027-2028">2027-2028</option>
                </select>
              </div>

              <button className="btn-primary" onClick={generateSchedule} disabled={loading} type="button">
                {loading ? (
                  <><i className="fas fa-spinner fa-spin"></i> Generating...</>
                ) : (
                  <><i className="fas fa-play"></i> Generate Schedule</>
                )}
              </button>

              <button className="btn-secondary" onClick={viewSchedule} disabled={loading} type="button">
                {loading ? (
                  <><i className="fas fa-spinner fa-spin"></i> Loading...</>
                ) : (
                  <><i className="fas fa-eye"></i> View Existing</>
                )}
              </button>

              <button className="btn-secondary" onClick={testApi} type="button">
                <i className="fas fa-plug"></i> Test Connection
              </button>

              <button
                className={`btn-show-conflicts ${showConflicts ? 'active' : ''}`}
                onClick={() => setShowConflicts(!showConflicts)}
                type="button"
              >
                <i className="fas fa-exclamation-triangle"></i>
                {showConflicts ? 'Show All' : 'Show Conflicts'}
              </button>
            </div>
          </div>

          {error && (
            <div className="error-alert">
              <i className="fas fa-exclamation-triangle"></i> {error}
            </div>
          )}

          {schedule && schedule.success && (
            <>
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-icon"><i className="fas fa-layer-group"></i></div>
                  <div>
                    <div className="stat-label">Total Sections</div>
                    <div className="stat-value">{schedule.totalSections}</div>
                  </div>
                </div>
                <div className="stat-card">
                  <div className="stat-icon"><i className="fas fa-check-circle"></i></div>
                  <div>
                    <div className="stat-label">Scheduled</div>
                    <div className="stat-value">{schedule.scheduledSections}</div>
                  </div>
                </div>
                <div className="stat-card">
                  <div className="stat-icon"><i className="fas fa-bolt"></i></div>
                  <div>
                    <div className="stat-label">Generation Time</div>
                    <div className="stat-value">{totalMs} ms</div>
                  </div>
                </div>
                <div className="stat-card" style={{
                  borderColor: schedule.conflictCount > 0 ? '#991b1b' : '#4caf50'
                }}>
                  <div className="stat-icon">
                    <i className={`fas ${schedule.conflictCount > 0 ? 'fa-exclamation-triangle' : 'fa-check-circle'}`}
                       style={{ color: schedule.conflictCount > 0 ? '#991b1b' : '#4caf50' }}></i>
                  </div>
                  <div>
                    <div className="stat-label">Conflicts Found</div>
                    <div className="stat-value" style={{
                      color: schedule.conflictCount > 0 ? '#991b1b' : '#4caf50'
                    }}>
                      {schedule.conflictCount || 0}
                    </div>
                  </div>
                </div>
              </div>

              <div className="table-card">
                <div className="table-header">
                  <h2>
                    <i className="fas fa-table"></i>
                    {showConflicts ? 'Conflicted Schedules' : 'Generated Schedule'}
                    {showConflicts && filteredEntries.length === 0 && (
                      <span className="no-conflicts-badge">✅ No Conflicts Found</span>
                    )}
                    {schedule.conflictCount > 0 && !showConflicts && (
                      <span className="conflict-warning-badge">
                        ⚠️ {schedule.conflictCount} conflict(s) detected
                      </span>
                    )}
                  </h2>
                  <div className="table-actions">
                    <button className="btn-export" onClick={exportToCSV} type="button">
                      <i className="fas fa-file-csv"></i> Export CSV
                    </button>
                    <button className="btn-export" onClick={exportToPDF} type="button">
                      <i className="fas fa-file-pdf"></i> Export PDF
                    </button>
                  </div>
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
                        <th>Assigned Faculty</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredEntries.length > 0 ? (
                        filteredEntries.map((entry, index) => (
                          <tr key={index} style={{
                            backgroundColor: !entry.isConflictFree ? 'rgba(153, 27, 27, 0.15)' : 'transparent'
                          }}>
                            <td>{index + 1}</td>
                            <td><span className="course-code">{entry.courseCode}</span></td>
                            <td>{entry.courseName}</td>
                            <td>{entry.section}</td>
                            <td>{entry.day}</td>
                            <td>{entry.time}</td>
                            <td>{entry.room}</td>
                            <td>{entry.faculty}</td>
                            <td>
                              {entry.isConflictFree ?
                                <span className="badge-success">
                                  <i className="fas fa-check"></i> Conflict Free
                                </span> :
                                <span className="badge-danger" title={entry.conflictReason || ''}>
                                  <i className="fas fa-exclamation"></i> Conflict
                                </span>
                              }
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan="9" style={{ textAlign: 'center', padding: '40px' }}>
                            {showConflicts ? (
                              <>
                                <i className="fas fa-check-circle" style={{ fontSize: '24px', color: '#81c784' }}></i>
                                <p style={{ marginTop: '8px', color: '#81c784' }}>🎉 No conflicts found! All schedules are conflict-free.</p>
                              </>
                            ) : (
                              'No schedule entries to display'
                            )}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
                <div className="table-footer">
                  <span className="table-footer-text">
                    <i className="fas fa-info-circle"></i>
                    {filteredEntries.length} entries shown •
                    {schedule.totalSections} total sections •
                    {schedule.scheduledSections} scheduled •
                    {totalMs}ms
                    {schedule.conflictCount > 0 && (
                      <span style={{ color: '#ef9a9a', marginLeft: '12px' }}>
                        ⚠️ {schedule.conflictCount} conflicts
                      </span>
                    )}
                  </span>
                </div>
              </div>
            </>
          )}

          {!schedule && !loading && (
            <div className="empty-state">
              <div className="empty-icon">
                <i className="fas fa-chart-simple"></i>
              </div>
              <h3>No Schedule Generated Yet</h3>
              <p>Select a semester and academic year, then click "Generate Schedule".</p>
            </div>
          )}

          {loading && (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Loading schedule...</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default Dashboard;