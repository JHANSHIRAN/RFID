import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../../api/api';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';
import { Download, BarChart2, List, Users, Clock, AlertCircle, Calendar, TrendingUp } from 'lucide-react';

// ─── helpers ──────────────────────────────────────────────────────────────────
const fmt = (iso) => iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--:--';
const pct  = (n, d) => d > 0 ? ((n / d) * 100).toFixed(0) + '%' : '—';

const MiniBar = ({ value, max, color }) => {
  const w = max > 0 ? Math.min(100, (value / max) * 100) : 0;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
      <div style={{ flex: 1, height: 6, background: 'rgba(0,0,0,0.08)', borderRadius: 99, overflow: 'hidden' }}>
        <div style={{ width: `${w}%`, height: '100%', background: color, borderRadius: 99, transition: 'width 0.4s ease' }} />
      </div>
      <span style={{ fontSize: '0.75rem', fontWeight: 600, color, minWidth: 28, textAlign: 'right' }}>{value}</span>
    </div>
  );
};

const StatCard = ({ icon: Icon, label, value, sub, color }) => (
  <div style={{
    background: 'rgba(255,255,255,0.5)',
    border: '1px solid rgba(255,255,255,0.6)',
    borderRadius: 12,
    padding: '1rem 1.25rem',
    display: 'flex', alignItems: 'center', gap: '1rem',
    backdropFilter: 'blur(8px)'
  }}>
    <div style={{ background: color + '22', borderRadius: 10, padding: '0.6rem', display: 'flex' }}>
      <Icon size={20} color={color} />
    </div>
    <div>
      <div style={{ fontSize: '1.4rem', fontWeight: 700, lineHeight: 1, color: 'var(--text-primary)' }}>{value}</div>
      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 2 }}>{label}</div>
      {sub && <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: 1 }}>{sub}</div>}
    </div>
  </div>
);

// ─── session columns ───────────────────────────────────────────────────────────
const sessionColumns = [
  { header: 'Date',      accessor: 'workDate' },
  { header: 'Person',    accessor: 'person',    render: (r) => r.person?.fullName },
  { header: 'Group',     accessor: 'person',    render: (r) => <Badge>{r.person?.groupLabel}</Badge> },
  { header: 'Check In',  accessor: 'checkInAt', render: (r) => fmt(r.checkInAt) },
  { header: 'Check Out', accessor: 'checkOutAt',render: (r) => fmt(r.checkOutAt) },
  { header: 'Hours',     accessor: 'duration',  render: (r) => r.durationMinutes != null ? (r.durationMinutes / 60).toFixed(1) + 'h' : '—' },
  { header: 'Late',      accessor: 'late',      render: (r) => r.late ? <Badge type="danger">Late</Badge> : <Badge type="success">On Time</Badge> },
  { header: 'Status',    accessor: 'status',    render: (r) => (
    <Badge type={r.status === 'CLOSED' ? 'success' : r.status === 'OPEN' ? 'primary' : 'danger'}>
      {r.status}
    </Badge>
  )},
];

// ─── per-person columns ────────────────────────────────────────────────────────
const buildPersonColumns = (maxDays) => [
  { header: 'Name',        accessor: 'fullName',      render: (r) => <strong>{r.fullName}</strong> },
  { header: 'Group',       accessor: 'groupLabel',    render: (r) => <Badge>{r.groupLabel}</Badge> },
  { header: 'Type',        accessor: 'memberType',    render: (r) => <Badge type="primary">{r.memberType}</Badge> },
  { header: 'Present',     accessor: 'daysPresent',   render: (r) => (
    <MiniBar value={r.daysPresent} max={r.workingDaysInRange} color="#10B981" />
  )},
  { header: 'Full Days',   accessor: 'daysMetMinimumHours', render: (r) => (
    <MiniBar value={r.daysMetMinimumHours} max={r.workingDaysInRange} color="#3B82F6" />
  )},
  { header: 'Absent',      accessor: 'daysAbsent',    render: (r) => (
    r.daysAbsent > 0
      ? <Badge type="danger">{r.daysAbsent}d</Badge>
      : <Badge type="success">0</Badge>
  )},
  { header: 'Total Hours', accessor: 'totalHours',    render: (r) => (
    <span style={{ fontWeight: 600 }}>{r.totalHours.toFixed(1)}<span style={{ fontWeight: 400, color: 'var(--text-muted)', fontSize: '0.75rem' }}>h</span></span>
  )},
  { header: 'Late Days',   accessor: 'lateCount',     render: (r) => (
    r.lateCount > 0
      ? <Badge type="warning">{r.lateCount}</Badge>
      : <span style={{ color: 'var(--text-muted)' }}>—</span>
  )},
  { header: 'Missed CO',   accessor: 'missedCheckOuts', render: (r) => (
    r.missedCheckOuts > 0
      ? <Badge type="danger">{r.missedCheckOuts}</Badge>
      : <span style={{ color: 'var(--text-muted)' }}>—</span>
  )},
  { header: 'Attendance %',  accessor: 'attendancePct', render: (r) => {
    const p = r.attendancePct;
    const color = p >= 90 ? '#10B981' : p >= 75 ? '#F59E0B' : '#EF4444';
    return (
      <span
        title={`${r.totalHours} hrs logged / ${r.expectedHours} expected hrs\n(${r.daysPresent} days physically present)`}
        style={{ fontWeight: 700, color, cursor: 'help' }}
      >
        {p}%
      </span>
    );
  }},
];

// ─── component ────────────────────────────────────────────────────────────────
const Reports = () => {
  const today = new Date().toISOString().split('T')[0];
  const [startDate,  setStartDate]  = useState(today);
  const [endDate,    setEndDate]    = useState(today);
  const [groupLabel, setGroupLabel] = useState('');
  const [view,       setView]       = useState('summary'); // 'summary' | 'sessions'

  const [sessions,   setSessions]   = useState([]);
  const [summary,    setSummary]    = useState([]);
  const [loading,    setLoading]    = useState(false);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const qs     = `startDate=${startDate}&endDate=${endDate}`;
      const grpQs  = groupLabel ? `&groupLabel=${encodeURIComponent(groupLabel)}` : '';

      const [sessionRes, summaryRes] = await Promise.all([
        api.get(`/api/attendance/report?${qs}${grpQs}`),
        api.get(`/api/attendance/per-person-report?${qs}${grpQs}`),
      ]);
      setSessions(sessionRes.data  || []);
      setSummary(summaryRes.data   || []);
    } catch (err) {
      console.error('Failed to fetch report:', err);
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate, groupLabel]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // ── export CSV ───────────────────────────────────────────────────────────
  const handleExport = () => {
    const qs    = `startDate=${startDate}&endDate=${endDate}`;
    const grpQs = groupLabel ? `&groupLabel=${encodeURIComponent(groupLabel)}` : '';
    fetch(`http://localhost:8081/api/attendance/report/export?${qs}${grpQs}`, {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` }
    })
      .then(r => { if (!r.ok) throw new Error(); return r.blob(); })
      .then(blob => {
        const url = window.URL.createObjectURL(blob);
        const a   = document.createElement('a');
        a.href    = url; a.download = `attendance_${startDate}_to_${endDate}.csv`;
        document.body.appendChild(a); a.click();
        window.URL.revokeObjectURL(url);
      })
      .catch(() => alert('Failed to download report'));
  };

  // ── derived summary stats ────────────────────────────────────────────────
  const totalPresent   = summary.reduce((a, r) => a + r.daysPresent, 0);
  const totalAbsent    = summary.reduce((a, r) => a + r.daysAbsent,  0);
  const totalHours     = summary.reduce((a, r) => a + r.totalHours,  0);
  const totalExpected  = summary.reduce((a, r) => a + r.expectedHours, 0);
  const totalLate      = summary.reduce((a, r) => a + r.lateCount,   0);
  const totalMissed    = summary.reduce((a, r) => a + r.missedCheckOuts, 0);
  const workingDays    = summary.length > 0 ? summary[0].workingDaysInRange : 0;
  const overallPct     = totalExpected > 0 ? Math.round((totalHours / totalExpected) * 100) : 0;

  const maxDays = summary.length > 0 ? summary[0].workingDaysInRange : 1;

  return (
    <div className="animate-fade-in">
      {/* ── header ── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>Attendance Reports</h1>
          <p style={{ margin: '0.25rem 0 0', color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            {summary.length} people · {workingDays} working days
          </p>
        </div>
        <button className="btn btn-secondary" onClick={handleExport} disabled={loading}>
          <Download size={16} /> Export CSV
        </button>
      </div>

      {/* ── filters ── */}
      <GlassCard style={{ marginBottom: '1.5rem', padding: '1rem 1.25rem' }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div className="form-group" style={{ flex: 1, minWidth: 140, marginBottom: 0 }}>
            <label className="form-label">Start Date</label>
            <input type="date" className="form-input" value={startDate} onChange={e => setStartDate(e.target.value)} />
          </div>
          <div className="form-group" style={{ flex: 1, minWidth: 140, marginBottom: 0 }}>
            <label className="form-label">End Date</label>
            <input type="date" className="form-input" value={endDate} onChange={e => setEndDate(e.target.value)} />
          </div>
          <div className="form-group" style={{ flex: 1, minWidth: 140, marginBottom: 0 }}>
            <label className="form-label">Group (optional)</label>
            <input type="text" className="form-input" placeholder="e.g. Engineering" value={groupLabel} onChange={e => setGroupLabel(e.target.value)} />
          </div>
          <button className="btn btn-primary" onClick={fetchAll} disabled={loading} style={{ marginBottom: 0 }}>
            {loading ? 'Loading…' : 'Apply'}
          </button>
        </div>
      </GlassCard>

      {/* ── summary stat cards ── */}
      {summary.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '0.75rem', marginBottom: '1.5rem' }}>
          <StatCard icon={TrendingUp}    label="Overall Attendance" value={`${overallPct}%`}                color="#3B82F6" />
          <StatCard icon={Users}         label="People Tracked"     value={summary.length}                  color="#8B5CF6" />
          <StatCard icon={Calendar}      label="Working Days"       value={workingDays}                     color="#10B981" />
          <StatCard icon={Clock}         label="Total Hours Logged" value={`${totalHours.toFixed(0)}h`}    color="#F59E0B" />
          <StatCard icon={AlertCircle}   label="Late Arrivals"      value={totalLate}    sub="across range" color="#EF4444" />
          <StatCard icon={AlertCircle}   label="Missed Check-outs"  value={totalMissed}  sub="no tap-out"   color="#DC2626" />
        </div>
      )}

      {/* ── view toggle ── */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <button
          className={view === 'summary' ? 'btn btn-primary' : 'btn btn-secondary'}
          onClick={() => setView('summary')}
          id="btn-view-summary"
        >
          <BarChart2 size={15} /> Per-Person Summary
        </button>
        <button
          className={view === 'sessions' ? 'btn btn-primary' : 'btn btn-secondary'}
          onClick={() => setView('sessions')}
          id="btn-view-sessions"
        >
          <List size={15} /> Session Log
        </button>
      </div>

      {/* ── per-person table ── */}
      {view === 'summary' && (
        <GlassCard>
          <DataTable
            columns={buildPersonColumns(maxDays)}
            data={summary}
            loading={loading}
            emptyMessage="No data found for this range."
          />
        </GlassCard>
      )}

      {/* ── session log table ── */}
      {view === 'sessions' && (
        <GlassCard>
          <DataTable
            columns={sessionColumns}
            data={sessions}
            loading={loading}
            emptyMessage="No session records found for this date range."
          />
        </GlassCard>
      )}
    </div>
  );
};

export default Reports;
