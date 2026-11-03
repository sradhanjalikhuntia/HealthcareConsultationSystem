import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import AppLayout from './layouts/AppLayout';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Patients from './pages/Patients';
import PatientDetails from './pages/PatientDetails';
import Appointments from './pages/Appointments';
import Billing from './pages/Billing';
import Admin from './pages/admin/Admin';
import Pharmacy from './pages/Pharmacy';
import Labs from './pages/Labs';
import Wards from './pages/Wards';

const ProtectedRoute: React.FC<{
  children: React.ReactNode;
  allowedRoles?: string[];
  adminOnly?: boolean;
}> = ({ children, allowedRoles, adminOnly }) => {
  const { isAuthenticated, loading, user } = useAuth();
  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-2)' }}>
      Loading…
    </div>
  );
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (adminOnly && user?.role !== 'ADMIN') return <Navigate to="/dashboard" replace />;
  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
};

function AppRoutes() {
  const staffRoles = ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN'];
  const clinicalRoles = ['ADMIN', 'DOCTOR', 'NURSE'];
  const billingRoles = ['ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT'];

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/patients" element={<ProtectedRoute allowedRoles={staffRoles}><Patients /></ProtectedRoute>} />
        <Route path="/patients/:id" element={<ProtectedRoute allowedRoles={staffRoles}><PatientDetails /></ProtectedRoute>} />
        <Route path="/appointments" element={<Appointments />} />
        <Route path="/billing" element={<ProtectedRoute allowedRoles={billingRoles}><Billing /></ProtectedRoute>} />
        <Route path="/pharmacy" element={<ProtectedRoute allowedRoles={clinicalRoles}><Pharmacy /></ProtectedRoute>} />
        <Route path="/labs" element={<ProtectedRoute allowedRoles={clinicalRoles}><Labs /></ProtectedRoute>} />
        <Route path="/wards" element={<ProtectedRoute allowedRoles={staffRoles}><Wards /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute adminOnly><Admin /></ProtectedRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </ToastProvider>
  );
}

export default App;
