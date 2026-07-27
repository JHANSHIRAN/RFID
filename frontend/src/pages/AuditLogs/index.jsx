import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';

const AuditLogs = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const fetchLogs = async (pageNumber) => {
    setLoading(true);
    try {
      const res = await api.get(`/api/audit-log?page=${pageNumber}&size=15`);
      setLogs(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      console.error("Failed to fetch audit logs:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs(page);
  }, [page]);

  const columns = [
    { header: 'Time', accessor: 'timestamp', render: (row) => new Date(row.timestamp).toLocaleString() },
    { 
      header: 'Action', 
      accessor: 'actionType',
      render: (row) => <Badge type="primary">{row.actionType}</Badge>
    },
    { header: 'Target Type', accessor: 'targetEntity' },
    { header: 'Target ID', accessor: 'targetId' },
    { header: 'Performed By', accessor: 'actor', render: (row) => row.actor ? row.actor.email : 'System' },
    { header: 'Details', accessor: 'ipAddress', render: (row) => <span className="text-muted">{row.ipAddress || '-'}</span> },
  ];

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1 style={{ margin: 0 }}>System Audit Logs</h1>
        <button className="btn btn-secondary" onClick={() => fetchLogs(page)} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      <GlassCard>
        <DataTable 
          columns={columns} 
          data={logs} 
          loading={loading} 
          emptyMessage="No audit logs found." 
        />
        
        {totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1.5rem', padding: '0 1rem' }}>
            <button 
              className="btn btn-secondary" 
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0 || loading}
            >
              Previous
            </button>
            <span className="text-muted">Page {page + 1} of {totalPages}</span>
            <button 
              className="btn btn-secondary" 
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || loading}
            >
              Next
            </button>
          </div>
        )}
      </GlassCard>
    </div>
  );
};

export default AuditLogs;
