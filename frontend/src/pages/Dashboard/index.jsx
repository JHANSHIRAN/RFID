import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import { useAuth } from '../../context/AuthContext';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';
import { Users, Clock, LogIn, AlertCircle } from 'lucide-react';

const Dashboard = () => {
  const { user } = useAuth();
  const [liveSessions, setLiveSessions] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);

  const isManagerOrAdmin = ['MANAGER', 'ADMIN'].includes(user?.role);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [liveRes, analyticsRes] = await Promise.allSettled([
        api.get('/api/attendance/live'),
        api.get('/api/dashboard/analytics')
      ]);

      if (liveRes.status === 'fulfilled') {
        setLiveSessions(liveRes.value.data);
      }
      
      if (analyticsRes.status === 'fulfilled' && analyticsRes.value.data) {
        setAnalytics(analyticsRes.value.data);
      }
    } catch (error) {
      console.error("Error fetching dashboard data:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // Refresh every 30 seconds
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  const formatTime = (timeString) => {
    if (!timeString) return 'N/A';
    return new Date(timeString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const columns = [
    { header: 'Person', accessor: 'person', render: (row) => row.person?.fullName || 'Unknown' },
    { header: 'Role', accessor: 'groupLabel', render: (row) => <Badge>{row.person?.groupLabel}</Badge> },
    { header: 'Check In', accessor: 'checkInAt', render: (row) => formatTime(row.checkInAt) },
    { 
      header: 'Status', 
      accessor: 'late', 
      render: (row) => row.late 
        ? <Badge type="warning">Late</Badge> 
        : <Badge type="success">On Time</Badge>
    }
  ];

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', flexWrap: 'wrap', gap: '1rem' }}>
        <h1 style={{ margin: 0 }}>Live Dashboard</h1>
        
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <button className="btn btn-secondary" onClick={fetchData} disabled={loading}>
            {loading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>

      {analytics && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.5rem', marginBottom: '2rem' }}>
          <GlassCard>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ padding: '1rem', backgroundColor: 'rgba(59, 130, 246, 0.2)', borderRadius: '12px', color: 'var(--accent-primary)' }}>
                <Users size={24} />
              </div>
              <div>
                <p className="text-muted" style={{ margin: '0 0 0.25rem 0', fontSize: '0.875rem' }}>Currently In</p>
                <h2 style={{ margin: 0, fontSize: '1.5rem' }}>{liveSessions.length}</h2>
              </div>
            </div>
          </GlassCard>
          
          <GlassCard>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ padding: '1rem', backgroundColor: 'rgba(16, 185, 129, 0.2)', borderRadius: '12px', color: '#059669' }}>
                <LogIn size={24} />
              </div>
              <div>
                <p className="text-muted" style={{ margin: '0 0 0.25rem 0', fontSize: '0.875rem' }}>Present Today</p>
                <h2 style={{ margin: 0, fontSize: '1.5rem' }}>{analytics.presentToday || 0}</h2>
              </div>
            </div>
          </GlassCard>

          <GlassCard>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ padding: '1rem', backgroundColor: 'rgba(245, 158, 11, 0.2)', borderRadius: '12px', color: '#D97706' }}>
                <Clock size={24} />
              </div>
              <div>
                <p className="text-muted" style={{ margin: '0 0 0.25rem 0', fontSize: '0.875rem' }}>Late Arrivals</p>
                <h2 style={{ margin: 0, fontSize: '1.5rem' }}>{analytics.lateArrivals || 0}</h2>
              </div>
            </div>
          </GlassCard>

          <GlassCard>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ padding: '1rem', backgroundColor: 'rgba(239, 68, 68, 0.2)', borderRadius: '12px', color: '#DC2626' }}>
                <AlertCircle size={24} />
              </div>
              <div>
                <p className="text-muted" style={{ margin: '0 0 0.25rem 0', fontSize: '0.875rem' }}>Absentees</p>
                <h2 style={{ margin: 0, fontSize: '1.5rem' }}>{analytics.absentees || 0}</h2>
              </div>
            </div>
          </GlassCard>
        </div>
      )}

      <GlassCard title="Live Who's-In Board">
        <DataTable 
          columns={columns} 
          data={liveSessions} 
          loading={loading} 
          emptyMessage="No one is currently checked in." 
        />
      </GlassCard>
    </div>
  );
};

export default Dashboard;
