import React from 'react';
import { Navigate } from 'react-router-dom';

const ProtectedRoute = ({ children, requiredRole }) => {
  const user = localStorage.getItem('user');
  
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  
  const userData = JSON.parse(user);
  
  // If a specific role is required
  if (requiredRole && userData.role !== requiredRole) {
    if (userData.role === 'admin') {
      return <Navigate to="/admin-dashboard" replace />;
    } else if (userData.role === 'faculty') {
      return <Navigate to="/faculty-dashboard" replace />;
    }
    return <Navigate to="/dashboard" replace />;
  }
  
  return children;
};

export default ProtectedRoute;