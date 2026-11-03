import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi, CaptchaResponse } from '../api/auth';
import { useAuth } from '../context/AuthContext';

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // CAPTCHA State
  const [captcha, setCaptcha] = useState<CaptchaResponse | null>(null);
  const [captchaAnswer, setCaptchaAnswer] = useState('');
  const [loadingCaptcha, setLoadingCaptcha] = useState(false);

  const fetchCaptcha = async () => {
    setLoadingCaptcha(true);
    try {
      const res = await authApi.getCaptcha();
      if (res?.data) {
        setCaptcha(res.data);
        setCaptchaAnswer('');
      }
    } catch (err) {
      console.error('Failed to load CAPTCHA challenge', err);
    } finally {
      setLoadingCaptcha(false);
    }
  };

  useEffect(() => {
    fetchCaptcha();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!captchaAnswer.trim()) {
      setError('Please enter the security verification CAPTCHA code.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const res = await authApi.login(email, password, captcha?.captchaId, captchaAnswer.trim());
      const token = res.data?.token || res.data?.accessToken;
      if (token) {
        await login(token);
        navigate('/dashboard');
      } else {
        setError('Login failed. Please verify your credentials.');
        fetchCaptcha();
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Invalid username or password.');
      fetchCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-ambient-glow" />
      <div className="auth-card fade-in">
        <div className="auth-logo">
          <div className="auth-logo-badge">
            <span>🏥</span>
          </div>
          <h1>ApexCare</h1>
          <span>Next-Generation Healthcare & Clinical Operations</span>
        </div>

        {error && (
          <div className="alert alert-error">
            <span>⚠️</span>
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Username or Hospital Email</label>
            <div style={{ position: 'relative' }}>
              <input
                type="text"
                className="form-input"
                style={{ paddingLeft: '2.5rem' }}
                placeholder="Enter your username or email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
              />
              <span style={{ position: 'absolute', left: '0.85rem', top: '50%', transform: 'translateY(-50%)', opacity: 0.4 }}>
                👤
              </span>
            </div>
          </div>

          <div className="form-group">
            <div className="flex justify-between items-center mb-1">
              <label className="form-label" style={{ marginBottom: 0 }}>Password</label>
              <button
                type="button"
                className="text-xs text-muted"
                style={{ background: 'none' }}
                onClick={() => setShowPassword(s => !s)}
              >
                {showPassword ? '🙈 Hide' : '👁️ Show'}
              </button>
            </div>
            <div style={{ position: 'relative' }}>
              <input
                type={showPassword ? 'text' : 'password'}
                className="form-input"
                style={{ paddingLeft: '2.5rem', paddingRight: '2.5rem' }}
                placeholder="••••••••••••"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />
              <span style={{ position: 'absolute', left: '0.85rem', top: '50%', transform: 'translateY(-50%)', opacity: 0.4 }}>
                🔒
              </span>
            </div>
          </div>

          {/* Security CAPTCHA Challenge */}
          <div className="form-group" style={{ marginTop: '1.25rem' }}>
            <div className="flex justify-between items-center mb-1">
              <label className="form-label" style={{ marginBottom: 0 }}>
                Security Verification (CAPTCHA) *
              </label>
              <button
                type="button"
                onClick={fetchCaptcha}
                disabled={loadingCaptcha}
                className="text-xs text-muted"
                style={{
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  color: 'var(--primary, #38bdf8)',
                  padding: '2px 6px',
                  borderRadius: 4
                }}
                title="Generate a new CAPTCHA challenge"
              >
                🔄 {loadingCaptcha ? 'Refreshing…' : 'Change Image'}
              </button>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
              <div
                style={{
                  background: '#0f172a',
                  border: '1px solid var(--border)',
                  borderRadius: 8,
                  padding: '2px 6px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  minWidth: 160,
                  minHeight: 50,
                  boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.4)',
                  cursor: 'pointer'
                }}
                onClick={fetchCaptcha}
                title="Click to refresh CAPTCHA"
                dangerouslySetInnerHTML={{
                  __html: captcha?.challengeSvg || '<div style="color:var(--text-muted);font-size:0.8rem;padding:12px;">Loading CAPTCHA...</div>'
                }}
              />
              <div style={{ flex: 1 }}>
                <input
                  type="text"
                  className="form-input"
                  style={{
                    letterSpacing: '3px',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    fontSize: '1.05rem',
                    textAlign: 'center'
                  }}
                  placeholder="CODE"
                  value={captchaAnswer}
                  onChange={e => setCaptchaAnswer(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
                  required
                  maxLength={6}
                  autoComplete="off"
                />
              </div>
            </div>
            <p className="text-xs text-muted mt-1">
              Type the 5 security characters shown above (case-insensitive).
            </p>
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-lg w-full mt-3"
            disabled={loading}
          >
            {loading ? (
              <>
                <span style={{ display: 'inline-block', width: 16, height: 16, border: '2px solid rgba(255,255,255,0.3)', borderTopColor: 'white', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
                Authenticating…
              </>
            ) : (
              'Sign In to Dashboard →'
            )}
          </button>
        </form>

        <div className="divider" />

        <div className="flex justify-between items-center text-sm text-muted">
          <span>New patient?</span>
          <Link to="/register" style={{ fontWeight: 600 }}>Create an Account</Link>
        </div>

        {/* Security badges */}
        <div className="flex justify-center gap-3 mt-4 text-xs text-muted" style={{ opacity: 0.65 }}>
          <span>🔒 256-Bit SSL</span>
          <span>·</span>
          <span>🛡️ HIPAA Compliant</span>
          <span>·</span>
          <span>⚡ 99.99% Uptime</span>
        </div>
      </div>
    </div>
  );
};

export default Login;
