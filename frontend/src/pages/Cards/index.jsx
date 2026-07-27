import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import { useAuth } from '../../context/AuthContext';
import GlassCard from '../../components/GlassCard';
import DataTable from '../../components/DataTable';
import Badge from '../../components/Badge';
import Modal from '../../components/Modal';
import { Plus, CreditCard } from 'lucide-react';

const Cards = () => {
  const { user } = useAuth();
  const [cards, setCards] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [cardUid, setCardUid] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isManagerOrAdmin = ['MANAGER', 'ADMIN'].includes(user?.role);

  const fetchCards = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/cards');
      
      const sortedCards = res.data.sort((a, b) => {
        const order = { 'AVAILABLE': 1, 'ASSIGNED': 2, 'DEACTIVATED': 3, 'LOST': 4 };
        const orderA = order[a.status] || 99;
        const orderB = order[b.status] || 99;
        return orderA - orderB;
      });
      
      setCards(sortedCards);
    } catch (err) {
      console.error("Failed to fetch cards:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCards();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setIsSubmitting(true);
    
    try {
      await api.post('/api/cards', { cardUid });
      setIsModalOpen(false);
      setCardUid('');
      fetchCards();
    } catch (err) {
      setFormError(err.message || 'Failed to register card');
    } finally {
      setIsSubmitting(false);
    }
  };

  const updateStatus = async (card, newStatus) => {
    if (!isManagerOrAdmin) return;
    
    if (newStatus === 'LOST' || newStatus === 'DEACTIVATED') {
      const confirmDeactivate = window.confirm(`Are you sure you want to mark this card as ${newStatus}? Active mappings will be released.`);
      if (!confirmDeactivate) return;
    }

    try {
      await api.patch(`/api/cards/${card.id}`, { status: newStatus });
      fetchCards();
    } catch (err) {
      alert(err.message || 'Failed to update status');
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'AVAILABLE': return <Badge type="success">{status}</Badge>;
      case 'ASSIGNED': return <Badge type="primary">{status}</Badge>;
      case 'LOST': return <Badge type="danger">{status}</Badge>;
      case 'DEACTIVATED': return <Badge type="warning">{status}</Badge>;
      default: return <Badge>{status}</Badge>;
    }
  };

  const columns = [
    { header: 'ID', accessor: 'id' },
    { 
      header: 'Card UID', 
      accessor: 'cardUid',
      render: (row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontFamily: 'monospace' }}>
          <CreditCard size={14} className="text-muted" /> {row.cardUid}
        </div>
      )
    },
    { 
      header: 'Status', 
      accessor: 'status',
      render: (row) => getStatusBadge(row.status)
    },
    { header: 'Registered On', accessor: 'createdAt', render: (row) => new Date(row.createdAt).toLocaleDateString() },
    {
      header: 'Actions',
      accessor: 'actions',
      render: (row) => (
        isManagerOrAdmin ? (
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            {row.status === 'AVAILABLE' && (
              <button className="btn btn-secondary" style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }} onClick={() => updateStatus(row, 'DEACTIVATED')}>
                Deactivate
              </button>
            )}
            {row.status === 'ASSIGNED' && (
              <button className="btn btn-secondary" style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', color: 'var(--danger)' }} onClick={() => updateStatus(row, 'LOST')}>
                Mark Lost
              </button>
            )}
            {row.status === 'DEACTIVATED' && (
              <button className="btn btn-secondary" style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }} onClick={() => updateStatus(row, 'AVAILABLE')}>
                Reactivate
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
        <h1 style={{ margin: 0 }}>Cards Inventory</h1>
        {isManagerOrAdmin && (
          <button className="btn btn-primary" onClick={() => setIsModalOpen(true)}>
            <Plus size={18} style={{ marginRight: '0.5rem' }} /> Register Card
          </button>
        )}
      </div>

      <GlassCard>
        <DataTable 
          columns={columns} 
          data={cards} 
          loading={loading} 
          emptyMessage="No RFID cards registered yet." 
        />
      </GlassCard>

      <Modal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        title="Register New Card"
      >
        {formError && (
          <div className="glass-card" style={{ padding: '0.75rem', marginBottom: '1rem', backgroundColor: 'var(--danger-bg)' }}>
            <p className="text-danger" style={{ fontSize: '0.875rem', margin: 0 }}>{formError}</p>
          </div>
        )}
        
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Card UID *</label>
            <input 
              type="text" 
              className="form-input" 
              name="cardUid" 
              value={cardUid} 
              onChange={(e) => setCardUid(e.target.value)} 
              placeholder="e.g. A1B2C3D4"
              required 
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Registering...' : 'Register'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default Cards;
