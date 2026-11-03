import React, { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { patientApi } from '../api/patients';
import { useToast } from '../context/ToastContext';
import { useDebounce } from '../hooks/useDebounce';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse, PatientRequest } from '../types/patient';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

const formatBloodGroup = (bg?: string | null): string => {
  if (!bg) return '';
  return bg.replace('_POSITIVE', '+').replace('_NEGATIVE', '-');
};

const emptyForm: PatientRequest = {
  firstName: '', lastName: '', dateOfBirth: '', gender: 'MALE', phone: '',
  email: '', address: '', bloodGroup: '', emergencyContact: '', medicalHistory: '', allergies: '',
};

const Patients: React.FC = () => {
  const { showToast } = useToast();
  const { can } = usePermissions();
  const [patients, setPatients] = useState<PatientResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 400);
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [showModal, setShowModal] = useState(false);
  const [editPatient, setEditPatient] = useState<PatientResponse | null>(null);
  const [form, setForm] = useState<PatientRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<PatientResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const fetchPatients = useCallback(async () => {
    setLoading(true);
    try {
      // If we had a dedicated status filter API param, we would pass it here
      const res = await patientApi.getPatients(page, 10, debouncedSearch || undefined);
      
      let rawList = res.data?.content || (res as any)?.content || (Array.isArray(res.data) ? res.data : []);
      let filtered = Array.isArray(rawList) ? rawList : [];
      if (statusFilter && Array.isArray(filtered)) {
        // Client-side fallback if backend doesn't support status filtering
        filtered = filtered.filter((p: PatientResponse) => p.status === statusFilter);
      }
      
      setPatients(filtered);
      setTotalPages(res.data?.totalPages || 1);
      setTotalElements(res.data?.totalElements ?? filtered.length);
    } catch {
      showToast('Failed to load patients', 'error');
    } finally {
      setLoading(false);
    }
  }, [page, debouncedSearch, statusFilter, showToast]);

  useEffect(() => { fetchPatients(); }, [fetchPatients]);

  const openCreate = () => { setEditPatient(null); setForm(emptyForm); setShowModal(true); };
  const openEdit = (p: PatientResponse) => {
    setEditPatient(p);
    setForm({
      firstName: p.firstName, lastName: p.lastName, dateOfBirth: p.dateOfBirth,
      gender: p.gender, phone: p.phone, email: p.email || '', address: p.address || '',
      bloodGroup: formatBloodGroup(p.bloodGroup), emergencyContact: p.emergencyContact || '',
      medicalHistory: p.medicalHistory || '', allergies: p.allergies || '',
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      if (editPatient) {
        await patientApi.updatePatient(editPatient.patientId, form);
        showToast('Patient updated successfully', 'success');
      } else {
        await patientApi.createPatient(form);
        showToast('Patient registered successfully', 'success');
      }
      setShowModal(false);
      fetchPatients();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Operation failed', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await patientApi.deactivatePatient(deleteTarget.patientId);
      showToast('Patient deactivated', 'success');
      setDeleteTarget(null);
      fetchPatients();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to deactivate', 'error');
    } finally {
      setDeleting(false);
    }
  };

  const handleReactivate = async (patient: PatientResponse) => {
    try {
      await patientApi.reactivatePatient(patient.patientId);
      showToast(`${patient.firstName} ${patient.lastName} reactivated successfully to ACTIVE`, 'success');
      fetchPatients();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to reactivate patient', 'error');
    }
  };

  const set = (k: keyof PatientRequest) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));

  const getInitials = (fn?: string, ln?: string) => {
    return `${fn?.[0] || ''}${ln?.[0] || ''}`.toUpperCase() || 'PT';
  };

  const statusBadge = (s: string) => {
    const cls = s === 'ACTIVE' ? 'badge-success' : s === 'DISCHARGED' ? 'badge-warning' : 'badge-danger';
    return (
      <span className={`badge ${cls}`}>
        <span className={`status-dot-pulse ${s === 'ACTIVE' ? 'green' : s === 'DISCHARGED' ? 'amber' : 'red'}`} />
        {s}
      </span>
    );
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Patient Directory</h1>
          <p className="page-subtitle">Centralized Electronic Health Records &amp; Patient Registry · {totalElements} registered</p>
        </div>
        {can('canRegisterPatient') && (
          <button className="btn btn-primary" onClick={openCreate}>
            <span>+</span> Register New Patient
          </button>
        )}
      </div>

      <div className="table-wrapper">
        <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap', flex: 1 }}>
            <div style={{ position: 'relative', minWidth: 260, maxWidth: 400, flex: 1 }}>
              <span style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', opacity: 0.5, pointerEvents: 'none' }}>🔍</span>
              <input 
                className="form-input" 
                placeholder="Search by name, phone, or ID…" 
                style={{ paddingLeft: '2.5rem', width: '100%' }}
                value={search} 
                onChange={e => { setSearch(e.target.value); setPage(0); }} 
              />
              {search && (
                <button 
                  onClick={() => setSearch('')} 
                  style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', opacity: 0.6, fontSize: '0.8rem', background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}
                >
                  ✕
                </button>
              )}
            </div>
            <select 
              className="form-select" 
              style={{ width: 170 }}
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            >
              <option value="">All Statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="DISCHARGED">Discharged</option>
              <option value="DECEASED">Deceased</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span className="badge badge-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.78rem' }}>
              {patients.length} {patients.length === 1 ? 'patient' : 'patients'} displayed
            </span>
          </div>
        </div>

        <table>
          <thead>
            <tr>
              <th>Patient</th>
              <th>UHID / MRN</th>
              <th>DOB / Gender</th>
              <th>Contact Info</th>
              <th>Blood Group</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              [1,2,3,4,5].map(i => (
                <tr key={i}><td colSpan={7}><div className="skeleton skeleton-row" /></td></tr>
              ))
            ) : patients.length === 0 ? (
              <tr><td colSpan={7}>
                <div className="empty-state">
                  <div className="empty-state-icon">🏥</div>
                  <div className="empty-state-title">No patients found</div>
                  <div className="empty-state-desc">Try adjusting your search criteria or register a new patient.</div>
                </div>
              </td></tr>
            ) : patients.map(p => (
              <tr key={p.patientId}>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
                    <div className="avatar-circle">
                      {getInitials(p.firstName, p.lastName)}
                    </div>
                    <div>
                      <Link to={`/patients/${p.patientId}`} style={{ fontWeight: 700, color: 'var(--text)', fontSize: '0.95rem' }}>
                        {p.firstName} {p.lastName}
                      </Link>
                      <div className="text-xs text-muted" style={{ marginTop: '0.15rem' }}>
                        Registered: {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : 'N/A'}
                      </div>
                    </div>
                  </div>
                </td>
                <td>
                  <code style={{ fontSize: '.78rem', background: 'var(--surface-2)', padding: '0.2rem 0.45rem', borderRadius: 4, border: '1px solid var(--border)' }}>
                    {p.uniquePatientId}
                  </code>
                </td>
                <td>
                  <div style={{ fontWeight: 500 }}>{p.dateOfBirth}</div>
                  <span className="text-xs text-muted" style={{ textTransform: 'capitalize' }}>{p.gender?.toLowerCase()}</span>
                </td>
                <td>
                  <div style={{ fontWeight: 500 }}>{p.phone}</div>
                  <div className="text-xs text-muted truncate" style={{ maxWidth: 160 }}>{p.email || '—'}</div>
                </td>
                <td>
                  {formatBloodGroup(p.bloodGroup) ? (
                    <span className="badge-blood">{formatBloodGroup(p.bloodGroup)}</span>
                  ) : (
                    <span className="text-muted">—</span>
                  )}
                </td>
                <td>{statusBadge(p.status)}</td>
                <td>
                  <div className="table-actions">
                    <Link to={`/patients/${p.patientId}`} className="btn btn-secondary btn-sm" title="View Patient Details">
                      View
                    </Link>
                    {can('canEditPatient') && (
                      <button className="btn btn-secondary btn-sm" onClick={() => openEdit(p)} title="Edit Patient Info">
                        ✎
                      </button>
                    )}
                    {can('canDeletePatient') && (
                      p.status === 'INACTIVE' ? (
                        <button
                          className="btn btn-success btn-sm"
                          onClick={() => handleReactivate(p)}
                          title="Reactivate Patient to ACTIVE"
                          style={{
                            background: 'rgba(16, 185, 129, 0.15)',
                            color: '#10b981',
                            borderColor: 'rgba(16, 185, 129, 0.35)',
                            padding: '0.25rem 0.5rem',
                            fontWeight: 600
                          }}
                        >
                          🔄 Activate
                        </button>
                      ) : (
                        <button className="btn btn-danger btn-sm" onClick={() => setDeleteTarget(p)} title="Deactivate Patient">
                          ✕
                        </button>
                      )
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {totalPages > 1 && (
          <div className="pagination">
            <span>Showing page {page + 1} of {totalPages} ({totalElements} total entries)</span>
            <div className="page-btns">
              <button className="btn btn-secondary btn-sm" onClick={() => setPage(p => p - 1)} disabled={page === 0}>← Previous</button>
              <button className="btn btn-secondary btn-sm" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next →</button>
            </div>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
          <div className="modal modal-lg">
            <div className="modal-header">
              <span className="modal-title">{editPatient ? 'Edit Patient' : 'Register New Patient'}</span>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <form onSubmit={handleSubmit}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">First Name *</label>
                    <input className="form-input" value={form.firstName} onChange={set('firstName')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Last Name *</label>
                    <input className="form-input" value={form.lastName} onChange={set('lastName')} required />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Date of Birth *</label>
                    <input type="date" className="form-input" value={form.dateOfBirth} onChange={set('dateOfBirth')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Gender *</label>
                    <select className="form-select" value={form.gender} onChange={set('gender')} required>
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Phone *</label>
                    <input className="form-input" value={form.phone} onChange={set('phone')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Email</label>
                    <input type="email" className="form-input" value={form.email} onChange={set('email')} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Blood Group</label>
                    <select className="form-select" value={form.bloodGroup} onChange={set('bloodGroup')}>
                      <option value="">Select…</option>
                      {BLOOD_GROUPS.map(b => <option key={b} value={b}>{b}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Emergency Contact</label>
                    <input className="form-input" value={form.emergencyContact} onChange={set('emergencyContact')} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Address</label>
                  <input className="form-input" value={form.address} onChange={set('address')} />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Allergies</label>
                    <input className="form-input" value={form.allergies} onChange={set('allergies')} placeholder="e.g. Penicillin, Peanuts" />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Medical History</label>
                    <input className="form-input" value={form.medicalHistory} onChange={set('medicalHistory')} placeholder="Chronic conditions, surgeries…" />
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Saving…' : (editPatient ? 'Save Changes' : 'Register')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Delete Modal */}
      {deleteTarget && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setDeleteTarget(null)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">Confirm Deactivation</span>
              <button className="modal-close" onClick={() => setDeleteTarget(null)}>×</button>
            </div>
            <div className="modal-body">
              <p>Are you sure you want to deactivate <strong>{deleteTarget.firstName} {deleteTarget.lastName}</strong>? This action can be reversed by an admin.</p>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setDeleteTarget(null)}>Cancel</button>
              <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
                {deleting ? 'Deactivating…' : 'Deactivate'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Patients;
