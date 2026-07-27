import React, { useState, useEffect } from 'react';
import { api } from '../../api/api';
import GlassCard from '../../components/GlassCard';
import { Settings as SettingsIcon, Save } from 'lucide-react';

const Settings = () => {
  const [config, setConfig] = useState(null);
  const [formData, setFormData] = useState({
    expectedStartTime: '',
    lateGraceMinutes: 0,
    autoCheckoutTime: '',
    workingDays: [],
    tapDebounceSeconds: 60,
    minimumHoursRequired: 8
  });
  
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  const fetchConfig = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/config');
      setConfig(res.data);
      setFormData({
        expectedStartTime: res.data.expectedStartTime || '',
        lateGraceMinutes: res.data.lateGraceMinutes || 0,
        autoCheckoutTime: res.data.autoCheckoutTime || '',
        workingDays: typeof res.data.workingDays === 'string' 
          ? res.data.workingDays.split(',').filter(Boolean) 
          : (res.data.workingDays || []),
        tapDebounceSeconds: res.data.tapDebounceSeconds || 60,
        minimumHoursRequired: res.data.minimumHoursRequired || 8
      });
    } catch (err) {
      console.error("Failed to fetch config:", err);
      setMessage({ type: 'danger', text: 'Failed to load settings.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleWorkingDaysChange = (day) => {
    const daysArray = formData.workingDays || [];
    if (daysArray.includes(day)) {
      setFormData(prev => ({ ...prev, workingDays: daysArray.filter(d => d !== day) }));
    } else {
      setFormData(prev => ({ ...prev, workingDays: [...daysArray, day] }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMessage({ type: '', text: '' });

    try {
      await api.patch('/api/config', {
        expectedStartTime: formData.expectedStartTime,
        lateGraceMinutes: parseInt(formData.lateGraceMinutes, 10),
        autoCheckoutTime: formData.autoCheckoutTime,
        workingDays: formData.workingDays,
        tapDebounceSeconds: parseInt(formData.tapDebounceSeconds, 10),
        minimumHoursRequired: parseInt(formData.minimumHoursRequired, 10)
      });
      setMessage({ type: 'success', text: 'Settings updated successfully!' });
      fetchConfig();
    } catch (err) {
      setMessage({ type: 'danger', text: err.message || 'Failed to update settings.' });
    } finally {
      setSaving(false);
    }
  };

  const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

  if (loading) return <div className="p-4 text-center">Loading settings...</div>;

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '2rem' }}>
        <SettingsIcon size={28} style={{ marginRight: '0.75rem', color: 'var(--accent-primary)' }} />
        <h1 style={{ margin: 0 }}>System Settings</h1>
      </div>

      <GlassCard title="Attendance Configuration" className="mb-4">
        {message.text && (
          <div className="glass-card" style={{ padding: '0.75rem', marginBottom: '1.5rem', backgroundColor: `var(--${message.type}-bg)`, border: `1px solid var(--${message.type})` }}>
            <p className={`text-${message.type}`} style={{ fontSize: '0.875rem', margin: 0 }}>{message.text}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ maxWidth: '600px' }}>
          <div className="form-group">
            <label className="form-label">Expected Start Time</label>
            <input 
              type="time" 
              className="form-input" 
              name="expectedStartTime"
              value={formData.expectedStartTime}
              onChange={handleChange}
              required
            />
            <small className="text-muted" style={{ display: 'block', marginTop: '0.25rem' }}>
              The time by which members are expected to check in (e.g., 09:00).
            </small>
          </div>

          <div style={{ display: 'flex', gap: '1rem' }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Late Grace (Mins)</label>
              <input 
                type="number" 
                className="form-input" 
                name="lateGraceMinutes"
                min="0"
                value={formData.lateGraceMinutes}
                onChange={handleChange}
                required
              />
            </div>
            
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Tap Debounce (Secs)</label>
              <input 
                type="number" 
                className="form-input" 
                name="tapDebounceSeconds"
                min="0"
                value={formData.tapDebounceSeconds}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div style={{ display: 'flex', gap: '1rem' }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Auto-Checkout Time</label>
              <input 
                type="time" 
                className="form-input" 
                name="autoCheckoutTime"
                value={formData.autoCheckoutTime}
                onChange={handleChange}
                required
              />
            </div>

            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Min Hours/Day</label>
              <input 
                type="number" 
                className="form-input" 
                name="minimumHoursRequired"
                min="1"
                value={formData.minimumHoursRequired}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Working Days</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginTop: '0.5rem' }}>
              {DAYS.map(day => {
                const isActive = (formData.workingDays || []).includes(day);
                return (
                  <button
                    key={day}
                    type="button"
                    onClick={() => handleWorkingDaysChange(day)}
                    style={{
                      padding: '0.5rem 1rem',
                      borderRadius: 'var(--radius-md)',
                      fontSize: '0.75rem',
                      fontWeight: '600',
                      cursor: 'pointer',
                      border: isActive ? '1px solid var(--accent-primary)' : '1px solid rgba(255, 255, 255, 0.4)',
                      backgroundColor: isActive ? 'var(--accent-primary)' : 'rgba(255, 255, 255, 0.2)',
                      color: isActive ? '#fff' : 'var(--text-secondary)',
                      transition: 'all 0.2s'
                    }}
                  >
                    {day.substring(0, 3)}
                  </button>
                );
              })}
            </div>
          </div>

          <button type="submit" className="btn btn-primary" disabled={saving}>
            <Save size={18} style={{ marginRight: '0.5rem' }} />
            {saving ? 'Saving...' : 'Save Settings'}
          </button>
        </form>
      </GlassCard>
    </div>
  );
};

export default Settings;
