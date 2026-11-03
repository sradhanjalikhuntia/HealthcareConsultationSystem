import React, { useEffect, useState } from 'react';
import { adminApi, UserResponse, UpdateUserRequest } from '../../api/admin';
import { useToast } from '../../context/ToastContext';
import { auditApi, AuditLog } from '../../api/audit';

const ROLES = ['PATIENT', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN', 'ADMIN'];

const Admin: React.FC = () => {
  const { showToast } = useToast();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [form, setForm] = useState<UpdateUserRequest>({});
  const [submitting, setSubmitting] = useState(false);
  const [tab, setTab] = useState<'USERS' | 'AUDIT'>('USERS');
  const [userFilter, setUserFilter] = useState<'ALL' | 'PENDING' | 'ACTIVE'>('ALL');
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loadingLogs, setLoadingLogs] = useState(false);

  const handleApprove = async (u: UserResponse) => {
    try {
      await adminApi.approveUser(u.id);
      showToast(`Account approved for ${u.firstName} ${u.lastName} (${u.role})! User can now sign in.`, 'success');
      fetchUsers();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to approve user', 'error');
    }
  };

  const handleDisable = async (u: UserResponse) => {
    try {
      await adminApi.updateUser(u.id, { enabled: false });
      showToast(`Account disabled for ${u.firstName} ${u.lastName}.`, 'success');
      fetchUsers();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to update user', 'error');
    }
  };

  const fetchLogs = React.useCallback(async () => {
    setLoadingLogs(true);
    try {
      const res = await auditApi.getLogs();
      const content = res?.data?.content;
      setLogs(Array.isArray(content) ? content : []);
    } catch {
      showToast('Failed to load audit logs', 'error');
      setLogs([]);
    } finally {
      setLoadingLogs(false);
    }
  }, [showToast]);

  const fetchUsers = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminApi.getUsers();
      const content = res?.data?.content || (Array.isArray(res?.data) ? res.data : []);
      setUsers(Array.isArray(content) ? content : []);
    } catch {
      showToast('Failed to load users', 'error');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { 
    if (tab === 'USERS') fetchUsers(); 
    else fetchLogs();
  }, [tab, fetchUsers, fetchLogs]);

  const openEdit = (u: UserResponse) => {
    setEditUser(u);
    setForm({ firstName: u.firstName, lastName: u.lastName, email: u.email, role: u.role, enabled: u.enabled });
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editUser) return;
    setSubmitting(true);
    try {
      const res = await adminApi.updateUser(editUser.id, form);
      const updated = res?.data;
      if (updated && updated.id) {
        setUsers(prev => prev.map(u => (u.id === updated.id ? { ...u, ...updated } : u)));
      }
      showToast('User updated successfully', 'success');
      setEditUser(null);
      fetchUsers();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Update failed', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const roleBadge = (r: string) => {
    const m: Record<string, string> = {
      ADMIN: 'badge-danger',
      DOCTOR: 'badge-info',
      NURSE: 'badge-success',
      PATIENT: 'badge-secondary',
      RECEPTIONIST: 'badge-warning',
      BILLING_ADMIN: 'badge-primary',
    };
    return <span className={`badge ${m[r] || 'badge-secondary'}`}>{r}</span>;
  };

  const getInitials = (fn?: string, ln?: string) => `${fn?.[0] || ''}${ln?.[0] || ''}`.toUpperCase() || 'US';

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Enterprise System Administration</h1>
          <p className="page-subtitle">Identity and access governance, role permission assignment, and compliance logs.</p>
        </div>
      </div>

      <div className="tab-pills-bar">
        <button className={`tab-pill ${tab === 'USERS' ? 'active' : ''}`} onClick={() => setTab('USERS')}>
          👥 User Accounts ({Array.isArray(users) ? users.length : 0})
        </button>
        <button className={`tab-pill ${tab === 'AUDIT' ? 'active' : ''}`} onClick={() => setTab('AUDIT')}>
          🛡️ Compliance Audit Trail ({Array.isArray(logs) ? logs.length : 0})
        </button>
      </div>

      {tab === 'USERS' ? (() => {
        const pendingCount = users.filter(u => !u.enabled).length;
        const filteredUsers = users.filter(u => {
          if (userFilter === 'PENDING') return !u.enabled;
          if (userFilter === 'ACTIVE') return u.enabled;
          return true;
        });

        return (
          <div className="table-wrapper">
            <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className={`btn btn-sm ${userFilter === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setUserFilter('ALL')}
                >
                  All Accounts ({users.length})
                </button>
                <button
                  type="button"
                  className={`btn btn-sm ${userFilter === 'PENDING' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setUserFilter('PENDING')}
                  style={userFilter !== 'PENDING' && pendingCount > 0 ? { borderColor: 'rgba(245, 158, 11, 0.5)', color: '#fbbf24', background: 'rgba(245, 158, 11, 0.1)' } : {}}
                >
                  ⏳ Pending Approval {pendingCount > 0 && `(${pendingCount})`}
                </button>
                <button
                  type="button"
                  className={`btn btn-sm ${userFilter === 'ACTIVE' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setUserFilter('ACTIVE')}
                >
                  Active ({users.length - pendingCount})
                </button>
              </div>
              <span className="badge badge-secondary">{filteredUsers.length} accounts displayed</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>Personnel</th>
                  <th>Email Address</th>
                  <th>Account Login</th>
                  <th>Assigned Role</th>
                  <th>Signed Up</th>
                  <th>Last Sign-In</th>
                  <th>Status</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  [1, 2, 3, 4, 5].map(i => (
                    <tr key={i}><td colSpan={8}><div className="skeleton skeleton-row" /></td></tr>
                  ))
                ) : filteredUsers.length === 0 ? (
                  <tr><td colSpan={8}>
                    <div className="empty-state">
                      <div className="empty-state-icon">⚙️</div>
                      <div className="empty-state-title">No accounts found</div>
                      <div className="empty-state-desc">
                        {userFilter === 'PENDING'
                          ? 'No staff accounts currently awaiting approval.'
                          : 'No registered user accounts found in this view.'}
                      </div>
                    </div>
                  </td></tr>
                ) : filteredUsers.map(u => (
                  <tr key={u.id}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                        <div className="avatar-circle sm">
                          {getInitials(u.firstName, u.lastName)}
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, color: 'var(--text)' }}>{u.firstName} {u.lastName}</div>
                          <div className="text-xs text-muted">ID: {u.id.slice(0, 8)}</div>
                        </div>
                      </div>
                    </td>
                    <td>{u.email}</td>
                    <td>
                      <code style={{ fontSize: '.8rem', background: 'var(--surface-2)', padding: '0.15rem 0.45rem', borderRadius: 4, border: '1px solid var(--border)' }}>
                        {u.username}
                      </code>
                    </td>
                    <td>{roleBadge(u.role)}</td>
                    <td>
                      <span className="text-xs text-muted" title={u.createdAt}>
                        {u.createdAt ? new Date(u.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'}
                      </span>
                    </td>
                    <td>
                      <span className="text-xs" style={{ color: u.lastLoginAt ? 'var(--text)' : 'var(--text-muted)' }} title={u.lastLoginAt}>
                        {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Never'}
                      </span>
                    </td>
                    <td>
                      {!u.enabled ? (
                        <span className="badge badge-warning" style={{ background: 'rgba(245, 158, 11, 0.15)', color: '#fbbf24', borderColor: 'rgba(245, 158, 11, 0.35)' }}>
                          <span className="status-dot-pulse amber" />
                          Pending Approval
                        </span>
                      ) : (
                        <span className="badge badge-success">
                          <span className="status-dot-pulse green" />
                          Active
                        </span>
                      )}
                    </td>
                    <td>
                      <div className="table-actions">
                        {!u.enabled ? (
                          <button
                            type="button"
                            className="btn btn-success btn-sm"
                            onClick={() => handleApprove(u)}
                            style={{ background: '#10b981', color: '#fff', borderColor: '#10b981', fontWeight: 600 }}
                            title="Accept and approve this staff member"
                          >
                            ✅ Accept
                          </button>
                        ) : (
                          <>
                            <button type="button" className="btn btn-secondary btn-sm" onClick={() => openEdit(u)} title="Edit user profile">
                              ✎ Edit
                            </button>
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              onClick={() => handleDisable(u)}
                              title="Disable staff login access"
                              style={{ padding: '0.25rem 0.5rem' }}
                            >
                              🚫
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      })() : (
      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Log ID</th>
              <th>Action</th>
              <th>Actor ID</th>
              <th>Details</th>
              <th>Timestamp</th>
            </tr>
          </thead>
          <tbody>
            {loadingLogs ? (
              <tr><td colSpan={5} className="text-center">Loading logs...</td></tr>
            ) : !Array.isArray(logs) || logs.length === 0 ? (
              <tr><td colSpan={5} className="text-center text-muted">No audit logs found</td></tr>
            ) : logs.map(l => (
              <tr key={l.id}>
                <td><code className="text-xs">{l.id ? `${l.id.slice(0,8)}...` : '-'}</code></td>
                <td><span className="badge badge-secondary">{l.action}</span></td>
                <td><code className="text-xs">{l.actorUsername || (l.actorId ? `${l.actorId.slice(0,8)}...` : '-')}</code></td>
                <td className="text-sm">{l.details || '-'}</td>
                <td>{l.eventTimestamp ? new Date(l.eventTimestamp).toLocaleString() : l.createdAt ? new Date(l.createdAt).toLocaleString() : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      )}

      {editUser && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setEditUser(null)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Edit User — {editUser.username}</span>
              <button className="modal-close" onClick={() => setEditUser(null)}>×</button>
            </div>
            <form onSubmit={handleUpdate}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">First Name</label>
                    <input
                      className="form-input"
                      value={form.firstName || ''}
                      onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Last Name</label>
                    <input
                      className="form-input"
                      value={form.lastName || ''}
                      onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Email</label>
                  <input
                    type="email"
                    className="form-input"
                    value={form.email || ''}
                    onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                  />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Role</label>
                    <select
                      className="form-select"
                      value={form.role || ''}
                      onChange={e => setForm(f => ({ ...f, role: e.target.value }))}
                    >
                      {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Account Status</label>
                    <select
                      className="form-select"
                      value={form.enabled ? 'true' : 'false'}
                      onChange={e => setForm(f => ({ ...f, enabled: e.target.value === 'true' }))}
                    >
                      <option value="true">Active</option>
                      <option value="false">Disabled</option>
                    </select>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setEditUser(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Saving…' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Admin;
