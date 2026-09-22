import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api';
import './Dashboard.css';

const PrintView = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [schedule, setSchedule] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filterSection, setFilterSection] = useState(searchParams.get('section') || 'all');
  const semester = searchParams.get('semester') || '1st Semester';
  const academicYear = searchParams.get('academicYear') || '2026-2027';

  useEffect(() => {
    fetchSchedule();
  }, []);

  const fetchSchedule = async () => {
    setLoading(true);
    try {
      const response = await api.get(
        `/api/schedule/view?semester=${encodeURIComponent(semester)}&academicYear=${academicYear}`
      );
      setSchedule(response.data.entries || []);
    } catch (error) {
      console.error('Error:', error);
    } finally {
      setLoading(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const groupedBySection = schedule.reduce((acc, entry) => {
    if (!acc[entry.section]) acc[entry.section] = [];
    acc[entry.section].push(entry);
    return acc;
  }, {});

  const sections = Object.keys(groupedBySection).sort();

  const displaySections = filterSection === 'all' 
    ? sections 
    : sections.filter(s => s === filterSection);

  if (loading) {
    return <div className="loading-state"><div className="spinner"></div><p>Loading...</p></div>;
  }

  return (
    <div style={{ padding: '20px', background: '#f5f5f5', minHeight: '100vh' }}>
      <div className="print-controls" style={{
        maxWidth: '210mm',
        margin: '0 auto 20px',
        background: '#fff',
        padding: '16px',
        borderRadius: '8px',
        display: 'flex',
        gap: '12px',
        alignItems: 'center',
        flexWrap: 'wrap'
      }}>
        <button 
          onClick={() => navigate(-1)}
          style={{
            padding: '10px 20px',
            background: '#f5f5f5',
            border: '1px solid #ddd',
            borderRadius: '6px',
            cursor: 'pointer',
            fontFamily: 'Open Sauce One, sans-serif'
          }}
        >
          <i className="fas fa-arrow-left"></i> Back
        </button>
        
        <select 
          value={filterSection} 
          onChange={(e) => setFilterSection(e.target.value)}
          style={{
            padding: '10px 16px',
            border: '1px solid #ddd',
            borderRadius: '6px',
            fontSize: '14px',
            fontFamily: 'Open Sauce One, sans-serif'
          }}
        >
          <option value="all">All Sections</option>
          {sections.map(sec => (
            <option key={sec} value={sec}>{sec}</option>
          ))}
        </select>

        <button 
          onClick={handlePrint}
          style={{
            padding: '10px 24px',
            background: '#1a1a1a',
            color: '#fff',
            border: 'none',
            borderRadius: '6px',
            cursor: 'pointer',
            fontWeight: '600',
            fontFamily: 'Open Sauce One, sans-serif'
          }}
        >
          <i className="fas fa-print"></i> Print
        </button>
      </div>

      <div className="print-area" style={{
        maxWidth: '210mm',
        margin: '0 auto',
        background: '#fff',
        padding: '15mm',
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
      }}>
        <div style={{ textAlign: 'center', marginBottom: '20px', borderBottom: '2px solid #1a1a1a', paddingBottom: '12px' }}>
          <h1 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '4px' }}>
            EARIST COLLEGE OF COMPUTING STUDIES
          </h1>
          <h2 style={{ fontSize: '14px', fontWeight: '500', marginBottom: '4px' }}>
            Subject Offering & Faculty Loading
          </h2>
          <p style={{ fontSize: '12px', color: '#666' }}>
            {semester}, Academic Year {academicYear}
          </p>
          <p style={{ fontSize: '10px', color: '#999', marginTop: '4px' }}>
            Generated: {new Date().toLocaleString()}
          </p>
        </div>

        {displaySections.map(section => (
          <div key={section} style={{ marginBottom: '24px', pageBreakInside: 'avoid' }}>
            <h3 style={{
              fontSize: '13px',
              fontWeight: '700',
              background: '#f0f0f0',
              padding: '6px 10px',
              marginBottom: '8px',
              borderLeft: '4px solid #1a1a1a'
            }}>
              Section: {section}
            </h3>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '10px' }}>
              <thead>
                <tr style={{ background: '#1a1a1a', color: '#fff' }}>
                  <th style={{ padding: '6px', border: '1px solid #333', textAlign: 'left' }}>Course</th>
                  <th style={{ padding: '6px', border: '1px solid #333', textAlign: 'left' }}>Description</th>
                  <th style={{ padding: '6px', border: '1px solid #333', textAlign: 'center' }}>Day</th>
                  <th style={{ padding: '6px', border: '1px solid #333', textAlign: 'center' }}>Time</th>
                  <th style={{ padding: '6px', border: '1px solid #333', textAlign: 'center' }}>Room</th>
                  <th style={{ padding: '6px', border: '1px solid #333', textAlign: 'left' }}>Faculty</th>
                </tr>
              </thead>
              <tbody>
                {groupedBySection[section].map((entry, i) => (
                  <tr key={i} style={{ background: i % 2 === 0 ? '#fafafa' : '#fff' }}>
                    <td style={{ padding: '6px', border: '1px solid #ccc', fontWeight: '600' }}>{entry.courseCode}</td>
                    <td style={{ padding: '6px', border: '1px solid #ccc' }}>{entry.courseName}</td>
                    <td style={{ padding: '6px', border: '1px solid #ccc', textAlign: 'center' }}>{entry.day}</td>
                    <td style={{ padding: '6px', border: '1px solid #ccc', textAlign: 'center' }}>{entry.time}</td>
                    <td style={{ padding: '6px', border: '1px solid #ccc', textAlign: 'center' }}>{entry.room}</td>
                    <td style={{ padding: '6px', border: '1px solid #ccc' }}>{entry.faculty}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}

        <div style={{ marginTop: '30px', textAlign: 'center', fontSize: '10px', color: '#666', borderTop: '1px solid #ccc', paddingTop: '12px' }}>
          <p>Total Sections: {displaySections.length} | Total Classes: {displaySections.reduce((sum, s) => sum + groupedBySection[s].length, 0)}</p>
          <p style={{ marginTop: '4px' }}>This document is system-generated. For official use only.</p>
        </div>
      </div>

      <style>{`
        @media print {
          body * { visibility: hidden; }
          .print-area, .print-area * { visibility: visible; }
          .print-area {
            position: absolute;
            left: 0;
            top: 0;
            box-shadow: none !important;
            padding: 0 !important;
          }
          .print-controls { display: none !important; }
          @page {
            size: A4 portrait;
            margin: 15mm;
          }
        }
      `}</style>
    </div>
  );
};

export default PrintView;