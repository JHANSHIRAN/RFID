import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';
import Modal from '../../components/Modal';
import { Link, Unlink } from 'lucide-react';

const CardMappings = () => {
  const [mappings, setMappings] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [availableCards, setAvailableCards] = useState([]);
  const [activePeople, setActivePeople] = useState([]);
  
  const [formData, setFormData] = useState({ personId: '', cardUid: '' });
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchMappings = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/mappings');
      setMappings(res.data);
    } catch (err) {
      console.error("Failed to fetch mappings:", err);
    } finally {
      setLoading(false);
    }
  };

  const fetchDependencies = async () => {
    try {
      const [cardsRes, peopleRes] = await Promise.all([
        api.get('/api/cards'),
        api.get('/api/people')
      ]);
      setAvailableCards(cardsRes.data.filter(c => c.status === 'AVAILABLE'));
      setActivePeople(peopleRes.data.filter(p => p.status === 'ACTIVE'));
    } catch (err) {
      console.error("Failed to fetch dependencies:", err);
    }
  };

  useEffect(() => {
    fetchMappings();
  }, []);

  const handleOpenModal = () => {
    fetchDependencies();
    setIsModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setIsSubmitting(true);
    
    try {
      await api.post('/api/mappings', formData);
      setIsModalOpen(false);
      setFormData({ personId: '', cardUid: '' });
      fetchMappings();
    } catch (err) {
      const msg = typeof err === 'string' ? err : (err.message || err.error || JSON.stringify(err) || 'Failed to create mapping');
      setFormError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const releaseMapping = async (mappingId) => {
    if (!window.confirm("Are you sure you want to release this card from this person?")) return;
    
    try {
      await api.patch(`/api/mappings/${mappingId}/release`);
      fetchMappings();
    } catch (err) {
      alert(err.message || 'Failed to release mapping');
    }
  };

  const columns = [
    { header: 'ID', accessor: 'id' },
    { header: 'Person', accessor: 'person', render: (row) => row.person.fullName },
    { header: 'Card UID', accessor: 'card', render: (row) => <span style={{fontFamily: 'monospace'}}>{row.card.cardUid}</span> },
    { 
      header: 'Status', 
      accessor: 'status',
      render: (row) => (
        <Badge type={row.status === 'ACTIVE' ? 'success' : 'default'}>
          {row.status}
        </Badge>
      )
    },
    { header: 'Assigned At', accessor: 'assignedAt', render: (row) => new Date(row.assignedAt).toLocaleString() },
    {
      header: 'Actions',
      accessor: 'actions',
      render: (row) => (
        row.status === 'ACTIVE' ? (
          <button 
            className="btn btn-secondary" 
            style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', color: 'var(--danger)' }}
            onClick={() => releaseMapping(row.id)}
          >
            <Unlink size={14} style={{ marginRight: '0.25rem' }} /> Release
          </button>
        ) : (
          <span className="text-muted" style={{ fontSize: '0.75rem' }}>Released</span>
        )
      )
    }
  ];

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1 style={{ margin: 0 }}>Card Mappings</h1>
        <button className="btn btn-primary" onClick={handleOpenModal}>
          <Link size={18} style={{ marginRight: '0.5rem' }} /> Assign Card
        </button>
      </div>

      <GlassCard>
        <DataTable 
          columns={columns} 
          data={mappings} 
          loading={loading} 
          emptyMessage="No card mappings found." 
        />
      </GlassCard>

      <Modal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        title="Assign Card to Person"
      >
        {formError && (
          <div className="glass-card" style={{ padding: '0.75rem', marginBottom: '1rem', backgroundColor: 'var(--danger-bg)' }}>
            <p className="text-danger" style={{ fontSize: '0.875rem', margin: 0 }}>{formError}</p>
          </div>
        )}
        
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Select Person *</label>
            <select 
              className="form-input" 
              value={formData.personId} 
              onChange={(e) => setFormData(prev => ({ ...prev, personId: e.target.value }))}
              required
            >
              <option value="">-- Choose an active person --</option>
              {activePeople.map(p => (
                <option key={p.id} value={p.id}>{p.fullName} ({p.groupLabel})</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Select Available Card *</label>
            <select 
              className="form-input" 
              value={formData.cardUid} 
              onChange={(e) => setFormData(prev => ({ ...prev, cardUid: e.target.value }))}
              required
            >
              <option value="">-- Choose an available card --</option>
              {availableCards.map(c => (
                <option key={c.id} value={c.cardUid}>{c.cardUid}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting || !formData.personId || !formData.cardUid}>
              {isSubmitting ? 'Assigning...' : 'Assign Card'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default CardMappings;
