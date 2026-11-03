import React, { useEffect, useState, useCallback } from 'react';
import { billingApi, BillResponse, BillingSummaryResponse, UnbilledChargesSummary } from '../api/billing';
import { patientApi } from '../api/patients';
import { insuranceApi, InsuranceClaim } from '../api/insurance';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse } from '../types/patient';
import { PaymentGatewayModal } from '../components/PaymentGatewayModal';

const emptyForm = {
  patientId: '', consultationFee: 0, medicineCharges: 0, labCharges: 0, wardCharges: 0, otherCharges: 0,
  discountPercentage: 0, taxPercentage: 5, notes: '',
};

const Billing: React.FC = () => {
  const { showToast } = useToast();
  const { user } = useAuth();
  const { can, isPatient } = usePermissions();
  const [bills, setBills] = useState<BillResponse[]>([]);
  const [stats, setStats] = useState<BillingSummaryResponse | null>(null);
  const [patients, setPatients] = useState<PatientResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showPayModal, setShowPayModal] = useState<BillResponse | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [tab, setTab] = useState<'INVOICES' | 'INSURANCE'>('INVOICES');
  const [claims, setClaims] = useState<InsuranceClaim[]>([]);
  const [loadingClaims, setLoadingClaims] = useState(false);

  // Auto-accumulate state
  const [unbilledSummary, setUnbilledSummary] = useState<UnbilledChargesSummary | null>(null);
  const [loadingUnbilled, setLoadingUnbilled] = useState(false);

  // Quick auto-bill modal
  const [showAutoBillModal, setShowAutoBillModal] = useState(false);
  const [autoBillPatientId, setAutoBillPatientId] = useState('');
  const [autoBilling, setAutoBilling] = useState(false);

  // Insurance Modal
  const [updateClaim, setUpdateClaim] = useState<InsuranceClaim | null>(null);
  const [claimStatus, setClaimStatus] = useState('');
  const [claimNotes, setClaimNotes] = useState('');

  const fetchClaims = useCallback(async () => {
    setLoadingClaims(true);
    try {
      const res = await insuranceApi.getClaims(0, 100);
      const data = res.data?.content || res.data;
      setClaims(Array.isArray(data) ? data : []);
    } catch {
      setClaims([]);
      showToast('Failed to load insurance claims', 'error');
    } finally {
      setLoadingClaims(false);
    }
  }, [showToast]);

  const fetchBills = useCallback(async () => {
    setLoading(true);
    try {
      if (user?.role === 'PATIENT') {
        const patientId = user.linkedPatientId || user.id;
        const res = await billingApi.getBillsByPatient(patientId);
        let list = Array.isArray(res.data) ? res.data : [];
        if (statusFilter) {
          list = list.filter((b: BillResponse) => b.paymentStatus === statusFilter);
        }
        setBills(list);
        setTotalPages(1);
      } else {
        const res = await billingApi.getAllBills(page, 10, statusFilter || undefined);
        setBills(Array.isArray(res.data?.content) ? res.data.content : []);
        setTotalPages(res.data?.totalPages || 1);
      }
    } catch {
      showToast('Failed to load billing data', 'error');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, user, showToast]);

  useEffect(() => { 
    if (tab === 'INVOICES') fetchBills(); 
    else fetchClaims();
  }, [tab, fetchBills, fetchClaims]);

  const fetchSummary = useCallback(() => {
    if (user && (user.role === 'ADMIN' || user.role === 'BILLING_ADMIN' || user.role === 'RECEPTIONIST')) {
      billingApi.getBillingSummary().then(r => setStats(r.data)).catch(() => {});
    }
  }, [user]);

  useEffect(() => {
    fetchSummary();
    const interval = setInterval(fetchSummary, 4000);
    return () => clearInterval(interval);
  }, [fetchSummary]);

  useEffect(() => {
    if (user && user.role !== 'PATIENT') {
      patientApi.getPatients(0, 100).then(r => {
        const pl = (r as any)?.data?.content || (r as any)?.content || (Array.isArray(r?.data) ? r.data : []);
        setPatients(Array.isArray(pl) ? pl : []);
      }).catch(() => {});
    }
  }, [user]);

  const handlePatientSelect = async (patientId: string) => {
    setForm(f => ({ ...f, patientId }));
    if (!patientId) {
      setUnbilledSummary(null);
      return;
    }
    setLoadingUnbilled(true);
    try {
      const res = await billingApi.getUnbilledSummary(patientId);
      const raw = res as any;
      const summary: UnbilledChargesSummary = raw?.data?.data !== undefined ? raw.data.data : (raw?.data || raw);
      if (summary) {
        setUnbilledSummary(summary);
        setForm(f => ({
          ...f,
          patientId,
          consultationFee: summary.consultationFee ?? 500,
          medicineCharges: summary.medicineCharges ?? 0,
          labCharges: summary.labCharges ?? 0,
          wardCharges: summary.wardCharges ?? 0,
          otherCharges: summary.otherCharges ?? 0,
          notes: summary.items && summary.items.length > 0
            ? `Auto-aggregated: ${summary.items.length} items (${summary.items.map((i: any) => i.description).slice(0, 3).join(', ')}...)`
            : f.notes,
        }));
      }
    } catch {
      setUnbilledSummary(null);
    } finally {
      setLoadingUnbilled(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await billingApi.createBill(form);
      showToast('Invoice created and usage charges finalized successfully', 'success');
      setShowModal(false);
      setForm(emptyForm);
      setUnbilledSummary(null);
      fetchBills();
      fetchSummary();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to create invoice', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleQuickAutoBill = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!autoBillPatientId) return;
    setAutoBilling(true);
    try {
      await billingApi.generateAutoBill(autoBillPatientId);
      showToast('Consolidated bill auto-generated from all patient usages!', 'success');
      setShowAutoBillModal(false);
      setAutoBillPatientId('');
      fetchBills();
      fetchSummary();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to auto-generate bill', 'error');
    } finally {
      setAutoBilling(false);
    }
  };

  const handleUpdateClaim = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!updateClaim) return;
    setSubmitting(true);
    try {
      await insuranceApi.adjudicate(updateClaim.id, { status: claimStatus, notes: claimNotes });
      showToast('Claim updated successfully', 'success');
      setUpdateClaim(null);
      fetchClaims();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to update claim', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const set = (k: keyof typeof emptyForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: k.includes('Fee') || k.includes('Charges') || k.includes('Percentage') ? parseFloat(e.target.value) || 0 : e.target.value }));

  const statusBadge = (s: string) => {
    const m: Record<string, string> = { 
      PENDING: 'badge-warning', 
      PAID: 'badge-success', 
      CANCELLED: 'badge-danger', 
      FAILED: 'badge-danger', 
      REFUNDED: 'badge-info',
      APPROVED: 'badge-success',
      REJECTED: 'badge-danger',
    };
    const dotCls = s === 'PAID' || s === 'APPROVED' ? 'green' : s === 'PENDING' ? 'amber' : 'red';
    return (
      <span className={`badge ${m[s] || 'badge-secondary'}`}>
        <span className={`status-dot-pulse ${dotCls}`} />
        {s}
      </span>
    );
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">{isPatient ? 'My Invoices & Receipts' : 'Revenue & Billing Operations'}</h1>
          <p className="page-subtitle">
            {isPatient ? 'Review your consultation bills, prescriptions charges, and settle pending payments.' : 'Automatic patient usage billing, financial settlements, and insurance adjudication.'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          {can('canCreateInvoice') && (
            <>
              <button className="btn btn-secondary" onClick={() => setShowAutoBillModal(true)}>
                <span>⚡</span> 1-Click Auto-Bill Patient
              </button>
              <button className="btn btn-primary" onClick={() => { setForm(emptyForm); setUnbilledSummary(null); setShowModal(true); }}>
                <span>+</span> Create New Invoice
              </button>
            </>
          )}
        </div>
      </div>

      {stats && (
        <div className="stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
          {[
            { label: 'Total Revenue', value: `$${Number(stats.totalRevenue || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, color: 'var(--success)', icon: '💰' },
            { label: 'Pending Receivables', value: `$${Number(stats.pendingAmount || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, color: 'var(--warning)', icon: '⏳' },
            { label: 'Total Invoices', value: stats.totalInvoices ?? (stats as any).totalBills ?? 0, color: 'var(--text)', icon: '🧾' },
            { label: 'Settled Bills', value: stats.paidInvoices ?? (stats as any).paidBills ?? 0, color: 'var(--success)', icon: '✅' },
            { label: 'Pending Bills', value: stats.pendingInvoices ?? (stats as any).pendingBills ?? 0, color: 'var(--warning)', icon: '⏳' },
          ].map(s => (
            <div className="stat-card" key={s.label}>
              <div>
                <div className="stat-label">{s.label}</div>
                <div className="stat-value" style={{ fontSize: '1.65rem', color: s.color }}>{s.value}</div>
              </div>
              <div className="stat-icon">{s.icon}</div>
            </div>
          ))}
        </div>
      )}

      {can('canManageInsurance') && (
        <div className="tab-pills-bar">
          <button className={`tab-pill ${tab === 'INVOICES' ? 'active' : ''}`} onClick={() => setTab('INVOICES')}>
            🧾 Patient Invoices
          </button>
          <button className={`tab-pill ${tab === 'INSURANCE' ? 'active' : ''}`} onClick={() => setTab('INSURANCE')}>
            🛡️ Insurance Claims ({Array.isArray(claims) ? claims.length : 0})
          </button>
        </div>
      )}

      {tab === 'INVOICES' ? (
      <div className="table-wrapper">
        <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <select className="form-select" style={{ width: 190 }} value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setPage(0); }}>
              <option value="">All Invoice Statuses</option>
              <option value="PENDING">Pending Payment</option>
              <option value="PAID">Paid / Cleared</option>
              <option value="REFUNDED">Refunded</option>
              <option value="FAILED">Failed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          <span className="badge badge-secondary" style={{ fontSize: '0.75rem' }}>
            {bills.length} invoices found
          </span>
        </div>
        <table>
          <thead>
            <tr>
              <th>Invoice Number</th>
              <th>Issued Date</th>
              <th>Patient</th>
              <th>Charges Breakdown</th>
              <th>Total Amount</th>
              <th>Status</th>
              <th className="text-right">Settlement</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              [1,2,3].map(i => <tr key={i}><td colSpan={7}><div className="skeleton skeleton-row" /></td></tr>)
            ) : bills.length === 0 ? (
              <tr><td colSpan={7}>
                <div className="empty-state">
                  <div className="empty-state-icon">💳</div>
                  <div className="empty-state-title">No invoices found</div>
                  <div className="empty-state-desc">Generate an invoice to start billing patients.</div>
                </div>
              </td></tr>
            ) : bills.map(b => (
              <tr key={b.id || Math.random()}>
                <td><code style={{ fontSize: '.8rem' }}>{b.id ? b.id.slice(0, 8) : 'N/A'}…</code></td>
                <td>{b.createdAt ? new Date(b.createdAt).toLocaleDateString() : 'N/A'}</td>
                <td>
                  <div style={{ fontWeight: 600 }}>{b.patientName || 'ApexCare Patient'}</div>
                  <div className="text-xs text-muted">ID: {b.patientId ? b.patientId.slice(0, 8) : 'N/A'}…</div>
                </td>
                <td style={{ fontSize: '0.8rem' }}>
                  <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                    {(b.consultationFee ?? 0) > 0 && <span className="badge badge-secondary">👨‍⚕️ Consult: ${b.consultationFee}</span>}
                    {(b.medicineCharges ?? 0) > 0 && <span className="badge badge-info">💊 Meds: ${b.medicineCharges}</span>}
                    {(b.labCharges ?? 0) > 0 && <span className="badge badge-primary">🔬 Lab: ${b.labCharges}</span>}
                    {(b.wardCharges ?? 0) > 0 && <span className="badge badge-warning" style={{ color: '#f59e0b', background: 'rgba(245, 158, 11, 0.1)' }}>🛏️ Bed: ${b.wardCharges}</span>}
                    {(b.otherCharges ?? 0) > 0 && <span className="badge badge-secondary">📦 Other: ${b.otherCharges}</span>}
                  </div>
                </td>
                <td>
                  <span style={{ fontWeight: 800, color: 'var(--text)', fontSize: '1rem' }}>
                    ${Number(b.totalAmount).toFixed(2)}
                  </span>
                </td>
                <td>{statusBadge(b.paymentStatus)}</td>
                <td>
                  <div className="table-actions">
                    {b.paymentStatus === 'PENDING' && can('canProcessPayment') && (
                      <button className="btn btn-success btn-sm" onClick={() => setShowPayModal(b)}>
                        💳 Pay Now
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {totalPages > 1 && (
          <div className="pagination">
            <span>Page {page + 1} of {totalPages}</span>
            <div className="page-btns">
              <button className="btn btn-secondary btn-sm" onClick={() => setPage(p => p - 1)} disabled={page === 0}>← Previous</button>
              <button className="btn btn-secondary btn-sm" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next →</button>
            </div>
          </div>
        )}
      </div>
      ) : (
      <div className="table-wrapper">
        <table>
          <thead>
            <tr><th>Claim ID</th><th>Patient</th><th>Provider</th><th>Policy Number</th><th>Claim Amount</th><th>Status</th><th className="text-right">Actions</th></tr>
          </thead>
          <tbody>
            {loadingClaims ? (
              <tr><td colSpan={7} className="text-center">Loading claims...</td></tr>
            ) : !Array.isArray(claims) || claims.length === 0 ? (
              <tr><td colSpan={7} className="text-center text-muted py-4">No insurance claims found</td></tr>
            ) : (Array.isArray(claims) ? claims : []).map(c => (
              <tr key={c.id || Math.random()}>
                <td><code className="text-xs">{c.id ? c.id.slice(0, 8) : 'N/A'}…</code></td>
                <td>{c.patientName}</td>
                <td>{c.provider}</td>
                <td>{c.policyNumber}</td>
                <td style={{ fontWeight: 600 }}>${Number(c.claimAmount).toFixed(2)}</td>
                <td>{statusBadge(c.status)}</td>
                <td>
                  <div className="table-actions">
                    <button className="btn btn-secondary btn-sm" onClick={() => { setUpdateClaim(c); setClaimStatus(c.status); setClaimNotes(c.notes || ''); }}>Update</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      )}

      {/* 1-Click Auto Bill Modal */}
      {showAutoBillModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowAutoBillModal(false)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">⚡ 1-Click Auto-Bill from Patient Usage</span>
              <button className="modal-close" onClick={() => setShowAutoBillModal(false)}>×</button>
            </div>
            <form onSubmit={handleQuickAutoBill}>
              <div className="modal-body">
                <p className="text-sm text-muted mb-3">
                  This scans all consultations, pharmacy dispensed medications, and laboratory tests for the patient, and consolidates them into a single bill automatically.
                </p>
                <div className="form-group">
                  <label className="form-label">Select Patient *</label>
                  <select className="form-select" value={autoBillPatientId} onChange={e => setAutoBillPatientId(e.target.value)} required>
                    <option value="">Select a patient…</option>
                    {patients.map(p => {
                      const pid = p.patientId || (p as any).id;
                      return (
                        <option key={pid} value={pid}>{p.firstName} {p.lastName} {p.uniquePatientId ? `(${p.uniquePatientId})` : ''}</option>
                      );
                    })}
                  </select>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowAutoBillModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={autoBilling || !autoBillPatientId}>
                  {autoBilling ? 'Auto-Generating…' : 'Generate Consolidated Bill'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create Invoice Modal with Live Usage Detection */}
      {showModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Create Patient Invoice</span>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Patient *</label>
                  <select className="form-select" value={form.patientId} onChange={e => handlePatientSelect(e.target.value)} required>
                    <option value="">Select a patient to auto-fetch usage…</option>
                    {patients.map(p => {
                      const pid = p.patientId || (p as any).id;
                      return (
                        <option key={pid} value={pid}>{p.firstName} {p.lastName} {p.uniquePatientId ? `(${p.uniquePatientId})` : ''}</option>
                      );
                    })}
                  </select>
                </div>

                {loadingUnbilled && (
                  <div style={{ padding: '0.65rem 0.85rem', background: 'var(--surface-2)', borderRadius: 'var(--radius)', marginBottom: '1rem', fontSize: '0.85rem' }}>
                    🔍 Scanning patient consultations, dispensed medicines &amp; lab tests...
                  </div>
                )}

                {unbilledSummary && (
                  <div style={{ padding: '0.85rem', background: 'rgba(56, 189, 248, 0.08)', border: '1px solid rgba(56, 189, 248, 0.3)', borderRadius: 'var(--radius)', marginBottom: '1rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.45rem' }}>
                      <span style={{ fontWeight: 700, fontSize: '0.88rem', color: 'var(--accent)' }}>
                        ⚡ Auto-Detected Patient Usages ({unbilledSummary.items?.length || 0} items)
                      </span>
                      <span className="badge badge-success text-xs">Values Auto-Filled</span>
                    </div>
                    <div style={{ maxHeight: 110, overflowY: 'auto', fontSize: '0.8rem', color: 'var(--text-2)' }}>
                      {unbilledSummary.items && unbilledSummary.items.length > 0 ? (
                        unbilledSummary.items.map((item, idx) => (
                          <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.2rem 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                            <span>{item.type === 'MEDICINE' ? '💊' : item.type === 'LAB_TEST' ? '🔬' : item.type === 'WARD' ? '🛏️' : '👨‍⚕️'} {item.description}</span>
                            <span style={{ fontWeight: 600 }}>${Number(item.amount || 0).toFixed(2)}</span>
                          </div>
                        ))
                      ) : (
                        <div className="text-muted">No pending unbilled pharmacy, lab, or ward stays found. Standard consultation applied.</div>
                      )}
                    </div>
                  </div>
                )}

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Consultation Fee ($) *</label>
                    <input type="number" step="0.01" min="0" className="form-input" value={form.consultationFee} onChange={set('consultationFee')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Medicine Charges ($)</label>
                    <input type="number" step="0.01" min="0" className="form-input" value={form.medicineCharges} onChange={set('medicineCharges')} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Lab Charges ($)</label>
                    <input type="number" step="0.01" min="0" className="form-input" value={form.labCharges} onChange={set('labCharges')} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Ward / Bed Stay ($)</label>
                    <input type="number" step="0.01" min="0" className="form-input" value={form.wardCharges} onChange={set('wardCharges')} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Other Charges ($)</label>
                    <input type="number" step="0.01" min="0" className="form-input" value={form.otherCharges} onChange={set('otherCharges')} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Discount (%)</label>
                    <input type="number" step="0.01" min="0" max="100" className="form-input" value={form.discountPercentage} onChange={set('discountPercentage')} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Tax (%)</label>
                    <input type="number" step="0.01" min="0" max="100" className="form-input" value={form.taxPercentage} onChange={set('taxPercentage')} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Notes &amp; Particulars</label>
                  <input className="form-input" value={form.notes} onChange={set('notes')} placeholder="Auto-generated items or clinical summary notes" />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Generating…' : 'Generate & Finalize Invoice'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Payment Terminal Modal */}
      {showPayModal && (
        <PaymentGatewayModal
          bill={showPayModal}
          isPatient={isPatient}
          onClose={() => setShowPayModal(null)}
          onSuccess={() => {
            fetchBills();
            if (user && (user.role === 'ADMIN' || user.role === 'BILLING_ADMIN' || user.role === 'RECEPTIONIST')) {
              billingApi.getBillingSummary().then(r => setStats(r.data)).catch(() => {});
            }
          }}
        />
      )}

      {/* Update Claim Modal */}
      {updateClaim && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setUpdateClaim(null)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">Adjudicate Claim</span>
              <button className="modal-close" onClick={() => setUpdateClaim(null)}>×</button>
            </div>
            <form onSubmit={handleUpdateClaim}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">New Status</label>
                  <select className="form-select" value={claimStatus} onChange={e => setClaimStatus(e.target.value)}>
                    <option value="PENDING">Pending</option>
                    <option value="APPROVED">Approved</option>
                    <option value="REJECTED">Rejected</option>
                    <option value="PAID">Paid</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Notes / Reason</label>
                  <textarea className="form-input" rows={3} value={claimNotes} onChange={e => setClaimNotes(e.target.value)} placeholder="Provide context for the decision..." />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setUpdateClaim(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Saving...' : 'Save Decision'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Billing;
