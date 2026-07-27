import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../api/api';
import { Lock, Mail } from 'lucide-react';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      const res = await api.post('/api/login', { email, password });
      
      const { token, email: userEmail, role, passwordChangeRequired } = res.data;
      const user = { email: userEmail, role, password_change_required: passwordChangeRequired };
      
      login(user, token);
      
      if (user.password_change_required) {
        navigate('/change-password');
      } else {
        navigate('/dashboard');
      }
    } catch (err) {
      setError(err.message || 'Login failed. Please check your credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center animate-fade-in" style={{ 
      minHeight: '100vh',
      backgroundImage: "url('/login-bg.png')",
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat'
    }}>
      <div className="glass-panel" style={{ width: '100%', maxWidth: '400px', padding: '2rem' }}>
        <div className="text-center mb-4">
          <h2 style={{ marginBottom: '0.5rem' }}>AccessTrack</h2>
          <p className="text-muted">Sign in to your account</p>
        </div>

        {error && (
          <div className="glass-card" style={{ padding: '0.75rem', marginBottom: '1rem', backgroundColor: 'var(--danger-bg)', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
            <p className="text-danger" style={{ fontSize: '0.875rem', margin: 0 }}>{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <div style={{ position: 'relative' }}>
              <Mail style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} size={18} />
              <input
                type="email"
                className="form-input"
                style={{ width: '100%', paddingLeft: '2.5rem' }}
                placeholder="admin@zencube.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
          </div>
          
          <div className="form-group">
            <label className="form-label">Password</label>
            <div style={{ position: 'relative' }}>
              <Lock style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} size={18} />
              <input
                type="password"
                className="form-input"
                style={{ width: '100%', paddingLeft: '2.5rem' }}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary mt-4"
            style={{ width: '100%', padding: '0.75rem' }}
            disabled={isLoading}
          >
            {isLoading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default LoginPage;
