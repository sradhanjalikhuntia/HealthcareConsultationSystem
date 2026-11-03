import React, { useState, useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { GlobalSearch } from '../components/ui/GlobalSearch';
import { SessionExpiryWarning } from '../components/ui/SessionExpiryWarning';
import { useTheme } from '../hooks/useTheme';
import { appointmentApi } from '../api/appointments';

interface NavItem {
  label: string;
  patientLabel?: string;
  path: string;
  icon: string;
  roles?: string[];
}

const NAV: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard', icon: '📊' },
  { label: 'Patients', path: '/patients', icon: '🏥', roles: ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST'] },
  { label: 'Appointments', patientLabel: 'My Appointments', path: '/appointments', icon: '📅' },
  { label: 'Pharmacy', path: '/pharmacy', icon: '💊', roles: ['ADMIN', 'DOCTOR', 'NURSE'] },
  { label: 'Labs & Pathology', path: '/labs', icon: '🔬', roles: ['ADMIN', 'DOCTOR', 'NURSE'] },
  { label: 'Wards & Beds', path: '/wards', icon: '🛏️', roles: ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST'] },
  { label: 'Billing & Invoices', patientLabel: 'My Invoices', path: '/billing', icon: '💳', roles: ['ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT'] },
  { label: 'Administration', path: '/admin', icon: '⚙️', roles: ['ADMIN'] },
];

const roleBadgeClass = (role?: string) => {
  switch (role) {
    case 'ADMIN': return 'badge-danger';
    case 'DOCTOR': return 'badge-info';
    case 'NURSE': return 'badge-success';
    case 'RECEPTIONIST': return 'badge-warning';
    case 'BILLING_ADMIN': return 'badge-secondary';
    case 'PATIENT': return 'badge-primary';
    default: return 'badge-secondary';
  }
};

const roleDisplayName = (role?: string) => {
  switch (role) {
    case 'ADMIN': return 'Administrator';
    case 'DOCTOR': return 'Physician / Doctor';
    case 'NURSE': return 'Staff Nurse';
    case 'RECEPTIONIST': return 'Front Desk';
    case 'BILLING_ADMIN': return 'Billing & Accounts';
    case 'PATIENT': return 'Verified Patient';
    default: return role || 'Staff';
  }
};

const AppLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [showSearch, setShowSearch] = useState(false);
  const [todayAppointmentsCount, setTodayAppointmentsCount] = useState(0);
  const { theme, toggle: toggleTheme } = useTheme();

  useEffect(() => {
    let isMounted = true;
    if (user && ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN', 'PATIENT'].includes(user.role)) {
      const today = new Date().toISOString().split('T')[0];
      appointmentApi.getAll(0, 50)
        .then(res => {
          if (isMounted) {
            const list = res.data?.content || [];
            const count = list.filter((a: any) => 
              a.appointmentDate === today && a.status === 'SCHEDULED'
            ).length;
            setTodayAppointmentsCount(count);
          }
        })
        .catch(err => {
          if (err.response?.status === 403) {
            console.warn('User does not have permission to view appointment counts');
          }
        });
    }
    return () => {
      isMounted = false;
    };
  }, [user]);

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 60000);
    
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        setShowSearch(true);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    
    return () => {
      clearInterval(timer);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const handleLogout = () => { logout(); navigate('/login'); };

  const isPatient = user?.role === 'PATIENT';
  const visible = NAV.filter(n => !n.roles || (user && n.roles.includes(user.role)));

  const greeting = () => {
    const h = currentTime.getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  };

  const formatDate = () => currentTime.toLocaleDateString('en-IN', {
    weekday: 'short', month: 'short', day: 'numeric',
  });

  const formatTime = () => currentTime.toLocaleTimeString('en-IN', {
    hour: '2-digit', minute: '2-digit',
  });

  return (
    <div className="app-shell">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          style={{ position: 'fixed', inset: 0, background: 'rgba(3,7,18,0.7)', backdropFilter: 'blur(4px)', zIndex: 99 }}
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside className={`sidebar${sidebarOpen ? ' open' : ''}`}>
        <div className="sidebar-logo">
          <div className="logo-badge">
            <span>🏥</span>
          </div>
          <div>
            <h2>ApexCare</h2>
            <span>{isPatient ? 'Patient Care Portal' : 'Clinical Health OS'}</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-section">{isPatient ? 'Patient Services' : 'Clinical Command'}</div>
          {visible.map(item => {
            const label = (isPatient && item.patientLabel) ? item.patientLabel : item.label;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <span style={{ fontSize: '1.15rem', display: 'flex', alignItems: 'center' }}>
                  {item.icon}
                  {item.path === '/appointments' && todayAppointmentsCount > 0 && (
                    <span className="notification-badge">{todayAppointmentsCount}</span>
                  )}
                </span>
                <span>{label}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          {/* Real-time connection badge */}
          <div className="flex items-center gap-2 mb-2" style={{ fontSize: '0.72rem', color: 'var(--success)' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--success)', display: 'inline-block', boxShadow: '0 0 8px var(--success)' }} />
            <span style={{ fontWeight: 600 }}>Connected · Local Node</span>
          </div>

          <div className="user-pill" title={user?.lastLoginAt ? `Last Sign-in: ${new Date(user.lastLoginAt).toLocaleString()}` : undefined}>
            <div className="avatar">{user?.firstName?.[0] || 'U'}</div>
            <div className="user-info">
              <div className="user-name">
                {user?.role === 'DOCTOR' ? `Dr. ${user?.firstName} ${user?.lastName}` : `${user?.firstName} ${user?.lastName}`}
              </div>
              <div style={{ marginTop: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
                <span className={`badge ${roleBadgeClass(user?.role)}`} style={{ fontSize: '0.65rem', padding: '2px 8px', alignSelf: 'flex-start' }}>
                  {roleDisplayName(user?.role)}
                </span>
                {user?.lastLoginAt && (
                  <span className="text-xs text-muted" style={{ fontSize: '0.65rem' }}>
                    Signed in: {new Date(user.lastLoginAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                )}
              </div>
            </div>
          </div>

          <button className="btn btn-secondary btn-sm w-full" style={{ justifyContent: 'center' }} onClick={handleLogout}>
            <span>🚪</span>
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Container */}
      <div className="main-content">
        <header className="topbar">
          <button
            className="btn btn-secondary btn-sm"
            id="mobile-menu-btn"
            onClick={() => setSidebarOpen(o => !o)}
            style={{ display: 'none' }}
            aria-label="Toggle sidebar"
          >
            ☰
          </button>

          {/* Hospital Campus Pill */}
          <div className="hospital-badge">
            <span>📍</span>
            <span>ApexCare Super Speciality · New Delhi</span>
          </div>

          <div style={{ flex: 1 }} />

          <div className="flex items-center gap-3">
            <span className="text-sm text-muted greeting-text" style={{ fontWeight: 500 }}>
              {greeting()}, {user?.role === 'DOCTOR' ? `Dr. ${user?.firstName}` : user?.firstName} &nbsp;·&nbsp;
              <strong style={{ color: 'var(--text)' }}>{formatTime()}</strong> ({formatDate()})
            </span>

            <button 
              className="btn btn-secondary btn-sm" 
              onClick={toggleTheme}
              title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
            >
              {theme === 'dark' ? '☀️' : '🌙'}
            </button>

            <button
              className="btn btn-secondary btn-sm"
              onClick={() => setShowSearch(true)}
              title="Search (Ctrl+K)"
              style={{ gap: '0.5rem' }}
            >
              <span>🔍</span>
              <span className="greeting-text">Search</span>
              <span className="cmd-key" style={{ fontSize: '0.65rem', padding: '1px 5px' }}>Ctrl K</span>
            </button>

            <div className="avatar" style={{ width: 34, height: 34, fontSize: '0.85rem' }}>
              {user?.firstName?.[0] || 'U'}
            </div>
          </div>
        </header>

        <main className="page-content fade-in">
          <Outlet />
        </main>
      </div>

      {showSearch && <GlobalSearch onClose={() => setShowSearch(false)} />}
      <SessionExpiryWarning />
    </div>
  );
};

export default AppLayout;
