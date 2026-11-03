import React, { useEffect, useState, useCallback } from 'react';
import { labApi, LabOrder } from '../api/lab';
import { patientApi } from '../api/patients';
import { useToast } from '../context/ToastContext';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse } from '../types/patient';

const COMMON_TESTS = [
  'Complete Blood Count (CBC)',
  'Comprehensive Metabolic Panel (CMP)',
  'Lipid Profile',
  'Liver Function Test (LFT)',
  'Kidney Function Test (KFT)',
  'Thyroid Stimulating Hormone (TSH)',
  'HbA1c (Glycated Hemoglobin)',
  'Urine Routine & Microscopy',
  'Chest X-Ray PA View',
  'Blood Glucose (Fasting)',
  'Dengue NS1 Antigen & IgG/IgM',
  'Serum Electrolytes (Na, K, Cl)',
  'C-Reactive Protein (CRP)'
];

const Labs: React.FC = () => {
  const { showToast } = useToast();
  const { can } = usePermissions();
  const [orders, setOrders] = useState<LabOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [patients, setPatients] = useState<PatientResponse[]>([]);

  const [showOrderModal, setShowOrderModal] = useState(false);
  const [orderForm, setOrderForm] = useState({
    patientId: '',
    testName: '',
    category: 'BLOOD',
    priority: 'ROUTINE',
  });

  const [uploadOrder, setUploadOrder] = useState<LabOrder | null>(null);
  const [uploadResult, setUploadResult] = useState('');
  const [uploadStatus, setUploadStatus] = useState('COMPLETED');
  const [submitting, setSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await labApi.getOrders();
      const payload = res.data?.data !== undefined ? res.data.data : res.data;
      const list = payload?.content || (Array.isArray(payload) ? payload : []);
      setOrders(Array.isArray(list) ? list : []);
    } catch {
      setOrders([]);
      showToast('Failed to load lab orders', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { 
    loadData();
    patientApi.getPatients(0, 200).then(res => {
      const pl = (res as any)?.data?.content || (res as any)?.content || (Array.isArray((res as any)?.data) ? (res as any).data : []);
      setPatients(Array.isArray(pl) ? pl : []);
    }).catch(() => {});
  }, [loadData]);

  const handleCreateOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!orderForm.patientId || !orderForm.testName.trim()) {
      showToast('Please select a patient and enter a test name', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await labApi.createOrder({
        patientId: orderForm.patientId,
        testName: orderForm.testName.trim(),
        category: orderForm.category,
        priority: orderForm.priority,
      });
      showToast('Diagnostic test order created successfully', 'success');
      setShowOrderModal(false);
      setOrderForm({
        patientId: '',
        testName: '',
        category: 'BLOOD',
        priority: 'ROUTINE',
      });
      loadData();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to create lab order', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadOrder) return;
    setSubmitting(true);
    try {
      await labApi.updateResult(uploadOrder.id, { result: uploadResult, status: uploadStatus });
      showToast('Lab result uploaded successfully', 'success');
      setUploadOrder(null);
      setUploadResult('');
      loadData();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to upload result', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const statusBadge = (s: string) => {
    const map: Record<string, string> = {
      PENDING: 'badge-warning', SAMPLE_COLLECTED: 'badge-info', PROCESSING: 'badge-secondary', COMPLETED: 'badge-success', CANCELLED: 'badge-danger'
    };
    const dotCls = s === 'COMPLETED' ? 'green' : s === 'PENDING' || s === 'SAMPLE_COLLECTED' ? 'amber' : 'red';
    return (
      <span className={`badge ${map[s] || 'badge-secondary'}`}>
        <span className={`status-dot-pulse ${dotCls}`} />
        {s.replace('_', ' ')}
      </span>
    );
  };

  return (
    <div className="fade-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">Diagnostic Pathology &amp; Laboratories</h1>
          <p className="page-subtitle">Track blood tests, pathology work orders, and digital diagnostic reports.</p>
        </div>
        {can('canOrderLabTest') && (
          <button className="btn btn-primary" onClick={() => setShowOrderModal(true)}>
            🔬 + Order Lab Test
          </button>
        )}
      </div>

      <div className="table-wrapper">
        <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontWeight: 600 }}>Diagnostic Queue</span>
          <span className="badge badge-secondary">{orders.length} requisitions</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Patient</th>
              <th>Diagnostic Test</th>
              <th>Status</th>
              <th>Order Date</th>
              <th className="text-right">Lab Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="text-center py-4">Loading diagnostics...</td></tr>
            ) : (!Array.isArray(orders) || orders.length === 0) ? (
              <tr><td colSpan={6} className="text-center text-muted py-4">No laboratory test orders found</td></tr>
            ) : (Array.isArray(orders) ? orders : []).map(order => (
              <tr key={order.id}>
                <td>
                  <code style={{ fontSize: '.8rem', background: 'var(--surface-2)', padding: '0.2rem 0.5rem', borderRadius: 4, border: '1px solid var(--border)' }}>
                    LAB-{(order.id || '').slice(0, 8).toUpperCase()}
                  </code>
                </td>
                <td>
                  <div style={{ fontWeight: 700, color: 'var(--text)' }}>{order.patientName}</div>
                </td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600 }}>
                    <span>🔬</span>
                    <span>{order.testName}</span>
                  </div>
                </td>
                <td>{statusBadge(order.status)}</td>
                <td>{new Date(order.orderDate).toLocaleDateString()}</td>
                <td>
                  <div className="table-actions">
                    {can('canUploadLabResult') && (
                      <button className="btn btn-secondary btn-sm" onClick={() => setUploadOrder(order)}>
                        📄 Upload Result
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Upload Modal */}
      {uploadOrder && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setUploadOrder(null)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Upload Result — {uploadOrder.testName}</span>
              <button className="modal-close" onClick={() => setUploadOrder(null)}>×</button>
            </div>
            <form onSubmit={handleUpload}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Patient</label>
                  <p>{uploadOrder.patientName}</p>
                </div>
                <div className="form-group">
                  <label className="form-label">New Status</label>
                  <select className="form-select" value={uploadStatus} onChange={e => setUploadStatus(e.target.value)}>
                    <option value="SAMPLE_COLLECTED">Sample Collected</option>
                    <option value="PROCESSING">Processing</option>
                    <option value="COMPLETED">Completed</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Test Result Notes *</label>
                  <textarea 
                    className="form-input" 
                    rows={4} 
                    value={uploadResult} 
                    onChange={e => setUploadResult(e.target.value)} 
                    placeholder="Enter clinical findings..." 
                    required={uploadStatus === 'COMPLETED'}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setUploadOrder(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Saving...' : 'Save Result'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Order Lab Test Modal */}
      {showOrderModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowOrderModal(false)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">🔬 Order Diagnostic / Pathology Test</span>
              <button className="modal-close" onClick={() => setShowOrderModal(false)}>×</button>
            </div>
            <form onSubmit={handleCreateOrder}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Select Patient *</label>
                  <select
                    className="form-select"
                    value={orderForm.patientId}
                    onChange={e => setOrderForm(f => ({ ...f, patientId: e.target.value }))}
                    required
                  >
                    <option value="">Choose patient for requisition...</option>
                    {patients.map(p => {
                      const pid = p.patientId || (p as any).id || '';
                      return (
                        <option key={pid} value={pid}>
                          {p.firstName} {p.lastName} ({p.uniquePatientId || (pid ? pid.slice(0, 8) : 'N/A')})
                        </option>
                      );
                    })}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Diagnostic Test Name *</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. Complete Blood Count (CBC) or type custom"
                    value={orderForm.testName}
                    onChange={e => setOrderForm(f => ({ ...f, testName: e.target.value }))}
                    list="common-tests-list"
                    required
                  />
                  <datalist id="common-tests-list">
                    {COMMON_TESTS.map(t => (
                      <option key={t} value={t} />
                    ))}
                  </datalist>
                </div>

                <div className="grid-2">
                  <div className="form-group">
                    <label className="form-label">Diagnostic Category *</label>
                    <select
                      className="form-select"
                      value={orderForm.category}
                      onChange={e => setOrderForm(f => ({ ...f, category: e.target.value }))}
                      required
                    >
                      <option value="BLOOD">BLOOD (Hematology)</option>
                      <option value="URINE">URINE (Clinical Pathology)</option>
                      <option value="BIOCHEMISTRY">BIOCHEMISTRY</option>
                      <option value="PATHOLOGY">PATHOLOGY</option>
                      <option value="RADIOLOGY">RADIOLOGY (X-Ray / Scan)</option>
                      <option value="MICROBIOLOGY">MICROBIOLOGY (Cultures)</option>
                      <option value="IMMUNOLOGY">IMMUNOLOGY / Serology</option>
                      <option value="HISTOPATHOLOGY">HISTOPATHOLOGY (Biopsy)</option>
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Requisition Priority *</label>
                    <select
                      className="form-select"
                      value={orderForm.priority}
                      onChange={e => setOrderForm(f => ({ ...f, priority: e.target.value }))}
                      required
                    >
                      <option value="ROUTINE">Routine (Standard)</option>
                      <option value="URGENT">Urgent (Priority processing)</option>
                      <option value="STAT">STAT (Immediate Emergency)</option>
                    </select>
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowOrderModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Prescribing...' : 'Prescribe & Create Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Labs;
