import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export const SessionExpiryWarning: React.FC = () => {
  const { logout, tokenExpiresAt } = useAuth();
  const navigate = useNavigate();
  const [remaining, setRemaining] = useState<number | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (!tokenExpiresAt) return;
    setDismissed(false);

    const check = () => {
      const now = Date.now();
      const timeLeft = Math.floor((tokenExpiresAt - now) / 1000);

      if (timeLeft <= 0) {
        logout();
        navigate('/login');
        return;
      }
      // Show warning in last 5 minutes
      if (timeLeft <= 300) {
        setRemaining(timeLeft);
      } else {
        setRemaining(null);
      }
    };

    const interval = setInterval(check, 15000);
    check();
    return () => clearInterval(interval);
  }, [tokenExpiresAt, logout, navigate]);

  if (remaining === null || dismissed) return null;

  const mins = Math.floor(remaining / 60);
  const secs = remaining % 60;
  const isUrgent = remaining <= 60;

  return (
    <div
      style={{
        position: 'fixed', bottom: '5rem', right: '1.5rem', zIndex: 998,
        background: 'var(--surface)',
        border: `1px solid ${isUrgent ? 'var(--danger)' : 'var(--warning)'}`,
        borderRadius: 'var(--radius-lg)',
        padding: '1rem 1.25rem',
        maxWidth: 340,
        boxShadow: 'var(--shadow)',
        animation: 'slideIn .3s ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '.75rem' }}>
        <span style={{ fontSize: '1.4rem' }}>{isUrgent ? '🚨' : '⚠️'}</span>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 600, marginBottom: '.25rem', color: isUrgent ? 'var(--danger)' : 'var(--warning)' }}>
            Session Expiring
          </div>
          <div style={{ fontSize: '.8rem', color: 'var(--text-2)', marginBottom: '.75rem' }}>
            Your session expires in{' '}
            <strong>{mins > 0 ? `${mins}m ` : ''}{secs}s</strong>.
            Please save your work.
          </div>
          <div style={{ display: 'flex', gap: '.5rem' }}>
            <button
              className="btn btn-danger btn-sm"
              onClick={() => { logout(); navigate('/login'); }}
            >
              Sign Out
            </button>
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => setDismissed(true)}
            >
              Dismiss
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
