import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../context/AuthContext';

const ROLES = [
  { value: 'PATIENT', label: 'Patient Portal', icon: '👤', desc: 'Book consultations & access personal health records' },
  { value: 'DOCTOR', label: 'Doctor / Physician', icon: '👨‍⚕️', desc: 'Clinical diagnosis, appointments, prescriptions & EMR' },
  { value: 'NURSE', label: 'Staff Nurse', icon: '👩‍⚕️', desc: 'Inpatient care, vitals monitoring & bed allocation' },
  { value: 'RECEPTIONIST', label: 'Front Desk', icon: '👩‍💼', desc: 'Patient onboarding, check-ins & appointments' },
  { value: 'BILLING_ADMIN', label: 'Billing & Accounts', icon: '💳', desc: 'Invoice generation, insurance claims & payments' },
  { value: 'ADMIN', label: 'Administrator', icon: '⚡', desc: 'Full institutional configuration & audit control' },
];

const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,15}$/;
const DISPOSABLE_DOMAINS = [
  'mailinator.com', 'tempmail.com', '10minutemail.com', 'guerrillamail.com',
  'throwawaymail.com', 'yopmail.com', 'sharklasers.com', 'dispostable.com',
  'getairmail.com', 'trashmail.com', 'temp-mail.org', 'fake.com'
];

const Register: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [form, setForm] = useState({
    username: '', email: '', password: '', firstName: '', lastName: '', role: 'PATIENT', adminCode: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Username Availability State
  const [usernameStatus, setUsernameStatus] = useState<'idle' | 'checking' | 'available' | 'taken' | 'invalid'>('idle');
  const [usernameMessage, setUsernameMessage] = useState('');
  const [usernameSuggestions, setUsernameSuggestions] = useState<string[]>([]);

  // Debounced Username Availability Check
  useEffect(() => {
    const raw = form.username.trim();
    if (!raw) {
      setUsernameStatus('idle');
      setUsernameMessage('');
      setUsernameSuggestions([]);
      return;
    }

    if (raw.length < 3) {
      setUsernameStatus('invalid');
      setUsernameMessage('Must be at least 3 characters');
      setUsernameSuggestions([]);
      return;
    }

    if (!/^[a-zA-Z0-9._-]+$/.test(raw)) {
      setUsernameStatus('invalid');
      setUsernameMessage('Only letters, numbers, ., _, - allowed (no spaces)');
      setUsernameSuggestions([]);
      return;
    }

    setUsernameStatus('checking');
    setUsernameMessage('Checking availability...');

    const timer = setTimeout(async () => {
      try {
        const res = await authApi.checkUsername(raw);
        if (res.data?.available) {
          setUsernameStatus('available');
          setUsernameMessage('✓ Username available');
          setUsernameSuggestions([]);
        } else {
          setUsernameStatus('taken');
          setUsernameMessage(res.data?.message || '⚠️ Username is already taken');
          setUsernameSuggestions(res.data?.suggestions || []);
        }
      } catch (err: any) {
        setUsernameStatus('invalid');
        setUsernameMessage(err.response?.data?.message || 'Could not verify username');
      }
    }, 350);

    return () => clearTimeout(timer);
  }, [form.username]);

  // Email Verification OTP State
  const [showOtpModal, setShowOtpModal] = useState(false);
  const [otp, setOtp] = useState('');
  const [otpError, setOtpError] = useState('');
  const [verifyingOtp, setVerifyingOtp] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [otpExpiresIn, setOtpExpiresIn] = useState(600); // 10 minutes
  const [pendingStaffSuccess, setPendingStaffSuccess] = useState(false);

  // Timer countdowns for OTP
  useEffect(() => {
    let timer: ReturnType<typeof setInterval>;
    if (showOtpModal && otpExpiresIn > 0) {
      timer = setInterval(() => {
        setOtpExpiresIn(prev => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [showOtpModal, otpExpiresIn]);

  useEffect(() => {
    let timer: ReturnType<typeof setInterval>;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown(prev => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [resendCooldown]);

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));

  const emailTrimmed = form.email.trim().toLowerCase();
  const emailDomain = emailTrimmed.includes('@') ? emailTrimmed.split('@')[1] : '';
  const isDisposable = DISPOSABLE_DOMAINS.includes(emailDomain);
  const isEmailValidFormat = EMAIL_REGEX.test(emailTrimmed);
  const isEmailValid = isEmailValidFormat && !isDisposable;

  // Step 1: Send OTP to user's real email inbox
  const handleInitiateRegister = async (e: React.FormEvent) => {
    e.preventDefault();

    const usernameClean = form.username.trim();
    if (!usernameClean) {
      setError('Please choose a username.');
      return;
    }
    if (usernameClean.length < 3) {
      setError('Username must be at least 3 characters.');
      return;
    }
    if (!/^[a-zA-Z0-9._-]+$/.test(usernameClean)) {
      setError('Username may only contain letters, numbers, ., _, and - (no spaces).');
      return;
    }
    if (usernameStatus === 'taken') {
      setError('This username is already taken. Please choose another username or pick a suggestion.');
      return;
    }

    if (!emailTrimmed) {
      setError('Email address is required.');
      return;
    }
    if (!isEmailValidFormat) {
      setError('Please enter a valid email address with a valid domain (e.g. name@gmail.com, name@hospital.com).');
      return;
    }
    if (isDisposable) {
      setError('Temporary or disposable email addresses are not permitted. Please use a valid personal or hospital email.');
      return;
    }

    if (form.role === 'ADMIN') {
      const code = form.adminCode?.trim();
      if (!code) {
        setError('Admin Security Code is required to register an Administrator account.');
        return;
      }
      if (code !== 'Abhi') {
        setError('Invalid Admin Security Code. Only authorized personnel can create an Administrator account.');
        return;
      }
    }

    setLoading(true);
    setError('');
    try {
      const res = await authApi.sendOtp(form.email, form.username);
      if (res?.data || res?.success) {
        setOtpExpiresIn(res.data?.expiresInSeconds || 600);
        setResendCooldown(30);
        setShowOtpModal(true);
        setOtp('');
        setOtpError('');
      } else {
        setError(res?.message || 'Failed to send verification code. Please check your email.');
      }
    } catch (err: any) {
      const msgs = err.response?.data?.errors;
      setError(msgs ? Object.values(msgs).join(', ') : (err.response?.data?.message || 'Failed to send verification code to your email.'));
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Resend OTP
  const handleResendOtp = async () => {
    if (resendCooldown > 0) return;
    setOtpError('');
    try {
      const res = await authApi.sendOtp(form.email, form.username);
      if (res?.data || res?.success) {
        setOtpExpiresIn(res.data?.expiresInSeconds || 600);
        setResendCooldown(30);
      }
    } catch (err: any) {
      setOtpError(err.response?.data?.message || 'Failed to resend code. Please try again.');
    }
  };

  // Step 3: Verify OTP from Email & Complete Registration
  const handleVerifyAndRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otp.trim() || otp.trim().length !== 6) {
      setOtpError('Please enter the 6-digit verification code received in your email.');
      return;
    }

    setVerifyingOtp(true);
    setOtpError('');
    try {
      const res = await authApi.register({
        ...form,
        adminCode: form.role === 'ADMIN' ? form.adminCode.trim() : undefined,
        otp: otp.trim(),
      });

      const isStaffRole = ['DOCTOR', 'NURSE', 'BILLING_ADMIN', 'RECEPTIONIST'].includes(form.role);
      const token = res.data?.token || res.data?.accessToken;

      if (token && !isStaffRole) {
        await login(token);
        setShowOtpModal(false);
        navigate('/dashboard');
      } else {
        // Staff account requiring administrator approval
        setShowOtpModal(false);
        setPendingStaffSuccess(true);
      }
    } catch (err: any) {
      const msgs = err.response?.data?.errors;
      setOtpError(msgs ? Object.values(msgs).join(', ') : (err.response?.data?.message || 'Invalid or expired code. Please check your email.'));
    } finally {
      setVerifyingOtp(false);
    }
  };

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="auth-page">
      <div className="auth-ambient-glow" />
      <div className="auth-card fade-in" style={{ maxWidth: 540 }}>
        <div className="auth-logo">
          <div className="auth-logo-badge">
            <span>✨</span>
          </div>
          <h1>Create Your Account</h1>
          <span>Join the ApexCare Next-Generation Health Network</span>
        </div>

        {error && (
          <div className="alert alert-error">
            <span>⚠️</span>
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleInitiateRegister}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">First Name *</label>
              <input
                className="form-input"
                placeholder="e.g. Rahul"
                value={form.firstName}
                onChange={set('firstName')}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Last Name *</label>
              <input
                className="form-input"
                placeholder="e.g. Verma"
                value={form.lastName}
                onChange={set('lastName')}
                required
              />
            </div>
          </div>

          <div className="form-group">
            <div className="flex justify-between items-center mb-1">
              <label className="form-label" style={{ marginBottom: 0 }}>Choose Unique Username *</label>
              {form.username.trim() && (
                <span
                  style={{
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    color:
                      usernameStatus === 'available'
                        ? '#22c55e'
                        : usernameStatus === 'checking'
                        ? '#60a5fa'
                        : '#ef4444',
                  }}
                >
                  {usernameMessage}
                </span>
              )}
            </div>
            <div style={{ position: 'relative' }}>
              <input
                className="form-input"
                style={{
                  borderColor:
                    !form.username.trim()
                      ? undefined
                      : usernameStatus === 'available'
                      ? '#22c55e'
                      : usernameStatus === 'checking'
                      ? '#60a5fa'
                      : '#ef4444',
                  paddingRight: usernameStatus === 'checking' ? '2.5rem' : undefined,
                }}
                placeholder="e.g. rahul_verma"
                value={form.username}
                onChange={set('username')}
                required
                minLength={3}
              />
              {usernameStatus === 'checking' && (
                <span
                  style={{
                    position: 'absolute',
                    right: '0.75rem',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    fontSize: '0.85rem',
                    color: '#60a5fa',
                  }}
                >
                  ⏳
                </span>
              )}
            </div>

            {/* Suggestions chip list if taken */}
            {usernameStatus === 'taken' && usernameSuggestions.length > 0 && (
              <div style={{ marginTop: '0.45rem', fontSize: '0.78rem' }}>
                <span style={{ color: 'var(--text-secondary, #94a3b8)', marginRight: '0.5rem' }}>
                  Available alternatives:
                </span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginTop: '0.25rem' }}>
                  {usernameSuggestions.map(s => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => setForm(f => ({ ...f, username: s }))}
                      style={{
                        padding: '0.2rem 0.55rem',
                        fontSize: '0.75rem',
                        background: 'rgba(99, 102, 241, 0.12)',
                        color: '#818cf8',
                        border: '1px solid rgba(99, 102, 241, 0.3)',
                        borderRadius: '9999px',
                        cursor: 'pointer',
                        transition: 'all 0.15s ease',
                      }}
                      onMouseOver={e => ((e.currentTarget as HTMLElement).style.background = 'rgba(99, 102, 241, 0.25)')}
                      onMouseOut={e => ((e.currentTarget as HTMLElement).style.background = 'rgba(99, 102, 241, 0.12)')}
                    >
                      + {s}
                    </button>
                  ))}
                </div>
              </div>
            )}
            <p className="text-xs text-muted mt-1">
              💡 Your unique username acts as your primary identification across ApexCare portals.
            </p>
          </div>

          <div className="form-group">
            <div className="flex justify-between items-center mb-1">
              <label className="form-label" style={{ marginBottom: 0 }}>Email Address *</label>
              {emailTrimmed && (
                <span style={{ fontSize: '0.75rem', fontWeight: 600, color: isEmailValid ? '#22c55e' : '#ef4444' }}>
                  {isEmailValid ? '✓ Valid format' : isDisposable ? '⚠️ Disposable email not allowed' : '⚠️ Invalid email format'}
                </span>
              )}
            </div>
            <input
              type="email"
              className="form-input"
              style={{
                borderColor: emailTrimmed ? (isEmailValid ? '#22c55e' : '#ef4444') : undefined
              }}
              placeholder="e.g. rahul.verma@gmail.com"
              value={form.email}
              onChange={set('email')}
              required
            />
            {emailTrimmed && !isEmailValid && (
              <p style={{ fontSize: '0.75rem', color: '#ef4444', marginTop: '0.35rem', marginBottom: 0 }}>
                {isDisposable
                  ? 'Disposable or temporary email services are not permitted.'
                  : 'Please enter a valid email with a valid domain (e.g. name@gmail.com, name@hospital.com).'}
              </p>
            )}
            <p className="text-xs text-muted mt-1">
              🔒 A 6-digit verification code will be sent to your real email inbox before account creation.
            </p>
          </div>

          <div className="form-group">
            <div className="flex justify-between items-center mb-1">
              <label className="form-label" style={{ marginBottom: 0 }}>Password *</label>
              <button
                type="button"
                className="text-xs text-muted"
                style={{ background: 'none' }}
                onClick={() => setShowPassword(s => !s)}
              >
                {showPassword ? '🙈 Hide' : '👁️ Show'}
              </button>
            </div>
            <input
              type={showPassword ? 'text' : 'password'}
              className="form-input"
              placeholder="At least 8 characters with letters & numbers"
              value={form.password}
              onChange={set('password')}
              required
              minLength={8}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Account Role Designation *</label>
            <select className="form-select" value={form.role} onChange={set('role')}>
              {ROLES.map(r => (
                <option key={r.value} value={r.value}>
                  {r.icon} {r.label} — ({r.value})
                </option>
              ))}
            </select>
            <p className="text-xs text-muted mt-1">
              {ROLES.find(r => r.value === form.role)?.desc}
            </p>
            {['DOCTOR', 'NURSE', 'BILLING_ADMIN', 'RECEPTIONIST'].includes(form.role) && (
              <div
                className="fade-in"
                style={{
                  marginTop: '0.65rem',
                  padding: '0.75rem',
                  background: 'rgba(245, 158, 11, 0.08)',
                  border: '1px solid rgba(245, 158, 11, 0.25)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.82rem',
                  color: '#fbbf24',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                }}
              >
                <span>ℹ️</span>
                <span>
                  Staff Security: <strong>{ROLES.find(r => r.value === form.role)?.label}</strong> accounts require Administrator approval before first sign-in.
                </span>
              </div>
            )}
          </div>

          {form.role === 'ADMIN' && (
            <div
              className="form-group fade-in"
              style={{
                background: 'rgba(239, 68, 68, 0.08)',
                border: '1px solid rgba(239, 68, 68, 0.35)',
                borderRadius: 'var(--radius)',
                padding: '1rem',
                marginTop: '1rem',
              }}
            >
              <div className="flex justify-between items-center mb-1">
                <label className="form-label" style={{ color: '#f87171', fontWeight: 700, marginBottom: 0 }}>
                  🔒 Admin Security Code *
                </label>
                <span style={{ fontSize: '0.75rem', color: '#fca5a5', fontWeight: 600 }}>
                  Restricted Access
                </span>
              </div>
              <input
                type="password"
                className="form-input"
                placeholder="Enter secret admin security code..."
                value={form.adminCode}
                onChange={set('adminCode')}
                required
                style={{ borderColor: 'rgba(239, 68, 68, 0.45)' }}
              />
              <p className="text-xs text-muted mt-1" style={{ color: '#fca5a5' }}>
                🛡️ Institutional Security: Admin accounts require authorization code verification.
              </p>
            </div>
          )}

          <button
            type="submit"
            className="btn btn-primary btn-lg w-full mt-4"
            disabled={loading}
          >
            {loading ? (
              <>
                <span style={{ display: 'inline-block', width: 16, height: 16, border: '2px solid rgba(255,255,255,0.3)', borderTopColor: 'white', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
                Sending verification code to your email…
              </>
            ) : (
              'Verify Email & Register →'
            )}
          </button>
        </form>

        <div className="divider" />

        <p className="text-center text-sm text-muted">
          Already registered? <Link to="/login" style={{ fontWeight: 600 }}>Sign In</Link>
        </p>
      </div>

      {/* Email Verification OTP Modal */}
      {showOtpModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(6px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '1rem',
          }}
        >
          <div
            className="auth-card fade-in"
            style={{
              maxWidth: 460,
              width: '100%',
              margin: 0,
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
              border: '1px solid rgba(56, 189, 248, 0.3)',
            }}
          >
            <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: '50%',
                  backgroundColor: 'rgba(56, 189, 248, 0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 12px',
                  fontSize: '28px',
                  border: '1px solid rgba(56, 189, 248, 0.25)',
                }}
              >
                ✉️
              </div>
              <h2 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 6px', color: '#f8fafc' }}>
                Check Your Email
              </h2>
              <p style={{ fontSize: '0.875rem', color: '#94a3b8', margin: 0, lineHeight: 1.5 }}>
                We sent a 6-digit verification code to:
                <br />
                <strong style={{ color: '#38bdf8', fontWeight: 600 }}>{form.email}</strong>
              </p>
              <p style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '6px' }}>
                Please check your inbox (and spam folder) and enter the code below.
              </p>
            </div>

            {otpError && (
              <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
                <span>⚠️</span>
                <span>{otpError}</span>
              </div>
            )}

            <form onSubmit={handleVerifyAndRegister}>
              <div className="form-group">
                <label className="form-label" style={{ textAlign: 'center', display: 'block' }}>
                  Enter 6-Digit Verification Code
                </label>
                <input
                  type="text"
                  className="form-input"
                  style={{
                    letterSpacing: '10px',
                    fontSize: '1.6rem',
                    fontWeight: 700,
                    textAlign: 'center',
                    fontFamily: 'monospace',
                    padding: '12px 16px',
                    backgroundColor: '#0f172a',
                    borderColor: 'rgba(56, 189, 248, 0.4)',
                  }}
                  placeholder="000000"
                  maxLength={6}
                  value={otp}
                  onChange={e => setOtp(e.target.value.replace(/[^0-9]/g, ''))}
                  autoFocus
                  required
                />
              </div>

              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  fontSize: '0.825rem',
                  margin: '1rem 0 1.5rem',
                  color: '#94a3b8',
                }}
              >
                <span>
                  ⏱️ Expires in:{' '}
                  <strong style={{ color: otpExpiresIn < 60 ? '#ef4444' : '#e2e8f0' }}>
                    {formatTimer(otpExpiresIn)}
                  </strong>
                </span>

                <button
                  type="button"
                  onClick={handleResendOtp}
                  disabled={resendCooldown > 0}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: resendCooldown > 0 ? '#64748b' : '#38bdf8',
                    cursor: resendCooldown > 0 ? 'not-allowed' : 'pointer',
                    fontWeight: 600,
                    fontSize: '0.825rem',
                    padding: 0,
                  }}
                >
                  {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : '🔄 Resend Code'}
                </button>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ flex: 1 }}
                  onClick={() => setShowOtpModal(false)}
                  disabled={verifyingOtp}
                >
                  ← Edit Details
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ flex: 2 }}
                  disabled={verifyingOtp || otp.length !== 6 || otpExpiresIn === 0}
                >
                  {verifyingOtp ? (
                    <>
                      <span style={{ display: 'inline-block', width: 14, height: 14, border: '2px solid rgba(255,255,255,0.3)', borderTopColor: 'white', borderRadius: '50%', animation: 'spin 0.7s linear infinite', marginRight: 6 }} />
                      Verifying…
                    </>
                  ) : (
                    'Confirm & Sign Up →'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* Staff Pending Approval Success Modal */}
      {pendingStaffSuccess && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            backdropFilter: 'blur(6px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '1rem',
          }}
        >
          <div
            className="auth-card fade-in"
            style={{
              maxWidth: 480,
              width: '100%',
              margin: 0,
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
              border: '1px solid rgba(245, 158, 11, 0.4)',
              textAlign: 'center',
              padding: '2rem',
            }}
          >
            <div
              style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                backgroundColor: 'rgba(245, 158, 11, 0.12)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px',
                fontSize: '32px',
                border: '1px solid rgba(245, 158, 11, 0.3)',
              }}
            >
              ⏳
            </div>
            <h2 style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text)', marginBottom: '0.5rem' }}>
              Pending Administrator Approval
            </h2>
            <p style={{ fontSize: '0.9rem', color: 'var(--text-2)', lineHeight: 1.6, marginBottom: '1.5rem' }}>
              Welcome, <strong style={{ color: 'var(--text)' }}>{form.firstName} {form.lastName}</strong>! Your staff account request for{' '}
              <strong style={{ color: '#fbbf24' }}>{ROLES.find(r => r.value === form.role)?.label}</strong> has been registered and email-verified.
              <br /><br />
              In accordance with hospital security protocols, an <strong>Administrator must approve your account</strong> before you can sign in.
            </p>
            <button
              type="button"
              className="btn btn-primary btn-lg w-full"
              onClick={() => navigate('/login')}
            >
              Back to Sign In →
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Register;
