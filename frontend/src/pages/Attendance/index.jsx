import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';

const Attendance = () => {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchEvents = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/events');
      setEvents(res.data);
    } catch (err) {
      console.error("Failed to fetch events:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  const columns = [
    { header: 'Time', accessor: 'occurredAt', render: (row) => new Date(row.occurredAt).toLocaleString() },
    { header: 'Type', accessor: 'eventType', render: (row) => (
        <Badge type={row.eventType === 'CHECK_IN' ? 'primary' : row.eventType === 'CHECK_OUT' ? 'warning' : 'default'}>
          {row.eventType || 'UNKNOWN'}
        </Badge>
      ) 
    },
    { header: 'Card UID', accessor: 'cardUid', render: (row) => <span style={{fontFamily: 'monospace'}}>{row.cardUid}</span> },
    { header: 'Person', accessor: 'person', render: (row) => row.person ? row.person.fullName : <span className="text-muted">Unmapped/Unknown</span> },
    { header: 'Decision', accessor: 'decision', render: (row) => (
        <Badge type={row.decision === 'GRANTED' ? 'success' : 'danger'}>
          {row.decision}
        </Badge>
      ) 
    },
    { header: 'Source', accessor: 'source' },
    { header: 'Reason/Message', accessor: 'reason' }
  ];

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1 style={{ margin: 0 }}>Tap Events</h1>
        <button className="btn btn-secondary" onClick={fetchEvents} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      <GlassCard>
        <DataTable 
          columns={columns} 
          data={events} 
          loading={loading} 
          emptyMessage="No tap events recorded." 
        />
      </GlassCard>
    </div>
  );
};

export default Attendance;
