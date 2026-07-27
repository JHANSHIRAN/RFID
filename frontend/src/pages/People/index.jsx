import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import { useAuth } from '../../context/AuthContext';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';
import Modal from '../../components/Modal';
import { Plus, Edit2, ShieldAlert } from 'lucide-react';

const People = () => {
  const { user } = useAuth();
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editPersonId, setEditPersonId] = useState(null);
  const [formData, setFormData] = useState({
    fullName: '',
    memberType: 'EMPLOYEE',
    externalRef: '',
    groupLabel: '',
    email: '',
    phone: ''
  });
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isManagerOrAdmin = ['MANAGER', 'ADMIN'].includes(user?.role);

  const fetchPeople = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/people');
      setPeople(res.data);
    } catch (err) {
      console.error("Failed to fetch people:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPeople();
  }, []);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setIsSubmitting(true);
    
    try {
      if (editPersonId) {
        await api.patch(`/api/people/${editPersonId}`, formData);
      } else {
        await api.post('/api/people', formData);
      }
      setIsModalOpen(false);
      setEditPersonId(null);
      setFormData({
        fullName: '',
        memberType: 'EMPLOYEE',
        externalRef: '',
        groupLabel: '',
        email: '',
        phone: ''
      });
      fetchPeople();
    } catch (err) {
      setFormError(err.message || `Failed to ${editPersonId ? 'update' : 'register'} person`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const openEditModal = (person) => {
    setEditPersonId(person.id);
    setFormData({
      fullName: person.fullName || '',
      memberType: person.memberType || 'EMPLOYEE',
      externalRef: person.externalRef || '',
      groupLabel: person.groupLabel || '',
      email: person.email || '',
      phone: person.phone || ''
    });
    setFormError('');
    setIsModalOpen(true);
  };

  const openCreateModal = () => {
    setEditPersonId(null);
    setFormData({
      fullName: '',
      memberType: 'EMPLOYEE',
      externalRef: '',
      groupLabel: '',
      email: '',
      phone: ''
    });
    setFormError('');
    setIsModalOpen(true);
  };

  const toggleStatus = async (person) => {
    if (!isManagerOrAdmin) return;
    
    const newStatus = person.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (newStatus === 'INACTIVE') {
      const confirmDeactivate = window.confirm(`Are you sure you want to deactivate ${person.fullName}? This will release their card mapping.`);
      if (!confirmDeactivate) return;
    }

    try {
      await api.patch(`/api/people/${person.id}`, { status: newStatus });
      fetchPeople();
    } catch (err) {
      alert(err.message || 'Failed to update status');
    }
  };

  const handleDelete = async (person) => {
    if (user?.role !== 'ADMIN') return;
    
    if (person.status === 'INACTIVE') {
        alert(`${person.fullName} is already inactive.`);
        return;
    }

    const confirmDelete = window.confirm(`Are you sure you want to delete ${person.fullName}?`);
    if (!confirmDelete) return;

    try {
      await api.patch(`/api/people/${person.id}`, { status: 'INACTIVE' });
      fetchPeople();
    } catch (err) {
      alert(err.message || 'Failed to delete person');
    }
  };

  const columns = [
    { header: 'ID', accessor: 'id' },
    { header: 'Name', accessor: 'fullName' },
    { 
      header: 'Type', 
      accessor: 'memberType',
      render: (row) => <Badge type={row.memberType === 'STUDENT' ? 'primary' : 'default'}>{row.memberType}</Badge>
    },
    { header: 'Reference', accessor: 'externalRef' },
    { header: 'Group/Role', accessor: 'groupLabel' },
    { 
      header: 'Assigned', 
      accessor: 'assignedCardUid',
      render: (row) => row.assignedCardUid 
        ? <Badge type="success">{row.assignedCardUid}</Badge> 
        : <Badge type="default">No</Badge>
    },
    { 
      header: 'Status', 
      accessor: 'status',
      render: (row) => (
        <Badge type={row.status === 'ACTIVE' ? 'success' : 'danger'}>
          {row.status}
        </Badge>
      )
    },
    {
      header: 'Actions',
      accessor: 'actions',
      render: (row) => (
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          {isManagerOrAdmin && (
            <>
              <button 
                className="btn btn-secondary" 
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                onClick={() => openEditModal(row)}
              >
                Edit
              </button>
              <button 
                className="btn btn-secondary" 
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                onClick={() => toggleStatus(row)}
              >
                {row.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
              </button>
            </>
          )}

          {user?.role === 'ADMIN' && row.status === 'ACTIVE' && (
            <button 
              className="btn" 
              style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', backgroundColor: 'var(--danger-bg)', color: 'var(--danger-color)', border: '1px solid var(--danger-color)' }}
              onClick={() => handleDelete(row)}
            >
              Delete
            </button>
          )}

          {!isManagerOrAdmin && user?.role !== 'ADMIN' && row.status !== 'ACTIVE' && (
             <span className="text-muted" style={{ fontSize: '0.75rem' }}>View Only</span>
          )}
        </div>
      )
    }
  ];

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1 style={{ margin: 0 }}>People Directory</h1>
        <button className="btn btn-primary" onClick={openCreateModal}>
          <Plus size={18} style={{ marginRight: '0.5rem' }} /> Register Person
        </button>
      </div>

      <GlassCard>
        <DataTable 
          columns={columns} 
          data={people} 
          loading={loading} 
          emptyMessage="No people registered yet." 
        />
      </GlassCard>

      <Modal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        title={editPersonId ? "Edit Person" : "Register New Person"}
      >
        {formError && (
          <div className="glass-card" style={{ padding: '0.75rem', marginBottom: '1rem', backgroundColor: 'var(--danger-bg)' }}>
            <p className="text-danger" style={{ fontSize: '0.875rem', margin: 0 }}>{formError}</p>
          </div>
        )}
        
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Full Name *</label>
            <input 
              type="text" 
              className="form-input" 
              name="fullName" 
              value={formData.fullName} 
              onChange={handleInputChange} 
              required 
            />
          </div>

          <div style={{ display: 'flex', gap: '1rem' }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Member Type *</label>
              <select 
                className="form-input" 
                name="memberType" 
                value={formData.memberType} 
                onChange={handleInputChange}
                required
              >
                <option value="EMPLOYEE">Employee</option>
                <option value="STUDENT">Student</option>
                <option value="GUEST">Guest</option>
              </select>
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">External Ref (ID)</label>
              <input 
                type="text" 
                className="form-input" 
                name="externalRef" 
                value={formData.externalRef} 
                onChange={handleInputChange} 
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Group / Role / Dept *</label>
            <input 
              type="text" 
              className="form-input" 
              name="groupLabel" 
              value={formData.groupLabel} 
              onChange={handleInputChange} 
              placeholder="e.g., Engineering, Class 10A"
              required 
            />
          </div>
          
          <div style={{ display: 'flex', gap: '1rem' }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Email</label>
              <input 
                type="email" 
                className="form-input" 
                name="email" 
                value={formData.email} 
                onChange={handleInputChange} 
              />
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Phone</label>
              <input 
                type="text" 
                className="form-input" 
                name="phone" 
                value={formData.phone} 
                onChange={handleInputChange} 
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : (editPersonId ? 'Save Changes' : 'Register')}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default People;
