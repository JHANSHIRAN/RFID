import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';
import Modal from '../../components/Modal';
import { Plus, UserCheck } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

const StaffUsers = () => {
  const { user } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({ email: '', role: 'OPERATOR' });
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/users');
      setUsers(res.data);
    } catch (err) {
      console.error("Failed to fetch staff users:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setIsSubmitting(true);
    
    try {
      await api.post('/api/users', formData);
      setIsModalOpen(false);
      setFormData({ email: '', role: 'OPERATOR' });
      fetchUsers();
    } catch (err) {
      setFormError(err.message || 'Failed to create staff user');
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleStatus = async (staffUser) => {
    if (staffUser.id === user.id) {
      alert("You cannot deactivate your own account.");
      return;
    }

    const action = staffUser.active ? 'deactivate' : 'reactivate';
    if (!window.confirm(`Are you sure you want to ${action} ${staffUser.email}?`)) return;
    
    try {
      await api.patch(`/api/users/${staffUser.id}`, { active: !staffUser.active });
      fetchUsers();
    } catch (err) {
      alert(err.message || `Failed to ${action} user`);
    }
  };

  const handleDelete = async (staffUser) => {
    if (user?.role !== 'ADMIN') return;
    if (staffUser.id === user.id) {
      alert("You cannot delete your own account.");
      return;
    }
    
    if (!staffUser.active) {
        alert(`${staffUser.email} is already inactive.`);
        return;
    }

    const confirmDelete = window.confirm(`Are you sure you want to delete ${staffUser.email}?`);
    if (!confirmDelete) return;
    
    try {
      await api.patch(`/api/users/${staffUser.id}`, { active: false });
      fetchUsers();
    } catch (err) {
      alert(err.message || 'Failed to delete user');
    }
  };

  const handleResetPassword = async (staffUser) => {
    if (user?.role !== 'ADMIN' && user?.role !== 'MANAGER') return;
    
    if (!window.confirm(`Are you sure you want to reset the password for ${staffUser.email}?`)) return;
    
    try {
      await api.post(`/api/users/${staffUser.id}/reset-password`);
      alert(`Password reset successfully for ${staffUser.email}. Their new password is their email address.`);
    } catch (err) {
      alert(err.message || 'Failed to reset password');
    }
  };

  const columns = [
    { header: 'ID', accessor: 'id' },
    { 
      header: 'Email', 
      accessor: 'email',
      render: (row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <UserCheck size={14} className="text-muted" /> {row.email}
        </div>
      )
    },
    { 
      header: 'Role', 
      accessor: 'role',
      render: (row) => (
        <Badge type={row.role === 'ADMIN' ? 'danger' : row.role === 'MANAGER' ? 'primary' : 'default'}>
          {row.role}
        </Badge>
      )
    },
    { 
      header: 'Status', 
      accessor: 'active',
      render: (row) => (
        <Badge type={row.active ? 'success' : 'danger'}>
          {row.active ? 'ACTIVE' : 'INACTIVE'}
        </Badge>
      )
    },
    { header: 'Last Login', accessor: 'lastLoginAt', render: (row) => row.lastLoginAt ? new Date(row.lastLoginAt).toLocaleString() : 'Never' },
    {
      header: 'Actions',
      accessor: 'actions',
      render: (row) => (
        user?.role === 'ADMIN' ? (
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button 
              className="btn btn-secondary" 
              style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', color: row.active ? 'var(--danger)' : 'var(--text-primary)' }}
              onClick={() => toggleStatus(row)}
              disabled={row.id === user.id}
            >
              {row.active ? 'Deactivate' : 'Reactivate'}
            </button>
            
            <button 
              className="btn btn-secondary" 
              style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
              onClick={() => handleResetPassword(row)}
            >
              Reset Password
            </button>
            
            {row.active && (
              <button 
                className="btn" 
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', backgroundColor: 'var(--danger-bg)', color: 'var(--danger)', border: '1px solid var(--danger)' }}
                onClick={() => handleDelete(row)}
                disabled={row.id === user.id}
              >
                Delete
              </button>
            )}
          </div>
        ) : (
          <span className="text-muted" style={{ fontSize: '0.75rem' }}>View Only</span>
        )
      )
    }
  ];

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1 style={{ margin: 0 }}>Staff Users</h1>
        <button className="btn btn-primary" onClick={() => setIsModalOpen(true)}>
          <Plus size={18} style={{ marginRight: '0.5rem' }} /> Create Staff
        </button>
      </div>

      <GlassCard>
        <DataTable 
          columns={columns} 
          data={users} 
          loading={loading} 
          emptyMessage="No staff users found." 
        />
      </GlassCard>

      <Modal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        title="Create Staff User"
      >
        {formError && (
          <div className="glass-card" style={{ padding: '0.75rem', marginBottom: '1rem', backgroundColor: 'var(--danger-bg)' }}>
            <p className="text-danger" style={{ fontSize: '0.875rem', margin: 0 }}>{formError}</p>
          </div>
        )}
        
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Email Address *</label>
            <input 
              type="email" 
              className="form-input" 
              value={formData.email} 
              onChange={(e) => setFormData(prev => ({ ...prev, email: e.target.value }))}
              required 
            />
          </div>

          <div className="form-group">
            <label className="form-label">Role *</label>
            <select 
              className="form-input" 
              value={formData.role} 
              onChange={(e) => setFormData(prev => ({ ...prev, role: e.target.value }))}
              required
            >
              <option value="OPERATOR">Operator</option>
              {user?.role === 'ADMIN' && <option value="MANAGER">Manager</option>}
              {user?.role === 'ADMIN' && <option value="ADMIN">Admin</option>}
            </select>
            <small className="text-muted" style={{ display: 'block', marginTop: '0.5rem' }}>
              Note: A temporary password will be sent via email. New staff will be forced to change it on first login.
            </small>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default StaffUsers;
