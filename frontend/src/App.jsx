import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { MainLayout, ProtectedRoute } from './layouts/MainLayout';

// Page Imports
import Login from './pages/Login';
import ChangePassword from './pages/Login/ChangePassword';
import Dashboard from './pages/Dashboard';
import People from './pages/People';
import Cards from './pages/Cards';
import CardMappings from './pages/CardMappings';
import Attendance from './pages/Attendance';
import Reports from './pages/Reports';
import StaffUsers from './pages/StaffUsers';
import AuditLogs from './pages/AuditLogs';
import Settings from './pages/Settings';

const NotFound = () => <div className="glass-panel" style={{padding: '2rem'}}><h2>404 Not Found</h2></div>;

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/change-password" element={<ProtectedRoute><ChangePassword /></ProtectedRoute>} />
          
          <Route element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
            <Route path="/dashboard" element={<Dashboard />} />
            
            <Route path="/people" element={<People />} />
            <Route path="/cards" element={<Cards />} />
            <Route path="/mappings" element={<CardMappings />} />
            <Route path="/attendance" element={<Attendance />} />
            
            <Route path="/reports" element={<ProtectedRoute requireRole="MANAGER"><Reports /></ProtectedRoute>} />
            <Route path="/staff" element={<ProtectedRoute requireRole="MANAGER"><StaffUsers /></ProtectedRoute>} />
            <Route path="/audit" element={<ProtectedRoute requireRole="MANAGER"><AuditLogs /></ProtectedRoute>} />
            <Route path="/settings" element={<ProtectedRoute requireRole="ADMIN"><Settings /></ProtectedRoute>} />
          </Route>
          
          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
