import React, { useEffect, useState, useCallback } from 'react';
import { pharmacyApi, PharmacyItem } from '../api/pharmacy';
import { patientApi } from '../api/patients';
import { useToast } from '../context/ToastContext';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse } from '../types/patient';

const Pharmacy: React.FC = () => {
  const { showToast } = useToast();
  const { can } = usePermissions();
  const [inventory, setInventory] = useState<PharmacyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'ALL' | 'LOW_STOCK' | 'EXPIRING'>('ALL');
  const [patients, setPatients] = useState<PatientResponse[]>([]);

  const [dispenseItem, setDispenseItem] = useState<PharmacyItem | null>(null);
  const [restockItem, setRestockItem] = useState<PharmacyItem | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  
  const [dispenseForm, setDispenseForm] = useState({ patientId: '', quantity: 1 });
  const [restockForm, setRestockForm] = useState({ quantity: 10, costPrice: 0 });
  const [addForm, setAddForm] = useState({
    medicineName: '',
    genericName: '',
    category: 'ANTIBIOTIC',
    dosageForm: 'TABLET',
    strength: '',
    stockQuantity: 100,
    reorderThreshold: 20,
    unitPrice: 10.0,
    batchNumber: '',
    expiryDate: '',
    manufacturer: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      let list: any[] = [];
      if (tab === 'ALL') {
        const res = await pharmacyApi.getInventory();
        const payload = res.data?.data !== undefined ? res.data.data : res.data;
        list = payload?.content || (Array.isArray(payload) ? payload : []);
      } else if (tab === 'LOW_STOCK') {
        const res = await pharmacyApi.getLowStock();
        const payload = res.data?.data !== undefined ? res.data.data : res.data;
        list = Array.isArray(payload) ? payload : (payload?.content || []);
      } else {
        const res = await pharmacyApi.getExpiringSoon();
        const payload = res.data?.data !== undefined ? res.data.data : res.data;
        list = Array.isArray(payload) ? payload : (payload?.content || []);
      }
      setInventory(Array.isArray(list) ? list : []);
    } catch {
      setInventory([]);
      showToast('Failed to load pharmacy data', 'error');
    } finally {
      setLoading(false);
    }
  }, [tab, showToast]);

  useEffect(() => { 
    loadData(); 
    patientApi.getPatients(0, 200).then(res => {
      const pl = (res as any)?.data?.content || (res as any)?.content || (Array.isArray((res as any)?.data) ? (res as any).data : []);
      setPatients(Array.isArray(pl) ? pl : []);
    }).catch(() => {});
  }, [loadData]);

  const handleDispense = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!dispenseItem) return;
    setSubmitting(true);
    try {
      const selectedPatient = patients.find(p => (p.patientId || (p as any).id) === dispenseForm.patientId);
      await pharmacyApi.dispense({
        itemId: dispenseItem.id,
        quantity: dispenseForm.quantity,
        patientId: dispenseForm.patientId || undefined,
        patientName: selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName}` : undefined,
        autoAddToBill: true,
      });
      showToast(
        dispenseForm.patientId 
          ? 'Medicine dispensed & automatically added to patient billing record!' 
          : 'Medicine dispensed successfully',
        'success'
      );
      setDispenseItem(null);
      setDispenseForm({ patientId: '', quantity: 1 });
      loadData();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to dispense item', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRestock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!restockItem) return;
    setSubmitting(true);
    try {
      await pharmacyApi.restock({
        itemId: restockItem.id,
        quantity: restockForm.quantity,
        costPrice: restockForm.costPrice,
      });
      showToast('Item restocked successfully', 'success');
      setRestockItem(null);
      setRestockForm({ quantity: 10, costPrice: 0 });
      loadData();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to restock item', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAddMedicine = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await pharmacyApi.addItem({
        medicineName: addForm.medicineName.trim(),
        genericName: addForm.genericName.trim(),
        category: addForm.category,
        dosageForm: addForm.dosageForm,
        strength: addForm.strength.trim(),
        stockQuantity: Number(addForm.stockQuantity) || 0,
        reorderThreshold: Number(addForm.reorderThreshold) || 10,
        unitPrice: Number(addForm.unitPrice) || 0,
        batchNumber: addForm.batchNumber.trim() || undefined,
        expiryDate: addForm.expiryDate || undefined,
        manufacturer: addForm.manufacturer.trim() || undefined,
      });
      showToast('Medicine added to inventory successfully', 'success');
      setShowAddModal(false);
      setAddForm({
        medicineName: '',
        genericName: '',
        category: 'ANTIBIOTIC',
        dosageForm: 'TABLET',
        strength: '',
        stockQuantity: 100,
        reorderThreshold: 20,
        unitPrice: 10.0,
        batchNumber: '',
        expiryDate: '',
        manufacturer: '',
      });
      loadData();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to add medicine', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fade-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">Pharmacy &amp; Formulary Stock</h1>
          <p className="page-subtitle">Track prescription medications, manage batches, and handle patient dispenses with automatic billing.</p>
        </div>
        {can('canAddMedicine') && (
          <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
            💊 + Add New Medicine
          </button>
        )}
      </div>

      <div className="tab-pills-bar">
        {[
          { id: 'ALL', label: `📦 All Medications (${Array.isArray(inventory) ? inventory.length : 0})` },
          { id: 'LOW_STOCK', label: '⚠️ Low Stock Alerts' },
          { id: 'EXPIRING', label: '⏳ Expiring Batches' },
        ].map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id as any)}
            className={`tab-pill ${tab === t.id ? 'active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Pharmaceutical Item</th>
              <th>Category</th>
              <th>Unit Retail</th>
              <th>Inventory Count</th>
              <th>Batch Expiry</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="text-center py-4">Checking dispensary shelves...</td></tr>
            ) : (!Array.isArray(inventory) || inventory.length === 0) ? (
              <tr><td colSpan={6} className="text-center text-muted py-4">No medications match current filter criteria</td></tr>
            ) : (Array.isArray(inventory) ? inventory : []).map(item => {
              const name = item.medicineName || item.itemName || 'Medication';
              const isLow = item.lowStock ?? (item.stockQuantity <= (item.reorderThreshold || item.reorderLevel || 10));
              return (
                <tr key={item.id}>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
                      <span style={{ fontSize: '1.2rem' }}>💊</span>
                      <div>
                        <div style={{ fontWeight: 700, color: 'var(--text)' }}>
                          {name} {item.strength && <span className="text-xs text-muted">({item.strength})</span>}
                        </div>
                        <div className="text-xs text-muted">
                          {item.genericName ? `${item.genericName} • ` : ''}ID: {item.id?.slice(0, 8)}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td><span className="badge badge-secondary">{item.category}</span></td>
                  <td><span style={{ fontWeight: 700 }}>${(item.unitPrice || 0).toFixed(2)}</span></td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                      <span className={`status-dot-pulse ${isLow ? 'red' : 'green'}`} />
                      <span style={{ fontWeight: 700, color: isLow ? 'var(--danger)' : 'var(--text)' }}>
                        {item.stockQuantity} units
                      </span>
                      {isLow && <span className="badge badge-danger text-xs">Low Stock</span>}
                    </div>
                  </td>
                  <td>
                    {item.expiryDate ? (
                      <span style={{ fontSize: '0.88rem' }}>{new Date(item.expiryDate).toLocaleDateString()}</span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td>
                    <div className="table-actions">
                      {can('canRestockPharmacy') && (
                        <button className="btn btn-secondary btn-sm" onClick={() => setRestockItem(item)}>
                          ➕ Restock
                        </button>
                      )}
                      {can('canDispenseMedication') && (
                        <button className="btn btn-primary btn-sm" onClick={() => setDispenseItem(item)} disabled={item.stockQuantity === 0}>
                          📋 Dispense
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Dispense Modal with Auto-Billing integration */}
      {dispenseItem && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setDispenseItem(null)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">Dispense {dispenseItem.medicineName || dispenseItem.itemName}</span>
              <button className="modal-close" onClick={() => setDispenseItem(null)}>×</button>
            </div>
            <form onSubmit={handleDispense}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Patient *</label>
                  <select className="form-select" value={dispenseForm.patientId} onChange={e => setDispenseForm(f => ({ ...f, patientId: e.target.value }))} required>
                    <option value="">Select patient for dispensing...</option>
                    {patients.map(p => (
                      <option key={p.patientId || (p as any).id} value={p.patientId || (p as any).id}>{p.firstName} {p.lastName} ({p.uniquePatientId})</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Quantity *</label>
                  <input type="number" min="1" max={dispenseItem.stockQuantity} className="form-input" value={dispenseForm.quantity} onChange={e => setDispenseForm(f => ({ ...f, quantity: parseInt(e.target.value) || 1 }))} required />
                </div>

                {/* Auto Billing Price Breakdown preview */}
                <div style={{ padding: '0.85rem', background: 'rgba(56, 189, 248, 0.08)', border: '1px solid rgba(56, 189, 248, 0.25)', borderRadius: 'var(--radius)', marginTop: '0.75rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '0.25rem' }}>
                    <span className="text-muted">Unit Retail Price:</span>
                    <span style={{ fontWeight: 600 }}>${Number(dispenseItem.unitPrice || 0).toFixed(2)}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.95rem', fontWeight: 700, color: 'var(--accent)' }}>
                    <span>Total Charge to Patient:</span>
                    <span>${((dispenseItem.unitPrice || 0) * (dispenseForm.quantity || 1)).toFixed(2)}</span>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-3)', marginTop: '0.35rem' }}>
                    ⚡ This charge will automatically attach to the patient's billing invoice.
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setDispenseItem(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Dispensing...' : 'Confirm Dispense & Bill'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Restock Modal */}
      {restockItem && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setRestockItem(null)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">Restock {restockItem.medicineName || restockItem.itemName}</span>
              <button className="modal-close" onClick={() => setRestockItem(null)}>×</button>
            </div>
            <form onSubmit={handleRestock}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Quantity *</label>
                  <input type="number" min="1" className="form-input" value={restockForm.quantity} onChange={e => setRestockForm(f => ({ ...f, quantity: parseInt(e.target.value) || 0 }))} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Total Cost Price ($) *</label>
                  <input type="number" min="0" step="0.01" className="form-input" value={restockForm.costPrice} onChange={e => setRestockForm(f => ({ ...f, costPrice: parseFloat(e.target.value) || 0 }))} required />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setRestockItem(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Restocking...' : 'Confirm Restock'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add New Medicine Modal */}
      {showAddModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowAddModal(false)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">💊 Add New Medicine / Formulary Item</span>
              <button className="modal-close" onClick={() => setShowAddModal(false)}>×</button>
            </div>
            <form onSubmit={handleAddMedicine}>
              <div className="modal-body">
                <div className="grid-2">
                  <div className="form-group">
                    <label className="form-label">Brand / Medicine Name *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Augmentin 625"
                      value={addForm.medicineName}
                      onChange={e => setAddForm(f => ({ ...f, medicineName: e.target.value }))}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Generic / Molecule Name *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Amoxicillin + Clavulanic Acid"
                      value={addForm.genericName}
                      onChange={e => setAddForm(f => ({ ...f, genericName: e.target.value }))}
                      required
                    />
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label className="form-label">Category *</label>
                    <select
                      className="form-select"
                      value={addForm.category}
                      onChange={e => setAddForm(f => ({ ...f, category: e.target.value }))}
                      required
                    >
                      <option value="ANTIBIOTIC">Antibiotic</option>
                      <option value="ANALGESIC">Analgesic / Pain Relief</option>
                      <option value="ANTIPYRETIC">Antipyretic (Fever)</option>
                      <option value="CARDIOVASCULAR">Cardiovascular</option>
                      <option value="GASTROINTESTINAL">Gastrointestinal</option>
                      <option value="RESPIRATORY">Respiratory</option>
                      <option value="ANTIHISTAMINE">Antihistamine / Allergy</option>
                      <option value="ANTIDIABETIC">Antidiabetic</option>
                      <option value="VITAMIN_SUPPLEMENT">Vitamins &amp; Supplements</option>
                      <option value="TOPICAL">Topical / Dermatology</option>
                      <option value="OPHTHALMIC">Ophthalmic (Eye/Ear)</option>
                      <option value="OTHER">Other / General</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Dosage Form *</label>
                    <select
                      className="form-select"
                      value={addForm.dosageForm}
                      onChange={e => setAddForm(f => ({ ...f, dosageForm: e.target.value }))}
                      required
                    >
                      <option value="TABLET">Tablet</option>
                      <option value="CAPSULE">Capsule</option>
                      <option value="SUSPENSION">Syrup / Suspension</option>
                      <option value="INJECTION">Injection (IV/IM)</option>
                      <option value="OINTMENT">Ointment / Cream</option>
                      <option value="DROPS">Drops (Eye/Ear)</option>
                      <option value="INHALER">Inhaler</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Strength *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. 500mg, 10mg/ml"
                      value={addForm.strength}
                      onChange={e => setAddForm(f => ({ ...f, strength: e.target.value }))}
                      required
                    />
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label className="form-label">Unit Retail Price ($) *</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      className="form-input"
                      value={addForm.unitPrice}
                      onChange={e => setAddForm(f => ({ ...f, unitPrice: parseFloat(e.target.value) || 0 }))}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Initial Stock *</label>
                    <input
                      type="number"
                      min="0"
                      className="form-input"
                      value={addForm.stockQuantity}
                      onChange={e => setAddForm(f => ({ ...f, stockQuantity: parseInt(e.target.value) || 0 }))}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Reorder Alert Level *</label>
                    <input
                      type="number"
                      min="1"
                      className="form-input"
                      value={addForm.reorderThreshold}
                      onChange={e => setAddForm(f => ({ ...f, reorderThreshold: parseInt(e.target.value) || 10 }))}
                      required
                    />
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label className="form-label">Batch Number</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. BT-2026-99"
                      value={addForm.batchNumber}
                      onChange={e => setAddForm(f => ({ ...f, batchNumber: e.target.value }))}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Expiry Date</label>
                    <input
                      type="date"
                      className="form-input"
                      value={addForm.expiryDate}
                      onChange={e => setAddForm(f => ({ ...f, expiryDate: e.target.value }))}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Manufacturer</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Cipla, Sun Pharma"
                      value={addForm.manufacturer}
                      onChange={e => setAddForm(f => ({ ...f, manufacturer: e.target.value }))}
                    />
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowAddModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Saving Medicine...' : 'Save & Register Medicine'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Pharmacy;
