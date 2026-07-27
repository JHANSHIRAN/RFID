import React, { useState, useEffect } from 'react';
import { Outlet, NavLink, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/api';
import { LayoutDashboard, Users, CreditCard, Settings, Activity, Clock, LogOut, Menu, X, Link, Calendar, BarChart, UserCheck, FileText, KeyRound, Bell } from 'lucide-react';

const ProtectedRoute = ({ children, requireRole }) => {
  const { user, isAuthenticated } = useAuth();
  const location = useLocation();
  
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  
  // Force change password, but avoid infinite loop if already on the page
  if (user.password_change_required && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  
  if (requireRole) {
    const roles = ['OPERATOR', 'MANAGER', 'ADMIN'];
    const userLevel = roles.indexOf(user.role);
    const requiredLevel = roles.indexOf(requireRole);
    
    if (userLevel < requiredLevel) {
      return <div className="p-4"><div className="glass-card p-4 text-center text-danger">Access Denied</div></div>;
    }
  }
  
  return children;
};

const MainLayout = () => {
  const { user, logout } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  
  const [selectedCard, setSelectedCard] = useState('');
  
  const [notifications, setNotifications] = useState([]);
  const [showNotifications, setShowNotifications] = useState(false);

  useEffect(() => {
    if (['ADMIN', 'MANAGER'].includes(user?.role)) {
      fetchNotifications();
      // Optional: poll every minute
      const interval = setInterval(fetchNotifications, 60000);
      return () => clearInterval(interval);
    }
  }, [user]);

  const fetchNotifications = async () => {
    try {
      const res = await api.get('/api/notifications');
      setNotifications(res.data);
    } catch (err) {
      console.error('Failed to fetch notifications', err);
    }
  };

  const markAsRead = async (id, currentReadStatus) => {
    if (currentReadStatus) return; // already read
    try {
      await api.patch(`/api/notifications/${id}/read`);
      setNotifications(notifications.map(n => n.id === id ? { ...n, read: true } : n));
    } catch (err) {
      console.error('Failed to mark read', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await api.patch('/api/notifications/read-all');
      setNotifications(notifications.map(n => ({ ...n, read: true })));
    } catch (err) {
      console.error('Failed to mark all read', err);
    }
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  const toggleSidebar = () => setSidebarOpen(!sidebarOpen);

  const manualTap = async (direction) => {
    if (!selectedCard) {
      alert('Please select a card first.');
      return;
    }
    try {
      await api.post(`/api/attendance/manual-tap?cardUid=${selectedCard}&direction=${direction}`);
      alert(`Successfully checked ${direction.toLowerCase()}`);
      // Refresh the page or let the child components handle their own refresh
      window.location.reload(); 
    } catch (err) {
      alert(err.message || 'Failed to perform manual tap');
    }
  };

  // Helper to determine if current user can see this menu item
  const canView = (minRole) => {
    const roles = ['OPERATOR', 'MANAGER', 'ADMIN'];
    return roles.indexOf(user?.role) >= roles.indexOf(minRole);
  };

  const navItems = [
    { path: '/dashboard', label: 'Live Board', icon: <Activity size={20} />, minRole: 'OPERATOR' },
    { path: '/people', label: 'People', icon: <Users size={20} />, minRole: 'OPERATOR' },
    { path: '/cards', label: 'Cards', icon: <CreditCard size={20} />, minRole: 'OPERATOR' },
    { path: '/mappings', label: 'Card Mappings', icon: <Link size={20} />, minRole: 'OPERATOR' },
    { path: '/attendance', label: 'Attendance', icon: <Calendar size={20} />, minRole: 'MANAGER' },
    { path: '/reports', label: 'Reports', icon: <BarChart size={20} />, minRole: 'MANAGER' },
    { path: '/staff', label: 'Staff Users', icon: <UserCheck size={20} />, minRole: 'MANAGER' },
    { path: '/audit', label: 'Audit Logs', icon: <FileText size={20} />, minRole: 'MANAGER' },
    { path: '/settings', label: 'Settings', icon: <Settings size={20} />, minRole: 'ADMIN' },
  ];

  return (
    <div style={{ 
      display: 'flex', 
      minHeight: '100vh', 
      backgroundColor: 'var(--bg-dashboard)',
      backgroundImage: 'radial-gradient(circle at 15% 50%, rgba(244, 114, 182, 0.08), transparent 25%), radial-gradient(circle at 85% 30%, rgba(251, 113, 133, 0.08), transparent 25%)'
    }}>
      {/* Mobile Sidebar Overlay */}
      {sidebarOpen && (
        <div 
          style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 40 }}
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside 
        className="glass-panel"
        style={{ 
          position: 'fixed', 
          top: 0, 
          bottom: 0, 
          left: 0, 
          width: '250px', 
          zIndex: 50, 
          backgroundColor: 'rgba(255, 255, 255, 0.65)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)',
          transition: 'transform 0.3s ease',
          display: 'flex',
          flexDirection: 'column',
          borderRight: '1px solid var(--border-color)',
          borderRadius: 0,
          /* Desktop behavior */
          ...(window.innerWidth > 768 ? { transform: 'none', position: 'sticky', height: '100vh' } : {})
        }}
      >
        <div style={{ padding: '1.5rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-light)' }}>
          <h2 style={{ fontSize: '1.25rem', margin: 0, background: 'linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            AccessTrack
          </h2>
          <button className="btn btn-secondary" style={{ padding: '0.25rem', border: 'none', background: 'transparent' }} onClick={() => setSidebarOpen(false)}>
            <X size={20} className="d-md-none" style={{ display: window.innerWidth > 768 ? 'none' : 'block' }} />
          </button>
        </div>

        <div style={{ padding: '1rem', flex: 1, display: 'flex', flexDirection: 'column', gap: '0.5rem', overflowY: 'auto' }}>


          {navItems.filter(item => canView(item.minRole)).map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => `btn ${isActive ? 'btn-primary' : 'btn-secondary'}`}
              style={({ isActive }) => ({
                justifyContent: 'flex-start', 
                padding: '0.75rem 1rem',
                backgroundColor: isActive ? '' : 'transparent',
                border: isActive ? '' : 'none',
                boxShadow: isActive ? '' : 'none',
              })}
              onClick={() => { if(window.innerWidth <= 768) setSidebarOpen(false); }}
            >
              {item.icon} {item.label}
            </NavLink>
          ))}
        </div>


        <div style={{ padding: '1rem', borderTop: '1px solid var(--border-light)', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <div style={{ padding: '0.5rem 1rem', marginBottom: '0.5rem', overflow: 'hidden' }}>
            <div style={{ fontSize: '0.875rem', fontWeight: 600, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>{user?.email}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Role: {user?.role}</div>
          </div>
          
          <NavLink 
            to="/change-password" 
            className="btn btn-secondary" 
            style={{ justifyContent: 'flex-start', padding: '0.75rem 1rem', background: 'transparent', border: 'none', boxShadow: 'none' }}
            onClick={() => { if(window.innerWidth <= 768) setSidebarOpen(false); }}
          >
            <KeyRound size={16} /> <span style={{ marginLeft: '0.5rem' }}>Change Password</span>
          </NavLink>
          
          <button className="btn btn-secondary" style={{ justifyContent: 'flex-start', padding: '0.75rem 1rem', background: 'transparent', border: 'none', boxShadow: 'none' }} onClick={logout}>
            <LogOut size={16} style={{ color: 'var(--danger)' }} /> 
            <span style={{ marginLeft: '0.5rem', color: 'var(--danger)' }}>Log out</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {/* Header */}
        <header 
          className="glass-panel" 
          style={{ 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'space-between',
            padding: '1rem 2rem',
            borderBottom: '1px solid var(--border-color)',
            borderRadius: 0,
            position: 'sticky',
            top: 0,
            zIndex: 30
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <button className="btn btn-secondary" style={{ padding: '0.5rem', border: 'none', background: 'transparent', display: window.innerWidth > 768 ? 'none' : 'block' }} onClick={toggleSidebar}>
              <Menu size={24} />
            </button>
            <h2 style={{ fontSize: '1.25rem', margin: '0 0 0 1rem', display: window.innerWidth > 768 ? 'none' : 'block' }}>AccessTrack</h2>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            
            {/* Notification Bell */}
            {['ADMIN', 'MANAGER'].includes(user?.role) && (
              <div style={{ position: 'relative' }}>
                <button 
                  className="btn btn-secondary" 
                  style={{ padding: '0.5rem', border: 'none', background: 'transparent', position: 'relative' }} 
                  onClick={() => setShowNotifications(!showNotifications)}
                >
                  <Bell size={20} />
                  {unreadCount > 0 && (
                    <span style={{
                      position: 'absolute',
                      top: '2px',
                      right: '2px',
                      backgroundColor: 'var(--danger)',
                      color: 'white',
                      borderRadius: '50%',
                      width: '18px',
                      height: '18px',
                      fontSize: '0.7rem',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 'bold'
                    }}>
                      {unreadCount}
                    </span>
                  )}
                </button>
                
                {/* Notification Dropdown */}
                {showNotifications && (
                  <div className="glass-card" style={{
                    position: 'absolute',
                    top: '100%',
                    right: '0',
                    marginTop: '0.5rem',
                    width: '320px',
                    maxHeight: '400px',
                    overflowY: 'auto',
                    zIndex: 100,
                    padding: '0',
                    boxShadow: '0 10px 25px rgba(0,0,0,0.5)'
                  }}>
                    <div style={{ padding: '1rem', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <h3 style={{ margin: 0, fontSize: '1rem' }}>Notifications</h3>
                      {unreadCount > 0 && (
                        <button className="btn btn-secondary" style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', background: 'transparent', border: 'none' }} onClick={markAllAsRead}>
                          Mark all as read
                        </button>
                      )}
                    </div>
                    
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      {notifications.length === 0 ? (
                        <div style={{ padding: '1.5rem', textAlign: 'center', color: 'var(--text-muted)' }}>No notifications</div>
                      ) : (
                        notifications.map(notif => (
                          <div 
                            key={notif.id} 
                            onClick={() => markAsRead(notif.id, notif.read)}
                            style={{ 
                              padding: '1rem', 
                              borderBottom: '1px solid var(--border-light)',
                              backgroundColor: notif.read ? 'transparent' : 'rgba(239, 68, 68, 0.1)',
                              cursor: notif.read ? 'default' : 'pointer',
                              transition: 'background-color 0.2s'
                            }}
                          >
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                              <span style={{ fontSize: '0.75rem', fontWeight: 'bold', color: notif.type === 'ALERT' ? 'var(--danger)' : 'var(--accent-primary)' }}>
                                {notif.type}
                              </span>
                              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                {new Date(notif.createdAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                              </span>
                            </div>
                            <div style={{ fontSize: '0.875rem', lineHeight: '1.4' }}>
                              {notif.message}
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
            
            {/* Global Manual Tap Controls */}
            {['OPERATOR', 'MANAGER', 'ADMIN'].includes(user?.role) && (
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                <input 
                  type="text"
                  className="form-input" 
                  value={selectedCard} 
                  onChange={(e) => setSelectedCard(e.target.value)}
                  placeholder="Enter Card UID"
                  style={{ minWidth: '150px', padding: '0.5rem', fontSize: '0.875rem' }}
                />
                <button className="btn btn-primary" style={{ padding: '0.5rem 1rem', fontSize: '0.875rem' }} onClick={() => manualTap('IN')}>
                  Check In
                </button>
                <button className="btn btn-secondary" style={{ padding: '0.5rem 1rem', fontSize: '0.875rem' }} onClick={() => manualTap('OUT')}>
                  Check Out
                </button>
              </div>
            )}

          </div>
        </header>

        <div style={{ padding: '2rem', flex: 1, overflowY: 'auto' }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export { MainLayout, ProtectedRoute };
