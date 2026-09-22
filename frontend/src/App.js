import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import LandingPage from './components/LandingPage';
import Login from './components/Login';
import ForgotPassword from './components/ForgotPassword';
import Dashboard from './components/Dashboard';
import AdminDashboard from './components/AdminDashboard';
import FacultyDashboard from './components/FacultyDashboard';
import FacultyProfile from './components/FacultyProfile';
import FacultyTimetable from './components/FacultyTimetable';
import PrintView from './components/PrintView';
import ProtectedRoute from './components/ProtectedRoute';
import { ToastProvider } from './components/Toast';
import './App.css';

function App() {
  return (
    <ToastProvider>
      <Router>
        <Routes>
          {/* Public Routes */}
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<Login />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />

          {/* Protected Routes */}
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />

          <Route path="/admin-dashboard" element={
            <ProtectedRoute requiredRole="admin">
              <AdminDashboard />
            </ProtectedRoute>
          } />

          <Route path="/print-schedule" element={
            <ProtectedRoute requiredRole="admin">
              <PrintView />
            </ProtectedRoute>
          } />

          <Route path="/faculty-dashboard" element={
            <ProtectedRoute requiredRole="faculty">
              <FacultyDashboard />
            </ProtectedRoute>
          } />

          <Route path="/faculty-timetable" element={
            <ProtectedRoute requiredRole="faculty">
              <FacultyTimetable />
            </ProtectedRoute>
          } />

          <Route path="/faculty-profile" element={
            <ProtectedRoute requiredRole="faculty">
              <FacultyProfile />
            </ProtectedRoute>
          } />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Router>
    </ToastProvider>
  );
}

export default App;