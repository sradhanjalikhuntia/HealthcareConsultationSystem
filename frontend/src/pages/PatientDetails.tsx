import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { patientApi } from '../api/patients';
import { appointmentApi, AppointmentResponse } from '../api/appointments';
import { billingApi, BillResponse } from '../api/billing';
import { vitalsApi, Vitals } from '../api/vitals';
import { referralApi, Referral } from '../api/referrals';
import { useToast } from '../context/ToastContext';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse, PatientRequest } from '../types/patient';
import { PaymentGatewayModal } from '../components/PaymentGatewayModal';
import type { CreateReferralPayload } from '../api/referrals';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

const formatBloodGroup = (bg?: string | null): string => {
  if (!bg) return '';
  return bg.replace('_POSITIVE', '+').replace('_NEGATIVE', '-');
};

const InfoRow = ({ label, value }: { label: string; value?: string | null }) =>
  value ? (
    <div style={{ display: 'flex', gap: '1rem', padding: '.5rem 0', borderBottom: '1px solid var(--border)' }}>
      <span style={{ width: 160, color: 'var(--text-2)', flexShrink: 0, fontSize: '.8rem', fontWeight: 500 }}>{label}</span>
      <span>{value}</span>
    </div>
  ) : null;

const PatientDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { can, isPatient } = usePermissions();
  const [patient, setPatient] = useState<PatientResponse | null>(null);
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [bills, setBills] = useState<BillResponse[]>([]);
  const [vitals, setVitals] = useState<Vitals[]>([]);
  const [referrals, setReferrals] = useState<Referral[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'overview' | 'appointments' | 'billing' | 'vitals' | 'referrals'>('overview');

  // Vitals
  const [showVitalsModal, setShowVitalsModal] = useState(false);
  const [vitalsForm, setVitalsForm] = useState<Partial<Vitals>>({});

  // Referrals
  const [showReferralModal, setShowReferralModal] = useState(false);
  const [referralForm, setReferralForm] = useState<Partial<CreateReferralPayload>>({});

  // Edit patient
  const [showEdit, setShowEdit] = useState(false);
  const [editForm, setEditForm] = useState<PatientRequest | null>(null);
  const [saving, setSaving] = useState(false);

  // Pay bill
  const [payTarget, setPayTarget] = useState<BillResponse | null>(null);
  const [autoBilling, setAutoBilling] = useState(false);

  const load = React.useCallback(async () => {
    if (!id) return;
    try {
      const [pr, ar, br, vr, rr] = await Promise.allSettled([
        patientApi.getPatient(id),
        appointmentApi.getAll(0, 20, undefined, id),
        can('canViewBilling') ? billingApi.getBillsByPatient(id) : Promise.resolve({ data: [] }),
        vitalsApi.getPatientVitals(id),
        referralApi.getPatientReferrals(id).catch(() => ({ data: [] })),
      ]);
      if (pr.status === 'fulfilled') {
        const pRaw = (pr.value as any).data;
        const pData = pRaw?.data !== undefined ? pRaw.data : pRaw;
        setPatient(pData);
      }
      if (ar.status === 'fulfilled') {
        const aRaw = (ar.value as any).data;
        const aData = aRaw?.data !== undefined ? aRaw.data : aRaw;
        const aList = Array.isArray(aData) ? aData : (aData?.content || []);
        setAppointments(Array.isArray(aList) ? aList : []);
      }
      if (br.status === 'fulfilled') {
        const bRaw = (br.value as any).data;
        const bData = bRaw?.data !== undefined ? bRaw.data : bRaw;
        const bList = Array.isArray(bData) ? bData : (bData?.content || []);
        setBills(Array.isArray(bList) ? bList : []);
      }
      if (vr.status === 'fulfilled') {
        const vRaw = (vr.value as any).data;
        const vData = vRaw?.data !== undefined ? vRaw.data : vRaw;
        const vList = Array.isArray(vData) ? vData : (vData?.content || []);
        setVitals(Array.isArray(vList) ? vList : []);
      }
      if (rr.status === 'fulfilled') {
        const rRaw = (rr.value as any).data;
        const rData = rRaw?.data !== undefined ? rRaw.data : rRaw;
        const rList = Array.isArray(rData) ? rData : (rData?.content || []);
        setReferrals(Array.isArray(rList) ? rList.filter((r: any) => !r.patientId || r.patientId === id) : []);
      }
    } catch {
      showToast('Failed to load patient details', 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showToast]);

  useEffect(() => { load(); }, [load]);

  const openEdit = () => {
    if (!patient) return;
    setEditForm({
      firstName: patient.firstName,
      lastName: patient.lastName,
      dateOfBirth: patient.dateOfBirth,
      gender: patient.gender,
      phone: patient.phone,
      email: patient.email || '',
      address: patient.address || '',
      bloodGroup: formatBloodGroup(patient.bloodGroup),
      emergencyContact: patient.emergencyContact || '',
      medicalHistory: patient.medicalHistory || '',
      allergies: patient.allergies || '',
    });
    setShowEdit(true);
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!patient || !editForm) return;
    setSaving(true);
    try {
      const res = await patientApi.updatePatient(patient.patientId, editForm);
      setPatient(res.data);
      showToast('Patient updated successfully', 'success');
      setShowEdit(false);
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to update patient', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleReactivate = async () => {
    if (!patient) return;
    try {
      await patientApi.reactivatePatient(patient.patientId || (patient as any).id);
      showToast(`${patient.firstName} ${patient.lastName} reactivated successfully to ACTIVE`, 'success');
      load();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to reactivate patient', 'error');
    }
  };

  const handleSaveVitals = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setSaving(true);
    try {
      let systolicBp: number | undefined;
      let diastolicBp: number | undefined;
      if (vitalsForm.bloodPressure && vitalsForm.bloodPressure.includes('/')) {
        const parts = vitalsForm.bloodPressure.split('/');
        systolicBp = parseInt(parts[0].trim(), 10) || undefined;
        diastolicBp = parseInt(parts[1].trim(), 10) || undefined;
      }
      const spo2Val = vitalsForm.oxygenSaturation ?? vitalsForm.spO2;

      await vitalsApi.addVitals({
        ...vitalsForm,
        patientId: id,
        systolicBp,
        diastolicBp,
        spO2: spo2Val,
        oxygenSaturation: spo2Val,
        bloodPressure: vitalsForm.bloodPressure,
        recordedBy: 'Current User',
      });
      showToast('Vitals recorded', 'success');
      setShowVitalsModal(false);
      setVitalsForm({});
      load();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to save vitals', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleSaveReferral = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !patient) return;
    setSaving(true);
    try {
      const payload: CreateReferralPayload = {
        patientId: id,
        targetSpecialty: (referralForm.targetSpecialty || 'CARDIOLOGY').trim(),
        priority: referralForm.priority || 'ROUTINE',
        clinicalSummary: referralForm.clinicalSummary || '',
        referralQuestion: referralForm.referralQuestion || '',
      };
      await referralApi.createReferral(payload);
      showToast('Referral created', 'success');
      setShowReferralModal(false);
      setReferralForm({});
      load();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to create referral', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleAutoBill = async () => {
    if (!id) return;
    setAutoBilling(true);
    try {
      await billingApi.generateAutoBill(id);
      showToast('Consolidated bill generated from patient usage!', 'success');
      load();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to auto-generate bill', 'error');
    } finally {
      setAutoBilling(false);
    }
  };

  const setF = (k: keyof PatientRequest) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      setEditForm(f => f ? { ...f, [k]: e.target.value } : f);

  if (loading) return (
    <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-2)' }}>
      <div className="skeleton" style={{ height: 200, marginBottom: '1rem' }} />
      <div className="skeleton" style={{ height: 100 }} />
    </div>
  );
  if (!patient) return <div className="alert alert-error">Patient not found.</div>;


  const statusBadge = (s: string) => {
    const m: Record<string, string> = {
      ACTIVE: 'badge-success', SCHEDULED: 'badge-info', COMPLETED: 'badge-success',
      CANCELLED: 'badge-danger', PENDING: 'badge-warning', PAID: 'badge-success',
      DISCHARGED: 'badge-warning', DECEASED: 'badge-danger', DEACTIVATED: 'badge-secondary',
      FAILED: 'badge-danger', REFUNDED: 'badge-info',
    };
    return <span className={`badge ${m[s] || 'badge-secondary'}`}>{s}</span>;
  };

  const getAge = (dobString?: string) => {
    if (!dobString) return '';
    try {
      const dob = new Date(dobString);
      const diff = Date.now() - dob.getTime();
      return Math.abs(new Date(diff).getUTCFullYear() - 1970);
    } catch {
      return '';
    }
  };

  const getInitials = (fn?: string, ln?: string) => `${fn?.[0] || ''}${ln?.[0] || ''}`.toUpperCase() || 'PT';

  const vitalsList = Array.isArray(vitals) ? vitals : [];
  const referralsList = Array.isArray(referrals) ? referrals : [];
  const appointmentsList = Array.isArray(appointments) ? appointments : [];
  const billsList = Array.isArray(bills) ? bills : [];

  const latestVitals = vitalsList.length > 0 ? vitalsList[0] : null;

  return (
    <div className="fade-in">
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: '1.25rem' }}>
        <Link to="/patients" style={{ color: 'var(--text-2)' }}>Patients</Link>
        <span className="breadcrumb-sep">›</span>
        <span style={{ color: 'var(--text)', fontWeight: 600 }}>{patient.firstName} {patient.lastName}</span>
      </div>

      {/* Hero Profile Banner */}
      <div className="patient-profile-hero">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
          <div className="avatar-circle lg">
            {getInitials(patient.firstName, patient.lastName)}
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem', flexWrap: 'wrap', marginBottom: '0.4rem' }}>
              <h1 className="font-display" style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--text)' }}>
                {patient.firstName} {patient.lastName}
              </h1>
              {statusBadge(patient.status)}
              {formatBloodGroup(patient.bloodGroup) && (
                <span className="badge-blood">{formatBloodGroup(patient.bloodGroup)}</span>
              )}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexWrap: 'wrap', color: 'var(--text-2)', fontSize: '0.88rem' }}>
              <span>
                <strong style={{ color: 'var(--text)' }}>UHID:</strong>{' '}
                <code style={{ background: 'rgba(255,255,255,0.06)', padding: '0.15rem 0.4rem', borderRadius: 4 }}>
                  {patient.uniquePatientId}
                </code>
              </span>
              <span>•</span>
              <span>
                {patient.gender}
                {getAge(patient.dateOfBirth) ? `, ${getAge(patient.dateOfBirth)} yrs` : ''}
              </span>
              <span>•</span>
              <span>📞 {patient.phone}</span>
              {patient.email && (
                <>
                  <span>•</span>
                  <span>✉️ {patient.email}</span>
                </>
              )}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          {can('canDeletePatient') && patient.status === 'INACTIVE' && (
            <button
              className="btn btn-success btn-sm"
              onClick={handleReactivate}
              style={{ background: '#10b981', color: '#fff', borderColor: '#10b981', fontWeight: 600 }}
            >
              🔄 Reactivate Patient
            </button>
          )}
          {can('canEditPatient') && (
            <button className="btn btn-secondary btn-sm" onClick={openEdit}>
              ✎ Edit Record
            </button>
          )}
          {can('canScheduleAppointment') && (
            <Link to="/appointments" className="btn btn-primary btn-sm">
              + Schedule Appointment
            </Link>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => navigate(-1)}>
            ← Back
          </button>
        </div>
      </div>

      {/* Latest Vitals Glance Bar */}
      {latestVitals && (
        <div className="vitals-grid">
          <div className="vital-item-card">
            <div className="vital-icon-box" style={{ background: 'rgba(244, 63, 94, 0.15)', color: '#fb7185' }}>
              ❤️
            </div>
            <div>
              <div className="text-xs text-muted" style={{ fontWeight: 600, textTransform: 'uppercase' }}>Heart Rate</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, fontFamily: 'var(--font-display)' }}>
                {latestVitals.heartRate ? `${latestVitals.heartRate} bpm` : '—'}
              </div>
            </div>
          </div>

          <div className="vital-item-card">
            <div className="vital-icon-box" style={{ background: 'rgba(14, 165, 233, 0.15)', color: '#38bdf8' }}>
              🩺
            </div>
            <div>
              <div className="text-xs text-muted" style={{ fontWeight: 600, textTransform: 'uppercase' }}>Blood Pressure</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, fontFamily: 'var(--font-display)' }}>
                {latestVitals.bloodPressure || (latestVitals.systolicBp && latestVitals.diastolicBp ? `${latestVitals.systolicBp}/${latestVitals.diastolicBp} mmHg` : '—')}
              </div>
            </div>
          </div>

          <div className="vital-item-card">
            <div className="vital-icon-box" style={{ background: 'rgba(245, 158, 11, 0.15)', color: '#fbbf24' }}>
              🌡️
            </div>
            <div>
              <div className="text-xs text-muted" style={{ fontWeight: 600, textTransform: 'uppercase' }}>Body Temp</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, fontFamily: 'var(--font-display)' }}>
                {latestVitals.temperature ? `${latestVitals.temperature} °C` : '—'}
              </div>
            </div>
          </div>

          <div className="vital-item-card">
            <div className="vital-icon-box" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#34d399' }}>
              🫁
            </div>
            <div>
              <div className="text-xs text-muted" style={{ fontWeight: 600, textTransform: 'uppercase' }}>SpO2 Level</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, fontFamily: 'var(--font-display)' }}>
                {(latestVitals.oxygenSaturation ?? latestVitals.spO2) !== undefined && (latestVitals.oxygenSaturation ?? latestVitals.spO2) !== null ? `${latestVitals.oxygenSaturation ?? latestVitals.spO2}%` : '—'}
              </div>
            </div>
          </div>

          <div className="vital-item-card">
            <div className="vital-icon-box" style={{ background: 'rgba(168, 85, 247, 0.15)', color: '#c084fc' }}>
              ⚖️
            </div>
            <div>
              <div className="text-xs text-muted" style={{ fontWeight: 600, textTransform: 'uppercase' }}>Weight</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, fontFamily: 'var(--font-display)' }}>
                {latestVitals.weight ? `${latestVitals.weight} kg` : '—'}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="tab-pills-bar">
        {([
          { id: 'overview', label: '👤 Clinical Overview' },
          { id: 'vitals', label: `🩺 Vitals (${vitalsList.length})` },
          { id: 'referrals', label: `📄 Referrals (${referralsList.length})` },
          { id: 'appointments', label: `📅 Consultations (${appointmentsList.length})` },
          ...(can('canViewBilling') ? [{ id: 'billing', label: `💳 Invoices (${billsList.length})` }] : []),
        ]).map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id as any)}
            className={`tab-pill ${tab === t.id ? 'active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Overview Tab */}
      {tab === 'overview' && (
        <div className="grid-2">
          <div className="card">
            <h3 className="card-title">
              <span>Personal Demographics</span>
              <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--text-3)' }}>EHR ID: {(patient.patientId || (patient as any).id || '').slice(0, 8)}</span>
            </h3>
            <InfoRow label="Legal Name" value={`${patient.firstName} ${patient.lastName}`} />
            <InfoRow label="Date of Birth" value={`${patient.dateOfBirth} (${getAge(patient.dateOfBirth)} years old)`} />
            <InfoRow label="Gender" value={patient.gender} />
            <InfoRow label="Blood Group" value={formatBloodGroup(patient.bloodGroup)} />
            <InfoRow label="Mobile Phone" value={patient.phone} />
            <InfoRow label="Email Address" value={patient.email} />
            <InfoRow label="Residential Address" value={patient.address} />
            <InfoRow label="Emergency Contact" value={patient.emergencyContact} />
          </div>
          <div className="card">
            <h3 className="card-title">Medical &amp; Clinical Background</h3>
            <div className="form-group">
              <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span style={{ color: 'var(--danger)' }}>⚠️</span> Known Allergies &amp; Drug Sensitivities
              </label>
              <div style={{ 
                padding: '0.75rem 1rem', 
                background: patient.allergies ? 'rgba(244, 63, 94, 0.08)' : 'var(--surface-2)', 
                border: `1px solid ${patient.allergies ? 'rgba(244, 63, 94, 0.25)' : 'var(--border)'}`, 
                borderRadius: 'var(--radius)',
                color: patient.allergies ? '#fb7185' : 'var(--text-2)'
              }}>
                {patient.allergies || 'No known allergies reported.'}
              </div>
            </div>
            <div className="form-group" style={{ marginTop: '1.25rem' }}>
              <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span>📋</span> Chronic Conditions &amp; Medical History
              </label>
              <div style={{ 
                padding: '0.85rem 1rem', 
                background: 'var(--surface-2)', 
                border: '1px solid var(--border)', 
                borderRadius: 'var(--radius)',
                whiteSpace: 'pre-wrap',
                lineHeight: 1.6
              }}>
                {patient.medicalHistory || 'No significant prior medical conditions recorded.'}
              </div>
            </div>
            <hr className="divider" />
            <div style={{ fontSize: '.75rem', color: 'var(--text-3)', display: 'flex', justifyContent: 'space-between' }}>
              <span>Registered: {new Date(patient.createdAt).toLocaleDateString()}</span>
              <span>Last Modified: {new Date(patient.updatedAt).toLocaleDateString()}</span>
            </div>
          </div>
        </div>
      )}

      {/* Vitals Tab */}
      {tab === 'vitals' && (
        <div className="table-wrapper">
          {can('canAddVitals') && (
            <div className="toolbar" style={{ justifyContent: 'flex-end', padding: '1rem' }}>
              <button className="btn btn-primary btn-sm" onClick={() => setShowVitalsModal(true)}>+ Add Vitals</button>
            </div>
          )}
          {vitalsList.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">🩺</div>
              <div className="empty-state-title">No vitals recorded</div>
              <div className="empty-state-desc">Patient vitals have not been recorded yet.</div>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Heart Rate</th>
                  <th>BP</th>
                  <th>Temp (°C)</th>
                  <th>Weight (kg)</th>
                  <th>SpO2 (%)</th>
                </tr>
              </thead>
              <tbody>
                {vitalsList.map(v => (
                  <tr key={v.id}>
                    <td>{new Date(v.recordedAt).toLocaleString()}</td>
                    <td>{v.heartRate || '—'} bpm</td>
                    <td>{v.bloodPressure || (v.systolicBp && v.diastolicBp ? `${v.systolicBp}/${v.diastolicBp} mmHg` : '—')}</td>
                    <td>{v.temperature ? `${v.temperature} °C` : '—'}</td>
                    <td>{v.weight ? `${v.weight} kg` : '—'}</td>
                    <td>{(v.oxygenSaturation ?? v.spO2) !== undefined && (v.oxygenSaturation ?? v.spO2) !== null ? `${v.oxygenSaturation ?? v.spO2}%` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Referrals Tab */}
      {tab === 'referrals' && (
        <div className="table-wrapper">
          {can('canCreateReferral') && (
            <div className="toolbar" style={{ justifyContent: 'flex-end', padding: '1rem' }}>
              <button className="btn btn-primary btn-sm" onClick={() => setShowReferralModal(true)}>+ New Referral</button>
            </div>
          )}
          {referralsList.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">📄</div>
              <div className="empty-state-title">No referrals</div>
              <div className="empty-state-desc">Patient has no referrals.</div>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Target Specialty</th>
                  <th>Clinical Summary</th>
                  <th>Priority</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {referralsList.map(r => (
                  <tr key={r.id}>
                    <td>{new Date(r.createdAt).toLocaleDateString()}</td>
                    <td>{r.targetSpecialty}</td>
                    <td>{r.clinicalSummary || r.reason || '—'}</td>
                    <td>
                      <span className={`badge ${r.priority === 'EMERGENCY' ? 'badge-danger' : r.priority === 'URGENT' ? 'badge-warning' : 'badge-secondary'}`}>
                        {r.priority || 'ROUTINE'}
                      </span>
                    </td>
                    <td>{statusBadge(r.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Appointments Tab */}
      {tab === 'appointments' && (
        <div className="table-wrapper">
          {appointmentsList.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">📅</div>
              <div className="empty-state-title">No appointments yet</div>
              <div className="empty-state-desc">
                {can('canScheduleAppointment') && (
                  <Link to="/appointments" className="btn btn-primary btn-sm" style={{ marginTop: '.5rem' }}>
                    + Schedule Appointment
                  </Link>
                )}
              </div>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Date &amp; Time</th>
                  <th>Doctor</th>
                  <th>Reason</th>
                  <th>Notes</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {appointmentsList.map(a => (
                  <tr key={a.appointmentId}>
                    <td>
                      <div style={{ fontWeight: 500 }}>{a.appointmentDate}</div>
                      <div className="text-sm text-muted">{a.startTime} – {a.endTime}</div>
                    </td>
                    <td>{a.doctorName}</td>
                    <td>{a.reason}</td>
                    <td className="text-sm text-muted">{a.notes || '—'}</td>
                    <td>{statusBadge(a.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Billing Tab */}
      {tab === 'billing' && (
        <div className="table-wrapper">
          {can('canCreateInvoice') && (
            <div className="toolbar" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', padding: '1rem' }}>
              <button className="btn btn-secondary btn-sm" onClick={handleAutoBill} disabled={autoBilling}>
                {autoBilling ? 'Auto-Calculating…' : '⚡ Auto-Generate Bill from Patient Usage'}
              </button>
              <Link to="/billing" className="btn btn-primary btn-sm">
                + Create Custom Invoice
              </Link>
            </div>
          )}
          {billsList.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">💳</div>
              <div className="empty-state-title">No billing records</div>
              {can('canCreateInvoice') && (
                <div className="empty-state-desc">
                  <Link to="/billing" className="btn btn-primary btn-sm" style={{ marginTop: '.5rem' }}>
                    + Create Invoice
                  </Link>
                </div>
              )}
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Invoice ID</th>
                  <th>Date</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {billsList.map(b => (
                  <tr key={b.id}>
                    <td><code style={{ fontSize: '.75rem' }}>{(b.id || (b as any).billId || '').slice(0, 8)}…</code></td>
                    <td>{new Date(b.createdAt).toLocaleDateString()}</td>
                    <td style={{ fontWeight: 600 }}>${Number(b.totalAmount).toFixed(2)}</td>
                    <td>{statusBadge(b.paymentStatus)}</td>
                    <td>
                      <div className="table-actions">
                        {b.paymentStatus === 'PENDING' && can('canProcessPayment') && (
                          <button className="btn btn-success btn-sm" onClick={() => setPayTarget(b)}>
                            Pay
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Edit Patient Modal */}
      {showEdit && editForm && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowEdit(false)}>
          <div className="modal modal-lg">
            <div className="modal-header">
              <span className="modal-title">Edit Patient — {patient.firstName} {patient.lastName}</span>
              <button className="modal-close" onClick={() => setShowEdit(false)}>×</button>
            </div>
            <form onSubmit={handleSaveEdit}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">First Name *</label>
                    <input className="form-input" value={editForm.firstName} onChange={setF('firstName')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Last Name *</label>
                    <input className="form-input" value={editForm.lastName} onChange={setF('lastName')} required />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Date of Birth *</label>
                    <input type="date" className="form-input" value={editForm.dateOfBirth} onChange={setF('dateOfBirth')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Gender *</label>
                    <select className="form-select" value={editForm.gender} onChange={setF('gender')} required>
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Phone *</label>
                    <input className="form-input" value={editForm.phone} onChange={setF('phone')} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Email</label>
                    <input type="email" className="form-input" value={editForm.email} onChange={setF('email')} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Blood Group</label>
                    <select className="form-select" value={editForm.bloodGroup} onChange={setF('bloodGroup')}>
                      <option value="">Select…</option>
                      {BLOOD_GROUPS.map(b => <option key={b} value={b}>{b}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Emergency Contact</label>
                    <input className="form-input" value={editForm.emergencyContact} onChange={setF('emergencyContact')} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Address</label>
                  <input className="form-input" value={editForm.address} onChange={setF('address')} />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Allergies</label>
                    <input className="form-input" value={editForm.allergies} onChange={setF('allergies')} placeholder="e.g. Penicillin, Peanuts" />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Medical History</label>
                    <input className="form-input" value={editForm.medicalHistory} onChange={setF('medicalHistory')} placeholder="Chronic conditions, surgeries…" />
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowEdit(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={saving}>
                  {saving ? 'Saving…' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Pay Bill Modal */}
      {/* Payment Terminal Modal */}
      {payTarget && (
        <PaymentGatewayModal
          bill={payTarget}
          isPatient={isPatient}
          onClose={() => setPayTarget(null)}
          onSuccess={() => {
            setPayTarget(null);
            load();
          }}
        />
      )}

      {/* Add Vitals Modal */}
      {showVitalsModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowVitalsModal(false)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Record Vitals</span>
              <button className="modal-close" onClick={() => setShowVitalsModal(false)}>×</button>
            </div>
            <form onSubmit={handleSaveVitals}>
              <div className="modal-body">
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Blood Pressure</label>
                    <input className="form-input" placeholder="e.g. 120/80" value={vitalsForm.bloodPressure || ''} onChange={e => setVitalsForm(f => ({ ...f, bloodPressure: e.target.value }))} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Heart Rate (bpm)</label>
                    <input type="number" className="form-input" value={vitalsForm.heartRate || ''} onChange={e => setVitalsForm(f => ({ ...f, heartRate: parseInt(e.target.value) }))} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Temperature (°C)</label>
                    <input type="number" step="0.1" className="form-input" value={vitalsForm.temperature || ''} onChange={e => setVitalsForm(f => ({ ...f, temperature: parseFloat(e.target.value) }))} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">SpO2 Level (%)</label>
                    <input type="number" min="50" max="100" placeholder="e.g. 98" className="form-input" value={vitalsForm.oxygenSaturation ?? vitalsForm.spO2 ?? ''} onChange={e => setVitalsForm(f => ({ ...f, oxygenSaturation: parseInt(e.target.value, 10), spO2: parseInt(e.target.value, 10) }))} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Weight (kg)</label>
                    <input type="number" step="0.1" className="form-input" value={vitalsForm.weight || ''} onChange={e => setVitalsForm(f => ({ ...f, weight: parseFloat(e.target.value) }))} />
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowVitalsModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Save Vitals'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Referral Modal */}
      {showReferralModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowReferralModal(false)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Create Referral</span>
              <button className="modal-close" onClick={() => setShowReferralModal(false)}>×</button>
            </div>
            <form onSubmit={handleSaveReferral}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Target Specialty *</label>
                  <select 
                    className="form-select" 
                    required 
                    value={referralForm.targetSpecialty || 'CARDIOLOGY'} 
                    onChange={e => setReferralForm(f => ({ ...f, targetSpecialty: e.target.value }))}
                  >
                    <option value="CARDIOLOGY">Cardiology</option>
                    <option value="NEUROLOGY">Neurology</option>
                    <option value="GENERAL_MEDICINE">General Medicine</option>
                    <option value="PEDIATRICS">Pediatrics</option>
                    <option value="SURGERY">Surgery</option>
                    <option value="ONCOLOGY">Oncology</option>
                    <option value="OTHER">Other Specialty</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Priority</label>
                  <select className="form-select" value={referralForm.priority || 'ROUTINE'} onChange={e => setReferralForm(f => ({ ...f, priority: e.target.value as any }))}>
                    <option value="ROUTINE">Routine</option>
                    <option value="URGENT">Urgent</option>
                    <option value="EMERGENCY">Emergency</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Clinical Summary *</label>
                  <textarea className="form-input" required rows={3} placeholder="Brief clinical summary of the patient's condition…" value={referralForm.clinicalSummary || ''} onChange={e => setReferralForm(f => ({ ...f, clinicalSummary: e.target.value }))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Referral Question</label>
                  <input className="form-input" placeholder="What should the specialist address?" value={referralForm.referralQuestion || ''} onChange={e => setReferralForm(f => ({ ...f, referralQuestion: e.target.value }))} />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowReferralModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Create Referral'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PatientDetails;
