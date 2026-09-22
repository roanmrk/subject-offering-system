import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import MetricsDashboard from './MetricsDashboard';
import { useToast } from './Toast';
import PageBackground from './Background/PageBackground';
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

const AdminDashboard = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [activeTab, setActiveTab] = useState('dashboard');

  useEffect(() => {
    const userData = localStorage.getItem('user');
    if (userData) {
      setUser(JSON.parse(userData));
    } else {
      navigate('/login');
    }
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    navigate('/login');
  };

  const renderContent = () => {
    switch(activeTab) {
      case 'dashboard': return <MergedDashboard />;
      case 'courses': return <CourseManagement />;
      case 'faculty': return <FacultyManagement />;
      case 'rooms': return <RoomManagement />;
      case 'timeslots': return <TimeSlotManagement />;
      case 'sections': return <SectionManagement />;
      case 'subject-offerings': return <SubjectOfferingManagement />;
      case 'metrics': return <MetricsDashboard />;
      case 'activity': return <ActivityLogContent />;
      case 'users': return <UserManagement />;
      default: return <MergedDashboard />;
    }
  };

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
            <i className="fas fa-user-shield"></i> Admin Panel
          </span>
        </div>
        <div className="nav-right">
          <span className="status-dot" title="All systems operational"></span>
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

      <div className="admin-layout">
        <aside className="admin-sidebar">
          <button className={`admin-nav-item ${activeTab === 'dashboard' ? 'active' : ''}`} onClick={() => setActiveTab('dashboard')}>
            <i className="fas fa-chart-simple"></i> Dashboard
          </button>
          <button className={`admin-nav-item ${activeTab === 'courses' ? 'active' : ''}`} onClick={() => setActiveTab('courses')}>
            <i className="fas fa-book"></i> Courses
          </button>
          <button className={`admin-nav-item ${activeTab === 'faculty' ? 'active' : ''}`} onClick={() => setActiveTab('faculty')}>
            <i className="fas fa-users"></i> Faculty
          </button>
          <button className={`admin-nav-item ${activeTab === 'rooms' ? 'active' : ''}`} onClick={() => setActiveTab('rooms')}>
            <i className="fas fa-building"></i> Rooms
          </button>
          <button className={`admin-nav-item ${activeTab === 'timeslots' ? 'active' : ''}`} onClick={() => setActiveTab('timeslots')}>
            <i className="fas fa-clock"></i> Time Slots
          </button>
          <button className={`admin-nav-item ${activeTab === 'sections' ? 'active' : ''}`} onClick={() => setActiveTab('sections')}>
            <i className="fas fa-layer-group"></i> Sections
          </button>
          <button className={`admin-nav-item ${activeTab === 'subject-offerings' ? 'active' : ''}`} onClick={() => setActiveTab('subject-offerings')}>
            <i className="fas fa-th-list"></i> Subject Offerings
          </button>
         
                    <button className={`admin-nav-item ${activeTab === 'metrics' ? 'active' : ''}`} onClick={() => setActiveTab('metrics')}>
            <i className="fas fa-chart-line"></i> Reports & Metrics
          </button>
          <button className={`admin-nav-item ${activeTab === 'activity' ? 'active' : ''}`} onClick={() => setActiveTab('activity')}>
            <i className="fas fa-history"></i> Activity Log
          </button>
          <button className={`admin-nav-item ${activeTab === 'users' ? 'active' : ''}`} onClick={() => setActiveTab('users')}>
            <i className="fas fa-user-cog"></i> Users
          </button>
                       </aside>

        <main className="admin-content">
          {renderContent()}
        </main>
      </div>
      </div>
    </div>
  );
};

// ============================================================
// MERGED DASHBOARD
// ============================================================
const MergedDashboard = () => {
  const { toast, confirm } = useToast();
  const [loading, setLoading] = useState(false);
  const [schedule, setSchedule] = useState(null);
  const [error, setError] = useState(null);
  const [selectedSemester, setSelectedSemester] = useState('1st Semester');
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(`${currentYear}-${currentYear + 1}`);
  const [showConflicts, setShowConflicts] = useState(false);
  const [stats, setStats] = useState({
    totalCourses: 0, totalFaculty: 0, totalRooms: 0, totalSections: 0
  });

   
   useEffect(() => { 
    fetchStats(); 
    loadExistingSchedule();
  }, []);

  // Auto-reload the schedule when semester/year changes
  useEffect(() => {
    loadExistingSchedule();
  }, [selectedSemester, selectedYear]);

  const fetchStats = async () => {
    try {
      const [coursesRes, facultyRes, roomsRes, sectionsRes] = await Promise.all([
        api.get('/api/admin/courses'),
        api.get('/api/admin/faculty'),
        api.get('/api/admin/rooms'),
        api.get('/api/admin/sections')
      ]);
      setStats({
        totalCourses: coursesRes.data.length,
        totalFaculty: facultyRes.data.length,
        totalRooms: roomsRes.data.length,
        totalSections: sectionsRes.data.length
      });
    } catch (error) {
      console.error('Error fetching stats:', error);
    }
  };

  const loadExistingSchedule = async () => {
    try {
      const res = await api.get(
        `/api/schedule/view?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}`
      );
      const data = res.data;
      if (data && data.entries && data.entries.length > 0) {
        setSchedule({
          success: true,
          entries: data.entries,
          totalSections: data.totalSections,
          scheduledSections: data.scheduledSections,
          conflictCount: data.conflictCount,
          hasConflicts: data.hasConflicts,
          semester: data.semester,
          academicYear: data.academicYear,
          metrics: null,
          timings: null,
          generationTimeMs: 0,
          seedUsed: 0,
        });
      } else {
        setSchedule(null);
      }
    } catch (err) {
      setSchedule(null);
    }
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
        toast.warning(
          'Schedule Generated with Conflicts',
          `${conflicts} conflict(s) detected. CFSR: ${cfsr}%`
        );
      } else {
        toast.success(
          'Schedule Generated',
          `No conflicts. CFSR: ${cfsr}%`
        );
      }
    } catch (err) {
      setError(err.message || 'Failed to generate schedule');
      toast.error('Generation Failed', 'Unable to generate schedule. Please try again.');
    } finally {
      setLoading(false);
    }
  };

    const resetAndRegenerate = async () => {
    // Check how many offerings exist for the selected semester/year
    let existingCount = 0;
    try {
      const res = await api.get(
        `/api/schedule/view?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}`
      );
      existingCount = (res.data?.entries || []).length;
    } catch (err) {
      existingCount = 0;
    }

    const message = existingCount > 0
      ? `This will clear ${existingCount} existing offerings for ${selectedSemester} ${selectedYear} and generate a new schedule.`
      : `This will generate a new schedule for ${selectedSemester} ${selectedYear}.`;

    const ok = await confirm({
      title: existingCount > 0 ? 'Reset & Regenerate?' : 'Generate New Schedule?',
      message,
      confirmText: existingCount > 0 ? 'Yes, Regenerate' : 'Generate',
      cancelText: 'Cancel',
      variant: 'warning',
    });

    if (!ok) return;

    setLoading(true);
    setError(null);
    try {
      await api.delete(
        `/api/schedule/reset?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}`
      );

      const response = await api.post(
        `/api/schedule/generate?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}&random=true`
      );

      setSchedule(response.data);

      const conflicts = response.data.conflictCount || 0;
      const cfsr = response.data.metrics?.cfsr || 0;

      if (conflicts > 0) {
        toast.warning(
          'New Schedule Generated with Conflicts',
          `${conflicts} conflict(s) detected. CFSR: ${cfsr}%`
        );
      } else {
        toast.success(
          'New Schedule Generated',
          `No conflicts. CFSR: ${cfsr}%`
        );
      }
    } catch (err) {
      setError(err.message || 'Failed to regenerate schedule');
      toast.error('Regeneration Failed', 'Unable to regenerate schedule. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const exportToCSV = () => {
    if (!schedule || !schedule.entries || schedule.entries.length === 0) {
      toast.warning('No Data', 'Generate a schedule first before exporting.');
      return;
    }

    const headers = ['#', 'Course Code', 'Course Name', 'Section', 'Day', 'Time', 'Room', 'Faculty', 'Status'];
    const rows = schedule.entries.map((entry, index) => [
      index + 1, entry.courseCode, entry.courseName, entry.section,
      entry.day, entry.time, entry.room, entry.faculty,
      entry.isConflictFree ? 'Conflict Free' : 'Conflict'
    ]);

    let csvContent = headers.join(',') + '\n';
    rows.forEach(row => {
      const escapedRow = row.map(field => {
        if (typeof field === 'string' && field.includes(',')) return `"${field}"`;
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
    toast.success('CSV Exported', 'Schedule saved as CSV file.');
  };

  const exportToPDF = () => {
    if (!schedule || !schedule.entries || schedule.entries.length === 0) {
      toast.warning('No Data', 'Generate a schedule first before exporting.');
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
      courseName: (entry.courseName || '').replace(/[^\w\s-]/g, '').substring(0, 30),
      section: (entry.section || '').replace(/[^\w\s-]/g, ''),
      day: entry.day || '',
      time: entry.time || '',
      room: (entry.room || '').replace(/[^a-zA-Z0-9]/g, ''),
      faculty: (entry.faculty || '').replace(/[^\w\s-]/g, '').substring(0, 25),
      isConflictFree: entry.isConflictFree || false
    }));

    const tableHeaders = ['#', 'Course Code', 'Course Name', 'Section', 'Day', 'Time', 'Room', 'Faculty', 'Status'];
    const tableRows = cleanEntries.map((entry, index) => [
      index + 1, entry.courseCode, entry.courseName, entry.section,
      entry.day, entry.time, entry.room, entry.faculty,
      entry.isConflictFree ? 'Conflict Free' : 'Conflict'
    ]);

    autoTable(doc, {
      head: [tableHeaders],
      body: tableRows,
      startY: 38,
      theme: 'grid',
      styles: { fontSize: 7, cellPadding: 2, textColor: [26, 26, 26], font: 'helvetica', overflow: 'linebreak' },
      headStyles: { fillColor: [26, 26, 26], textColor: [255, 255, 255], fontSize: 7, fontStyle: 'bold', halign: 'center' },
      alternateRowStyles: { fillColor: [252, 249, 242] },
      columnStyles: {
        0: { cellWidth: 8, halign: 'center' }, 1: { cellWidth: 22, halign: 'center' },
        2: { cellWidth: 40 }, 3: { cellWidth: 22, halign: 'center' },
        4: { cellWidth: 22, halign: 'center' }, 5: { cellWidth: 28, halign: 'center' },
        6: { cellWidth: 18, halign: 'center' }, 7: { cellWidth: 32 },
        8: { cellWidth: 25, halign: 'center' }
      }
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

    const timeText = totalMs > 0 
  ? ` | Generated: ${totalMs}ms` 
  : '';
doc.text(
  `Total: ${cleanEntries.length} sections | Conflicts: ${conflictCount}${timeText}`,
  pageWidth / 2, finalY, { align: 'center' }
);
    doc.setTextColor(150, 150, 150);
    doc.text(`Distribution: ${daySummary}`, pageWidth / 2, finalY + 5, { align: 'center' });

    doc.save(`schedule_${selectedSemester}_${selectedYear}.pdf`);
    toast.success('PDF Exported', 'Schedule saved as PDF file.');
  };

  const getFilteredEntries = () => {
    if (!schedule || !schedule.entries) return [];
    if (showConflicts) return schedule.entries.filter(entry => !entry.isConflictFree);
    return schedule.entries;
  };

  const filteredEntries = getFilteredEntries();

  const statItems = [
    { label: 'Total Courses', value: stats.totalCourses, icon: 'fa-book' },
    { label: 'Faculty Members', value: stats.totalFaculty, icon: 'fa-users' },
    { label: 'Rooms', value: stats.totalRooms, icon: 'fa-building' },
    { label: 'Total Sections', value: stats.totalSections, icon: 'fa-layer-group' },
  ];

  return (
    <div>
      <h2><i className="fas fa-chart-simple"></i> Dashboard</h2>
      <p>System overview and schedule generation</p>

      <div className="admin-stats-grid" style={{ marginTop: '20px' }}>
        {statItems.map((stat, index) => (
          <div className="admin-stat-card" key={index}>
            <div className="admin-stat-icon"><i className={`fas ${stat.icon}`}></i></div>
            <div>
              <div className="admin-stat-value">{stat.value}</div>
              <div className="admin-stat-label">{stat.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="controls-card" style={{ marginTop: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>
          <i className="fas fa-calendar-plus"></i> Generate Schedule
        </h3>
        <div className="controls-row">
          <div className="control-group">
            <label htmlFor="admin-semester"><i className="fas fa-calendar"></i> Semester</label>
            <select id="admin-semester" value={selectedSemester} onChange={(e) => setSelectedSemester(e.target.value)}>
              <option value="1st Semester">1st Semester</option>
              <option value="2nd Semester">2nd Semester</option>
              <option value="Summer">Summer</option>
            </select>
          </div>

          <div className="control-group">
            <label htmlFor="admin-year"><i className="fas fa-clock"></i> Academic Year</label>
            <select id="admin-year" value={selectedYear} onChange={(e) => setSelectedYear(e.target.value)}>
              {generateAcademicYears().map(year => (
                <option key={year} value={year}>{year}</option>
              ))}
            </select>
          </div>

          <button className="btn-primary" onClick={generateSchedule} disabled={loading}>
            {loading ? (
              <><i className="fas fa-spinner fa-spin"></i> Generating...</>
            ) : (
              <><i className="fas fa-play"></i> Generate Schedule</>
            )}
          </button>

          <button
            className="btn-secondary"
            onClick={resetAndRegenerate}
            disabled={loading}
            style={{ background: '#fff3e0', borderColor: '#ffcc80', color: '#e65100' }}
          >
            <i className="fas fa-redo"></i> Reset & Regenerate
          </button>
        </div>
      </div>

      {error && (
        <div className="error-alert" style={{ marginTop: '12px' }}>
          <i className="fas fa-exclamation-triangle"></i> {error}
        </div>
      )}

      {schedule && schedule.success && (
        <div className="table-card" style={{ marginTop: '20px' }}>
          <div className="table-header">
            <h2>
              <i className="fas fa-table"></i>
              {showConflicts ? 'Conflicted Schedules' : 'Generated Schedule'}
            </h2>
            <div className="table-actions">
              <button
                className={`btn-show-conflicts ${showConflicts ? 'active' : ''}`}
                onClick={() => setShowConflicts(!showConflicts)}
              >
                <i className="fas fa-exclamation-triangle"></i>
                {showConflicts ? 'Show All' : 'Show Conflicts'}
              </button>
              <button className="btn-export" onClick={exportToCSV}>
                <i className="fas fa-file-csv"></i> Export CSV
              </button>
              <button className="btn-export" onClick={exportToPDF}>
                <i className="fas fa-file-pdf"></i> Export PDF
              </button>
              <button
                className="btn-export"
                onClick={() => window.open(`/print-schedule?semester=${encodeURIComponent(selectedSemester)}&academicYear=${selectedYear}`, '_blank')}
                style={{ background: '#1976d2', color: '#fff', border: 'none' }}
              >
                <i className="fas fa-print"></i> Print View
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
                  <th>Faculty</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredEntries.length > 0 ? (
                  filteredEntries.map((entry, index) => (
                    <tr key={index} style={{ backgroundColor: !entry.isConflictFree ? '#ffebee' : 'transparent' }}>
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
                          <span className="badge-success"><i className="fas fa-check"></i> Free</span> :
                          <span className="badge-danger"><i className="fas fa-exclamation"></i> Conflict</span>
                        }
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="9" style={{ textAlign: 'center', padding: '40px' }}>
                      {showConflicts ? '🎉 No conflicts found!' : 'No entries'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!schedule && !loading && (
        <div className="empty-state" style={{ marginTop: '40px' }}>
          <div className="empty-icon"><i className="fas fa-calendar-plus"></i></div>
          <h3>No Schedule Generated Yet</h3>
          <p>Select a semester and academic year, then click "Generate Schedule".</p>
        </div>
      )}
    </div>
  );
};

// ============================================================
// COURSE MANAGEMENT
// ============================================================
const CourseManagement = () => {
  const { toast, confirm } = useToast();
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newCourse, setNewCourse] = useState({
    courseCode: '', courseName: '', program: 'BSCS',
    yearLevel: 1, semester: 1, units: 3, isLaboratory: false
  });
  const [editingId, setEditingId] = useState(null);

  useEffect(() => { fetchCourses(); }, []);

  const fetchCourses = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/courses');
      setCourses(response.data);
    } catch (error) {
      console.error('Error fetching courses:', error);
    } finally {
      setLoading(false);
    }
  };

  const saveCourse = async () => {
    if (!newCourse.courseCode || !newCourse.courseName) {
      toast.warning('Missing Fields', 'Please fill in all required fields.');
      return;
    }
    try {
      let response;
      if (editingId) {
        response = await api.put(`/api/admin/courses/${editingId}`, newCourse);
        setCourses(courses.map(c => c.id === editingId ? response.data : c));
        toast.success('Course Updated', `${newCourse.courseCode} has been updated.`);
      } else {
        response = await api.post('/api/admin/courses', newCourse);
        setCourses([...courses, response.data]);
        toast.success('Course Added', `${newCourse.courseCode} has been created.`);
      }
      setNewCourse({ courseCode: '', courseName: '', program: 'BSCS', yearLevel: 1, semester: 1, units: 3, isLaboratory: false });
      setShowForm(false);
      setEditingId(null);
    } catch (error) {
      console.error('Error saving course:', error);
      toast.error('Save Failed', error.response?.data?.error || error.message);
    }
  };

  const editCourse = (course) => {
    setNewCourse({
      courseCode: course.courseCode, courseName: course.courseName,
      program: course.program, yearLevel: course.yearLevel,
      semester: course.semester, units: course.units, isLaboratory: course.isLaboratory
    });
    setEditingId(course.id);
    setShowForm(true);
  };

  const deleteCourse = async (id) => {
    const ok = await confirm({
      title: 'Delete Course?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/courses/${id}`);
      setCourses(courses.filter(c => c.id !== id));
      toast.success('Course Deleted', 'The course has been removed.');
    } catch (error) {
      console.error('Error deleting course:', error);
      toast.error('Delete Failed', 'Could not delete the course.');
    }
  };

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading courses...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-book"></i> Course Management</h2>
        <button className="btn-primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setNewCourse({ courseCode: '', courseName: '', program: 'BSCS', yearLevel: 1, semester: 1, units: 3, isLaboratory: false }); }}>
          <i className="fas fa-plus"></i> Add Course
        </button>
      </div>

      {showForm && (
        <div className="admin-form">
          <input type="text" placeholder="Course Code" value={newCourse.courseCode} onChange={(e) => setNewCourse({...newCourse, courseCode: e.target.value})} />
          <input type="text" placeholder="Course Name" value={newCourse.courseName} onChange={(e) => setNewCourse({...newCourse, courseName: e.target.value})} />
          <select value={newCourse.program} onChange={(e) => setNewCourse({...newCourse, program: e.target.value})}>
            <option value="BSCS">BSCS</option>
            <option value="BSInfoTech">BSInfoTech</option>
          </select>
          <input type="number" placeholder="Year Level" value={newCourse.yearLevel} onChange={(e) => setNewCourse({...newCourse, yearLevel: parseInt(e.target.value)})} />
          <input type="number" placeholder="Semester" value={newCourse.semester} onChange={(e) => setNewCourse({...newCourse, semester: parseInt(e.target.value)})} />
          <input type="number" placeholder="Units" value={newCourse.units} onChange={(e) => setNewCourse({...newCourse, units: parseInt(e.target.value)})} />
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <input type="checkbox" checked={newCourse.isLaboratory} onChange={(e) => setNewCourse({...newCourse, isLaboratory: e.target.checked})} />
            Laboratory Course
          </label>
          <button className="btn-primary" onClick={saveCourse}>{editingId ? 'Update' : 'Save'}</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search courses by code or name..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr><th>Code</th><th>Name</th><th>Program</th><th>Year</th><th>Units</th><th>Lab</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {courses.length === 0 ? (
              <tr><td colSpan="7" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>No courses found.</td></tr>
            ) : (
              courses
                .filter(c => 
                  c.courseCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  c.courseName.toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(course => (
                  <tr key={course.id}>
                    <td><strong>{course.courseCode}</strong></td>
                    <td>{course.courseName}</td>
                    <td>{course.program}</td>
                    <td>{course.yearLevel}</td>
                    <td>{course.units}</td>
                    <td>{course.isLaboratory ? '✅' : '❌'}</td>
                    <td>
                      <button className="btn-edit" onClick={() => editCourse(course)}><i className="fas fa-edit"></i></button>
                      <button className="btn-delete" onClick={() => deleteCourse(course.id)}><i className="fas fa-trash"></i></button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ============================================================
// FACULTY MANAGEMENT
// ============================================================
const FacultyManagement = () => {
  const { toast, confirm } = useToast();
  const [faculty, setFaculty] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newFaculty, setNewFaculty] = useState({
    facultyId: '', firstName: '', lastName: '',
    specialization: '', certifications: '', maxLoadUnits: 24
  });
  const [editingId, setEditingId] = useState(null);

  useEffect(() => { fetchFaculty(); }, []);

  const fetchFaculty = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/faculty');
      setFaculty(response.data);
    } catch (error) {
      console.error('Error fetching faculty:', error);
    } finally {
      setLoading(false);
    }
  };

  const saveFaculty = async () => {
    if (!newFaculty.facultyId || !newFaculty.firstName || !newFaculty.lastName) {
      toast.warning('Missing Fields', 'Please fill in all required fields.');
      return;
    }
    try {
      let response;
      if (editingId) {
        response = await api.put(`/api/admin/faculty/${editingId}`, newFaculty);
        setFaculty(faculty.map(f => f.id === editingId ? response.data : f));
        toast.success('Faculty Updated', `${newFaculty.firstName} ${newFaculty.lastName} updated.`);
      } else {
        response = await api.post('/api/admin/faculty', newFaculty);
        setFaculty([...faculty, response.data]);
        toast.success('Faculty Added', `${newFaculty.firstName} ${newFaculty.lastName} added.`);
      }
      setNewFaculty({ facultyId: '', firstName: '', lastName: '', specialization: '', certifications: '', maxLoadUnits: 24 });
      setShowForm(false);
      setEditingId(null);
    } catch (error) {
      console.error('Error saving faculty:', error);
      toast.error('Save Failed', error.response?.data?.error || error.message);
    }
  };

  const editFaculty = (fac) => {
    setNewFaculty({
      facultyId: fac.facultyId, firstName: fac.firstName, lastName: fac.lastName,
      specialization: fac.specialization || '', certifications: fac.certifications || '',
      maxLoadUnits: fac.maxLoadUnits || 24
    });
    setEditingId(fac.id);
    setShowForm(true);
  };

  const deleteFaculty = async (id) => {
    const ok = await confirm({
      title: 'Delete Faculty?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/faculty/${id}`);
      setFaculty(faculty.filter(f => f.id !== id));
      toast.success('Faculty Deleted', 'The faculty record has been removed.');
    } catch (error) {
      console.error('Error deleting faculty:', error);
      toast.error('Delete Failed', 'Could not delete the faculty.');
    }
  };

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading faculty...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-users"></i> Faculty Management</h2>
        <button className="btn-primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setNewFaculty({ facultyId: '', firstName: '', lastName: '', specialization: '', certifications: '', maxLoadUnits: 24 }); }}>
          <i className="fas fa-plus"></i> Add Faculty
        </button>
      </div>

      {showForm && (
        <div className="admin-form">
          <input type="text" placeholder="Faculty ID (e.g. F001)" value={newFaculty.facultyId} onChange={(e) => setNewFaculty({...newFaculty, facultyId: e.target.value})} />
          <input type="text" placeholder="First Name" value={newFaculty.firstName} onChange={(e) => setNewFaculty({...newFaculty, firstName: e.target.value})} />
          <input type="text" placeholder="Last Name" value={newFaculty.lastName} onChange={(e) => setNewFaculty({...newFaculty, lastName: e.target.value})} />
          <input type="text" placeholder="Specialization" value={newFaculty.specialization} onChange={(e) => setNewFaculty({...newFaculty, specialization: e.target.value})} />
          <input type="text" placeholder="Certifications" value={newFaculty.certifications} onChange={(e) => setNewFaculty({...newFaculty, certifications: e.target.value})} />
          <input type="number" placeholder="Max Load Units" value={newFaculty.maxLoadUnits} onChange={(e) => setNewFaculty({...newFaculty, maxLoadUnits: parseInt(e.target.value)})} />
          <button className="btn-primary" onClick={saveFaculty}>{editingId ? 'Update' : 'Save'}</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search faculty by name or ID..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr><th>ID</th><th>Name</th><th>Specialization</th><th>Max Load</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {faculty.length === 0 ? (
              <tr><td colSpan="5" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>No faculty found.</td></tr>
            ) : (
              faculty
                .filter(f => 
                  f.firstName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  f.lastName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  f.facultyId.toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(f => (
                  <tr key={f.id}>
                    <td><strong>{f.facultyId}</strong></td>
                    <td>{f.firstName} {f.lastName}</td>
                    <td>{f.specialization}</td>
                    <td>{f.maxLoadUnits} units</td>
                    <td>
                      <button className="btn-edit" onClick={() => editFaculty(f)}><i className="fas fa-edit"></i></button>
                      <button className="btn-delete" onClick={() => deleteFaculty(f.id)}><i className="fas fa-trash"></i></button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ============================================================
// ROOM MANAGEMENT
// ============================================================
const RoomManagement = () => {
  const { toast, confirm } = useToast();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newRoom, setNewRoom] = useState({ roomCode: '', roomName: '', roomType: 'LECTURE', capacity: 60, equipment: '' });
  const [editingId, setEditingId] = useState(null);

  useEffect(() => { fetchRooms(); }, []);

  const fetchRooms = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/rooms');
      setRooms(response.data);
    } catch (error) {
      console.error('Error fetching rooms:', error);
    } finally {
      setLoading(false);
    }
  };

  const saveRoom = async () => {
    if (!newRoom.roomCode || !newRoom.roomName) {
      toast.warning('Missing Fields', 'Please fill in all required fields.');
      return;
    }
    try {
      let response;
      if (editingId) {
        response = await api.put(`/api/admin/rooms/${editingId}`, newRoom);
        setRooms(rooms.map(r => r.id === editingId ? response.data : r));
        toast.success('Room Updated', `${newRoom.roomCode} updated.`);
      } else {
        response = await api.post('/api/admin/rooms', newRoom);
        setRooms([...rooms, response.data]);
        toast.success('Room Added', `${newRoom.roomCode} added.`);
      }
      setNewRoom({ roomCode: '', roomName: '', roomType: 'LECTURE', capacity: 60, equipment: '' });
      setShowForm(false);
      setEditingId(null);
    } catch (error) {
      console.error('Error saving room:', error);
      toast.error('Save Failed', error.response?.data?.error || error.message);
    }
  };

  const editRoom = (room) => {
    setNewRoom({
      roomCode: room.roomCode, roomName: room.roomName,
      roomType: room.roomType, capacity: room.capacity, equipment: room.equipment || ''
    });
    setEditingId(room.id);
    setShowForm(true);
  };

  const deleteRoom = async (id) => {
    const ok = await confirm({
      title: 'Delete Room?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/rooms/${id}`);
      setRooms(rooms.filter(r => r.id !== id));
      toast.success('Room Deleted', 'The room has been removed.');
    } catch (error) {
      console.error('Error deleting room:', error);
      toast.error('Delete Failed', 'Could not delete the room.');
    }
  };

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading rooms...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-building"></i> Room Management</h2>
        <button className="btn-primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setNewRoom({ roomCode: '', roomName: '', roomType: 'LECTURE', capacity: 60, equipment: '' }); }}>
          <i className="fas fa-plus"></i> Add Room
        </button>
      </div>

      {showForm && (
        <div className="admin-form">
          <input type="text" placeholder="Room Code (e.g. RM101)" value={newRoom.roomCode} onChange={(e) => setNewRoom({...newRoom, roomCode: e.target.value})} />
          <input type="text" placeholder="Room Name" value={newRoom.roomName} onChange={(e) => setNewRoom({...newRoom, roomName: e.target.value})} />
          <select value={newRoom.roomType} onChange={(e) => setNewRoom({...newRoom, roomType: e.target.value})}>
            <option value="LECTURE">Lecture</option>
            <option value="LABORATORY">Laboratory</option>
          </select>
          <input type="number" placeholder="Capacity" value={newRoom.capacity} onChange={(e) => setNewRoom({...newRoom, capacity: parseInt(e.target.value)})} />
          <input type="text" placeholder="Equipment" value={newRoom.equipment} onChange={(e) => setNewRoom({...newRoom, equipment: e.target.value})} />
          <button className="btn-primary" onClick={saveRoom}>{editingId ? 'Update' : 'Save'}</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search rooms by code or name..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr><th>Code</th><th>Name</th><th>Type</th><th>Capacity</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {rooms.length === 0 ? (
              <tr><td colSpan="5" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>No rooms found.</td></tr>
            ) : (
              rooms
                .filter(room => 
                  room.roomCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  room.roomName.toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(room => (
                  <tr key={room.id}>
                    <td><strong>{room.roomCode}</strong></td>
                    <td>{room.roomName}</td>
                    <td>{room.roomType}</td>
                    <td>{room.capacity}</td>
                    <td>
                      <button className="btn-edit" onClick={() => editRoom(room)}><i className="fas fa-edit"></i></button>
                      <button className="btn-delete" onClick={() => deleteRoom(room.id)}><i className="fas fa-trash"></i></button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ============================================================
// TIME SLOT MANAGEMENT
// ============================================================
const TimeSlotManagement = () => {
  const { toast, confirm } = useToast();
  const [timeslots, setTimeslots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newSlot, setNewSlot] = useState({ day: 'Monday', startTime: '07:00', endTime: '08:00', slotCode: '' });
  const [editingId, setEditingId] = useState(null);
  const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  useEffect(() => { fetchTimeslots(); }, []);

  const fetchTimeslots = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/timeslots');
      setTimeslots(response.data);
    } catch (error) {
      console.error('Error fetching timeslots:', error);
    } finally {
      setLoading(false);
    }
  };

  const saveTimeSlot = async () => {
    if (!newSlot.day || !newSlot.startTime || !newSlot.endTime) {
      toast.warning('Missing Fields', 'Please fill in all fields.');
      return;
    }
    const slotCode = `${newSlot.day.substring(0, 3).toUpperCase()}-${newSlot.startTime.substring(0, 2)}`;
    const data = { ...newSlot, slotCode };
    try {
      let response;
      if (editingId) {
        response = await api.put(`/api/admin/timeslots/${editingId}`, data);
        setTimeslots(timeslots.map(s => s.id === editingId ? response.data : s));
        toast.success('Time Slot Updated', `${slotCode} updated.`);
      } else {
        response = await api.post('/api/admin/timeslots', data);
        setTimeslots([...timeslots, response.data]);
        toast.success('Time Slot Added', `${slotCode} added.`);
      }
      setNewSlot({ day: 'Monday', startTime: '07:00', endTime: '08:00', slotCode: '' });
      setShowForm(false);
      setEditingId(null);
    } catch (error) {
      console.error('Error saving timeslot:', error);
      toast.error('Save Failed', error.response?.data?.error || error.message);
    }
  };

  const editTimeSlot = (slot) => {
    setNewSlot({ day: slot.day, startTime: slot.startTime, endTime: slot.endTime, slotCode: slot.slotCode });
    setEditingId(slot.id);
    setShowForm(true);
  };

  const deleteTimeSlot = async (id) => {
    const ok = await confirm({
      title: 'Delete Time Slot?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/timeslots/${id}`);
      setTimeslots(timeslots.filter(s => s.id !== id));
      toast.success('Time Slot Deleted', 'The time slot has been removed.');
    } catch (error) {
      console.error('Error deleting timeslot:', error);
      toast.error('Delete Failed', 'Could not delete the time slot.');
    }
  };

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading time slots...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-clock"></i> Time Slot Management</h2>
        <button className="btn-primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setNewSlot({ day: 'Monday', startTime: '07:00', endTime: '08:00', slotCode: '' }); }}>
          <i className="fas fa-plus"></i> Add Time Slot
        </button>
      </div>

      {showForm && (
        <div className="admin-form">
          <select value={newSlot.day} onChange={(e) => setNewSlot({...newSlot, day: e.target.value})}>
            {days.map(day => <option key={day} value={day}>{day}</option>)}
          </select>
          <input type="time" value={newSlot.startTime} onChange={(e) => setNewSlot({...newSlot, startTime: e.target.value})} />
          <input type="time" value={newSlot.endTime} onChange={(e) => setNewSlot({...newSlot, endTime: e.target.value})} />
          <button className="btn-primary" onClick={saveTimeSlot}>{editingId ? 'Update' : 'Save'}</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search by day or slot code..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr><th>Day</th><th>Start</th><th>End</th><th>Slot Code</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {timeslots.length === 0 ? (
              <tr><td colSpan="5" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>No time slots found.</td></tr>
            ) : (
              timeslots
                .filter(slot => 
                  slot.day.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  (slot.slotCode || '').toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(slot => (
                  <tr key={slot.id}>
                    <td><strong>{slot.day}</strong></td>
                    <td>{slot.startTime}</td>
                    <td>{slot.endTime}</td>
                    <td>{slot.slotCode}</td>
                    <td>
                      <button className="btn-edit" onClick={() => editTimeSlot(slot)}><i className="fas fa-edit"></i></button>
                      <button className="btn-delete" onClick={() => deleteTimeSlot(slot.id)}><i className="fas fa-trash"></i></button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ============================================================
// SECTION MANAGEMENT
// ============================================================
const SectionManagement = () => {
  const { toast, confirm } = useToast();
  const [sections, setSections] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newSection, setNewSection] = useState({
    sectionCode: '', program: 'BSCS', yearLevel: 1, expectedEnrollment: 60
  });
  const [editingId, setEditingId] = useState(null);

  useEffect(() => { fetchSections(); }, []);

  const fetchSections = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/sections');
      setSections(response.data);
    } catch (error) {
      console.error('Error fetching sections:', error);
    } finally {
      setLoading(false);
    }
  };

  const saveSection = async () => {
    if (!newSection.sectionCode) {
      toast.warning('Missing Fields', 'Please fill in section code.');
      return;
    }

    const payload = {
      sectionCode: newSection.sectionCode,
      program: newSection.program,
      yearLevel: parseInt(newSection.yearLevel),
      expectedEnrollment: parseInt(newSection.expectedEnrollment)
    };

    try {
      let response;
      if (editingId) {
        response = await api.put(`/api/admin/sections/${editingId}`, payload);
        setSections(sections.map(s => s.id === editingId ? response.data : s));
        toast.success('Section Updated', `${newSection.sectionCode} updated.`);
      } else {
        response = await api.post('/api/admin/sections', payload);
        setSections([...sections, response.data]);
        toast.success('Section Added', `${newSection.sectionCode} added.`);
      }
      setNewSection({ sectionCode: '', program: 'BSCS', yearLevel: 1, expectedEnrollment: 60 });
      setShowForm(false);
      setEditingId(null);
    } catch (error) {
      console.error('Error saving section:', error);
      toast.error('Save Failed', error.response?.data?.error || error.message);
    }
  };

  const editSection = (sec) => {
    setNewSection({
      sectionCode: sec.sectionCode, program: sec.program,
      yearLevel: sec.yearLevel, expectedEnrollment: sec.expectedEnrollment
    });
    setEditingId(sec.id);
    setShowForm(true);
  };

  const deleteSection = async (id) => {
    const ok = await confirm({
      title: 'Delete Section?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/sections/${id}`);
      setSections(sections.filter(s => s.id !== id));
      toast.success('Section Deleted', 'The section has been removed.');
    } catch (error) {
      console.error('Error deleting section:', error);
      toast.error('Delete Failed', 'Could not delete the section.');
    }
  };

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading sections...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-layer-group"></i> Section Management</h2>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="file"
            accept=".csv"
            id="csv-import-sections"
            style={{ display: 'none' }}
            onChange={async (e) => {
              const file = e.target.files[0];
              if (!file) return;

              const formData = new FormData();
              formData.append('file', file);

              try {
                const response = await api.post('/api/admin/sections/import', formData, {
                  headers: { 'Content-Type': 'multipart/form-data' }
                });

                toast.success(
                  'CSV Imported',
                  `Imported: ${response.data.imported}, Failed: ${response.data.failed}`
                );
                fetchSections();
              } catch (error) {
                toast.error('Import Failed', error.response?.data?.error || error.message);
              }

              e.target.value = '';
            }}
          />
          <button 
            className="btn-secondary"
            onClick={() => document.getElementById('csv-import-sections').click()}
          >
            <i className="fas fa-file-csv"></i> Import CSV
          </button>
          <button className="btn-primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setNewSection({ sectionCode: '', program: 'BSCS', yearLevel: 1, expectedEnrollment: 60 }); }}>
            <i className="fas fa-plus"></i> Add Section
          </button>
        </div>
      </div>

      {showForm && (
        <div className="admin-form">
          <input 
            type="text" 
            placeholder="Section Code (e.g. BSCS-1A)" 
            value={newSection.sectionCode} 
            onChange={(e) => setNewSection({...newSection, sectionCode: e.target.value})} 
          />
          <select 
            value={newSection.program} 
            onChange={(e) => setNewSection({...newSection, program: e.target.value})}
          >
            <option value="BSCS">BSCS</option>
            <option value="BSInfoTech">BSInfoTech</option>
          </select>
          <input 
            type="number" 
            placeholder="Year Level" 
            value={newSection.yearLevel} 
            onChange={(e) => setNewSection({...newSection, yearLevel: parseInt(e.target.value)})} 
          />
          <input 
            type="number" 
            placeholder="Expected Enrollment" 
            value={newSection.expectedEnrollment} 
            onChange={(e) => setNewSection({...newSection, expectedEnrollment: parseInt(e.target.value)})} 
          />
          <button className="btn-primary" onClick={saveSection}>{editingId ? 'Update' : 'Save'}</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search sections by code or program..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Section Code</th>
              <th>Program</th>
              <th>Year Level</th>
              <th>Enrollment</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {sections.length === 0 ? (
              <tr>
                <td colSpan="5" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>
                  No sections found.
                </td>
              </tr>
            ) : (
              sections
                .filter(sec => 
                  sec.sectionCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  sec.program.toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(sec => (
                  <tr key={sec.id}>
                    <td><strong>{sec.sectionCode}</strong></td>
                    <td>{sec.program}</td>
                    <td>Year {sec.yearLevel}</td>
                    <td>{sec.expectedEnrollment}</td>
                    <td>
                      <button className="btn-edit" onClick={() => editSection(sec)}>
                        <i className="fas fa-edit"></i>
                      </button>
                      <button className="btn-delete" onClick={() => deleteSection(sec.id)}>
                        <i className="fas fa-trash"></i>
                      </button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
      <div className="table-footer">
        <span className="table-footer-text">
          <i className="fas fa-info-circle"></i> Total: {sections.length} sections
        </span>
      </div>
    </div>
  );
};

// ============================================================
// SUBJECT OFFERING MANAGEMENT
// ============================================================
const SubjectOfferingManagement = () => {
  const { toast, confirm } = useToast();
  const [offerings, setOfferings] = useState([]);
  const [sections, setSections] = useState([]);
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newOffering, setNewOffering] = useState({
    sectionId: '', courseId: '',
    semester: '1st Semester',
    academicYear: `${new Date().getFullYear()}-${new Date().getFullYear() + 1}`
  });

  useEffect(() => {
    fetchAll();
  }, []);

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [offRes, secRes, courRes] = await Promise.all([
        api.get('/api/admin/subject-offerings'),
        api.get('/api/admin/sections'),
        api.get('/api/admin/courses')
      ]);
      setOfferings(offRes.data || []);
      setSections(secRes.data || []);
      setCourses(courRes.data || []);
    } catch (error) {
      console.error('Error:', error);
      setOfferings([]);
    } finally {
      setLoading(false);
    }
  };

  const saveOffering = async () => {
    if (!newOffering.sectionId || !newOffering.courseId) {
      toast.warning('Missing Fields', 'Please select a section and course.');
      return;
    }

    try {
      const response = await api.post('/api/admin/subject-offerings', newOffering);
      setOfferings([...offerings, response.data]);
      setNewOffering({ 
        sectionId: '', courseId: '', 
        semester: '1st Semester', 
        academicYear: `${new Date().getFullYear()}-${new Date().getFullYear() + 1}`
      });
      setShowForm(false);
      toast.success('Subject Offering Added', 'The offering has been created.');
    } catch (error) {
      console.error('Error:', error);
      toast.error('Save Failed', error.response?.data?.error || error.message);
    }
  };

  const deleteOffering = async (id) => {
    const ok = await confirm({
      title: 'Delete Subject Offering?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/subject-offerings/${id}`);
      setOfferings(offerings.filter(o => o.id !== id));
      toast.success('Deleted', 'The subject offering has been removed.');
    } catch (error) {
      console.error('Error:', error);
      toast.error('Delete Failed', 'Could not delete the offering.');
    }
  };

  const getSectionCode = (off) => off.section?.sectionCode || 'N/A';
  const getCourseCode = (off) => off.course?.courseCode || 'N/A';
  const getCourseName = (off) => off.course?.courseName || 'N/A';

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading subject offerings...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-th-list"></i> Subject Offerings</h2>
        <button className="btn-primary" onClick={() => setShowForm(!showForm)}>
          <i className="fas fa-plus"></i> Add Subject Offering
        </button>
      </div>

      <p style={{ color: '#6f6f6f', marginBottom: '20px' }}>
        A subject offering is a course assigned to a specific section. This is what gets scheduled.
      </p>

      {showForm && (
        <div className="admin-form">
          <select 
            value={newOffering.sectionId} 
            onChange={(e) => setNewOffering({...newOffering, sectionId: e.target.value})}
          >
            <option value="">Select Section</option>
            {sections.map(s => (
              <option key={s.id} value={s.id}>{s.sectionCode} - {s.program}</option>
            ))}
          </select>

          <select 
            value={newOffering.courseId} 
            onChange={(e) => setNewOffering({...newOffering, courseId: e.target.value})}
          >
            <option value="">Select Course</option>
            {courses.map(c => (
              <option key={c.id} value={c.id}>{c.courseCode} - {c.courseName}</option>
            ))}
          </select>

          <select 
            value={newOffering.semester} 
            onChange={(e) => setNewOffering({...newOffering, semester: e.target.value})}
          >
            <option value="1st Semester">1st Semester</option>
            <option value="2nd Semester">2nd Semester</option>
            <option value="Summer">Summer</option>
          </select>

          <input 
            type="text" 
            placeholder="Academic Year (e.g. 2026-2027)" 
            value={newOffering.academicYear} 
            onChange={(e) => setNewOffering({...newOffering, academicYear: e.target.value})} 
          />

          <button className="btn-primary" onClick={saveOffering}>Save</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search by section, course, or faculty..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Section</th>
              <th>Course Code</th>
              <th>Course Name</th>
              <th>Semester</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {offerings.length === 0 ? (
              <tr>
                <td colSpan="5" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>
                  <i className="fas fa-info-circle" style={{ fontSize: '24px', marginBottom: '8px', display: 'block' }}></i>
                  No subject offerings found.
                </td>
              </tr>
            ) : (
              offerings
                .filter(off => 
                  getSectionCode(off).toLowerCase().includes(searchTerm.toLowerCase()) ||
                  getCourseCode(off).toLowerCase().includes(searchTerm.toLowerCase()) ||
                  getCourseName(off).toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(off => (
                  <tr key={off.id}>
                    <td><strong>{getSectionCode(off)}</strong></td>
                    <td><span className="course-code">{getCourseCode(off)}</span></td>
                    <td>{getCourseName(off)}</td>
                    <td>{off.semester}</td>
                    <td>
                      <button className="btn-delete" onClick={() => deleteOffering(off.id)}>
                        <i className="fas fa-trash"></i>
                      </button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
      <div className="table-footer">
        <span className="table-footer-text">
          <i className="fas fa-info-circle"></i> Total: {offerings.length} subject offerings
        </span>
      </div>
    </div>
  );
};
// ============================================================
// ACTIVITY LOG
// ============================================================
const ActivityLogContent = () => {
  const { toast } = useToast();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all');

  useEffect(() => { fetchLogs(); }, []);

  const fetchLogs = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/activity-logs');
      setLogs(response.data || []);
    } catch (error) {
      console.error('Error fetching logs:', error);
      setLogs([]);
    } finally {
      setLoading(false);
    }
  };

  const exportLogs = () => {
    const headers = ['User', 'Action', 'Details', 'Timestamp'];
    const rows = logs.map(log => [log.user, log.action, log.details, log.timestamp]);

    let csvContent = headers.join(',') + '\n';
    rows.forEach(row => {
      const escapedRow = row.map(field => {
        if (typeof field === 'string' && field.includes(',')) return `"${field}"`;
        return field;
      });
      csvContent += escapedRow.join(',') + '\n';
    });

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'activity_log.csv';
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Export Complete', 'Activity log downloaded as CSV.');
  };

  const getFilteredLogs = () => {
    if (filter === 'all') return logs;
    return logs.filter(log => log.action.toLowerCase().includes(filter.toLowerCase()));
  };

  const filteredLogs = getFilteredLogs();

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading activity logs...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-history"></i> Activity Log</h2>
        <div className="activity-filters">
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">All Actions</option>
            <option value="generate">Generate</option>
            <option value="added">Added</option>
            <option value="updated">Updated</option>
            <option value="deleted">Deleted</option>
          </select>
          <button className="btn-export" onClick={exportLogs}>
            <i className="fas fa-file-csv"></i> Export
          </button>
          <button className="btn-secondary" onClick={fetchLogs}>
            <i className="fas fa-sync"></i> Refresh
          </button>
        </div>
      </div>

      <div className="activity-log-table">
        <table className="admin-table">
          <thead>
            <tr><th>User</th><th>Action</th><th>Details</th><th>Timestamp</th></tr>
          </thead>
          <tbody>
            {filteredLogs.length === 0 ? (
              <tr><td colSpan="4" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>No activity logs found</td></tr>
            ) : (
              filteredLogs.map(log => (
                <tr key={log.id}>
                  <td><span className="user-email"><i className="fas fa-user"></i> {log.user}</span></td>
                  <td><span className="action-badge">{log.action}</span></td>
                  <td>{log.details}</td>
                  <td><span className="timestamp">{log.timestamp}</span></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="table-footer">
        <span className="table-footer-text">
          <i className="fas fa-info-circle"></i> Showing {filteredLogs.length} of {logs.length} logs
        </span>
      </div>
    </div>
  );
};

// ============================================================
// USER MANAGEMENT
// ============================================================
const UserManagement = () => {
  const { toast, confirm } = useToast();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [newUser, setNewUser] = useState({ email: '', name: '', role: 'faculty', password: '', status: 'active' });
  const [editingId, setEditingId] = useState(null);
  const roles = ['admin', 'faculty'];

  useEffect(() => { fetchUsers(); }, []);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/admin/users');
      setUsers(response.data);
    } catch (error) {
      console.error('Error fetching users:', error);
    } finally {
      setLoading(false);
    }
  };

  const saveUser = async () => {
    if (!newUser.email || !newUser.name || !newUser.role) {
      toast.warning('Missing Fields', 'Please fill in all required fields.');
      return;
    }
    if (!editingId && !newUser.password) {
      toast.warning('Password Required', 'Password is required for new users.');
      return;
    }
    try {
      let response;
      if (editingId) {
        response = await api.put(`/api/admin/users/${editingId}`, newUser);
        setUsers(users.map(u => u.id === editingId ? response.data : u));
        toast.success('User Updated', `${newUser.name} has been updated.`);
      } else {
        response = await api.post('/api/admin/users', newUser);
        setUsers([...users, response.data]);
        toast.success('User Added', `${newUser.name} has been created.`);
      }
      setNewUser({ email: '', name: '', role: 'faculty', password: '', status: 'active' });
      setShowForm(false);
      setEditingId(null);
    } catch (error) {
      console.error('Error saving user:', error);
      toast.error('Save Failed', error.response?.data?.error || 'Failed to save user');
    }
  };

  const editUser = (user) => {
    setNewUser({ email: user.email, name: user.name, role: user.role, password: '', status: user.status });
    setEditingId(user.id);
    setShowForm(true);
  };

  const deleteUser = async (id) => {
    const ok = await confirm({
      title: 'Delete User?',
      message: 'This action cannot be undone.',
      confirmText: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/admin/users/${id}`);
      setUsers(users.filter(u => u.id !== id));
      toast.success('User Deleted', 'The user has been removed.');
    } catch (error) {
      console.error('Error deleting user:', error);
      toast.error('Delete Failed', 'Could not delete the user.');
    }
  };

  const toggleStatus = async (id) => {
    const user = users.find(u => u.id === id);
    if (!user) return;
    const newStatus = user.status === 'active' ? 'inactive' : 'active';
    const ok = await confirm({
      title: newStatus === 'inactive' ? 'Deactivate User?' : 'Activate User?',
      message: `${newStatus === 'inactive' ? 'Deactivate' : 'Activate'} ${user.name}?`,
      confirmText: newStatus === 'inactive' ? 'Deactivate' : 'Activate',
      variant: 'warning',
    });
    if (!ok) return;
    try {
      await api.put(`/api/admin/users/${id}`, { ...user, status: newStatus });
      setUsers(users.map(u => u.id === id ? { ...u, status: newStatus } : u));
      toast.success('Status Changed', `${user.name} is now ${newStatus}.`);
    } catch (error) {
      console.error('Error updating user status:', error);
      toast.error('Update Failed', 'Could not update user status.');
    }
  };

  if (loading) return <div className="loading-state"><div className="spinner"></div><p>Loading users...</p></div>;

  return (
    <div>
      <div className="admin-header">
        <h2><i className="fas fa-user-cog"></i> User Management</h2>
        <button className="btn-primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setNewUser({ email: '', name: '', role: 'faculty', password: '', status: 'active' }); }}>
          <i className="fas fa-plus"></i> Add User
        </button>
      </div>

      {showForm && (
        <div className="admin-form">
          <input type="email" placeholder="Email" value={newUser.email} onChange={(e) => setNewUser({...newUser, email: e.target.value})} />
          <input type="text" placeholder="Full Name" value={newUser.name} onChange={(e) => setNewUser({...newUser, name: e.target.value})} />
          <select value={newUser.role} onChange={(e) => setNewUser({...newUser, role: e.target.value})}>
            {roles.map(role => <option key={role} value={role}>{role.charAt(0).toUpperCase() + role.slice(1)}</option>)}
          </select>
          <input type="password" placeholder={editingId ? "New Password (optional)" : "Password"} value={newUser.password} onChange={(e) => setNewUser({...newUser, password: e.target.value})} />
          <select value={newUser.status} onChange={(e) => setNewUser({...newUser, status: e.target.value})}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </select>
          <button className="btn-primary" onClick={saveUser}>{editingId ? 'Update' : 'Save'}</button>
          <button className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: '16px' }}>
        <input
          type="text"
          placeholder="🔍 Search by name, email, or role..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ width: '100%', padding: '12px 16px', border: '1px solid #d5cfc0', borderRadius: '8px', fontSize: '14px', fontFamily: 'Open Sauce One, sans-serif' }}
        />
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr><td colSpan="5" style={{ textAlign: 'center', padding: '40px', color: '#999' }}>No users found.</td></tr>
            ) : (
              users
                .filter(user =>
                  user.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  user.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
                  user.role.toLowerCase().includes(searchTerm.toLowerCase())
                )
                .map(user => (
                  <tr key={user.id}>
                    <td><strong>{user.name}</strong></td>
                    <td>{user.email}</td>
                    <td><span className={`role-badge ${user.role}`}>{user.role}</span></td>
                    <td><span className={`status-badge ${user.status}`}>{user.status}</span></td>
                    <td>
                      <button className="btn-edit" onClick={() => editUser(user)}><i className="fas fa-edit"></i></button>
                      <button className={`btn-toggle ${user.status}`} onClick={() => toggleStatus(user.id)}>
                        {user.status === 'active' ? 'Deactivate' : 'Activate'}
                      </button>
                      <button className="btn-delete" onClick={() => deleteUser(user.id)}><i className="fas fa-trash"></i></button>
                    </td>
                  </tr>
                ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminDashboard;