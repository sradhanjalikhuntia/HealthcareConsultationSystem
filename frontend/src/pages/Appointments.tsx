import React, { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { appointmentApi, AppointmentResponse } from '../api/appointments';
import { adminApi, UserResponse } from '../api/admin';
import { patientApi } from '../api/patients';
import { billingApi } from '../api/billing';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse } from '../types/patient';
import { TimeSlotPicker } from '../components/TimeSlotPicker';

const getTodayString = () => new Date().toISOString().split('T')[0];

const emptyForm = {
  patientId: '', doctorId: '', appointmentDate: getTodayString(), startTime: '09:00', endTime: '09:30', reason: '', notes: '',
};

const Appointments: React.FC = () => {
  const { showToast } = useToast();
  const { user } = useAuth();
  const { can, isPatient } = usePermissions();
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [patients, setPatients] = useState<PatientResponse[]>([]);
  const [doctors, setDoctors] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'calendar'>('list');

  // Create modal
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);

  // Cancel modal
  const [cancelTarget, setCancelTarget] = useState<AppointmentResponse | null>(null);
  const [cancelling, setCancelling] = useState(false);

  // Status update (Complete) modal
  const [statusTarget, setStatusTarget] = useState<AppointmentResponse | null>(null);
  const [statusNotes, setStatusNotes] = useState('');
  const [autoBill, setAutoBill] = useState(true);
  const [updatingStatus, setUpdatingStatus] = useState(false);

  // Reschedule modal
  const [rescheduleTarget, setRescheduleTarget] = useState<AppointmentResponse | null>(null);
  const [rescheduleForm, setRescheduleForm] = useState(emptyForm);
  const [rescheduling, setRescheduling] = useState(false);

  const todayDateStr = new Date().toISOString().split('T')[0];

  const fetchAppointments = useCallback(async (isMountedCheck?: () => boolean) => {
    setLoading(true);
    try {
      const res = await appointmentApi.getAll(page, 10, statusFilter || undefined);
      if (!isMountedCheck || isMountedCheck()) {
        setAppointments(res.data?.content || []);
        setTotalPages(res.data?.totalPages || 0);
        setTotalElements(res.data?.totalElements || 0);
      }
    } catch (err: any) {
      if (!isMountedCheck || isMountedCheck()) {
        if (err.response?.status === 403) {
          showToast(err.userMessage || 'Access Denied: You do not have permission to view appointments.', 'error');
        } else if (err.response?.status === 401) {
          showToast('Session expired. Please log in again.', 'error');
        } else {
          showToast(err.response?.data?.message || 'Failed to load appointments', 'error');
        }
      }
    } finally {
      if (!isMountedCheck || isMountedCheck()) {
        setLoading(false);
      }
    }
  }, [page, statusFilter, showToast]);

  useEffect(() => {
    let mounted = true;
    fetchAppointments(() => mounted);
    return () => {
      mounted = false;
    };
  }, [fetchAppointments]);

  useEffect(() => {
    let mounted = true;
    if (user?.role !== 'PATIENT') {
      patientApi.getPatients(0, 100, undefined, 'ACTIVE').then(r => {
        const pl = (r as any)?.data?.content || (r as any)?.content || (Array.isArray(r?.data) ? r.data : []);
        if (mounted) setPatients(Array.isArray(pl) ? pl : []);
      }).catch(() => {});
    }
    adminApi.getDoctors().then(r => {
      if (mounted) setDoctors(r.data || []);
    }).catch(() => {});
    return () => {
      mounted = false;
    };
  }, [user]);

  // Create appointment
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const effectivePatientId = (user?.role === 'PATIENT' && user.linkedPatientId) ? user.linkedPatientId : form.patientId;
    if (!effectivePatientId) {
      showToast('Patient identifier is missing. Please contact clinic staff.', 'error');
      return;
    }

    if (form.appointmentDate < todayDateStr) {
      showToast('Cannot schedule an appointment for a past date. Please choose today or a future date.', 'error');
      return;
    }
    if (form.appointmentDate === todayDateStr) {
      const nowTime = new Date().toTimeString().slice(0, 5);
      if (form.startTime <= nowTime) {
        showToast('Cannot schedule an appointment for a time slot that has already passed.', 'error');
        return;
      }
    }
    setSubmitting(true);
    try {
      await appointmentApi.create({
        ...form,
        patientId: effectivePatientId,
        startTime: form.startTime + ':00',
        endTime: form.endTime + ':00',
      });
      showToast('Appointment scheduled successfully', 'success');
      setShowModal(false);
      setForm(emptyForm);
      fetchAppointments();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to schedule appointment', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // Cancel appointment
  const handleCancel = async () => {
    if (!cancelTarget) return;
    setCancelling(true);
    try {
      await appointmentApi.cancel(cancelTarget.appointmentId);
      showToast('Appointment cancelled', 'success');
      setCancelTarget(null);
      fetchAppointments();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to cancel appointment', 'error');
    } finally {
      setCancelling(false);
    }
  };

  // Mark as completed
  const handleMarkComplete = async () => {
    if (!statusTarget) return;
    setUpdatingStatus(true);
    try {
      await appointmentApi.updateStatus(statusTarget.appointmentId, 'COMPLETED', statusNotes || undefined);
      
      if (autoBill) {
        try {
          await billingApi.generateBillForAppointment({
            patientId: statusTarget.patientId,
            appointmentId: statusTarget.appointmentId,
            consultationFee: 100, // Default fee
            notes: `Auto-generated consultation fee for appointment on ${statusTarget.appointmentDate}`
          });
          showToast('Appointment completed and Bill generated successfully', 'success');
        } catch (e: any) {
          showToast('Appointment completed, but Bill generation failed', 'error');
        }
      } else {
        showToast('Appointment marked as completed', 'success');
      }

      setStatusTarget(null);
      setStatusNotes('');
      fetchAppointments();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to update status', 'error');
    } finally {
      setUpdatingStatus(false);
    }
  };

  // Open reschedule modal
  const openReschedule = (a: AppointmentResponse) => {
    setRescheduleTarget(a);
    setRescheduleForm({
      patientId: a.patientId,
      doctorId: a.doctorId,
      appointmentDate: a.appointmentDate,
      startTime: a.startTime.substring(0, 5),
      endTime: a.endTime.substring(0, 5),
      reason: a.reason,
      notes: a.notes || '',
    });
  };

  // Reschedule appointment
  const handleReschedule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rescheduleTarget) return;

    if (rescheduleForm.appointmentDate < todayDateStr) {
      showToast('Cannot reschedule an appointment to a past date. Please choose today or a future date.', 'error');
      return;
    }
    if (rescheduleForm.appointmentDate === todayDateStr) {
      const nowTime = new Date().toTimeString().slice(0, 5);
      if (rescheduleForm.startTime <= nowTime) {
        showToast('Cannot reschedule an appointment to a time slot that has already passed.', 'error');
        return;
      }
    }
    setRescheduling(true);
    try {
      await appointmentApi.reschedule(rescheduleTarget.appointmentId, {
        ...rescheduleForm,
        startTime: rescheduleForm.startTime + ':00',
        endTime: rescheduleForm.endTime + ':00',
      });
      showToast('Appointment rescheduled successfully', 'success');
      setRescheduleTarget(null);
      fetchAppointments();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to reschedule', 'error');
    } finally {
      setRescheduling(false);
    }
  };

  const set = (k: keyof typeof emptyForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));

  const setR = (k: keyof typeof emptyForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setRescheduleForm(f => ({ ...f, [k]: e.target.value }));

  const statusBadge = (s: string) => {
    const m: Record<string, string> = {
      SCHEDULED: 'badge-info',
      COMPLETED: 'badge-success',
      CANCELLED: 'badge-danger',
    };
    const dotCls = s === 'SCHEDULED' ? 'amber' : s === 'COMPLETED' ? 'green' : 'red';
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
          <h1 className="page-title">{isPatient ? 'My Consultations' : 'Appointment Scheduler'}</h1>
          <p className="page-subtitle">
            {isPatient ? 'Manage upcoming clinical consultations and book new sessions.' : 'Manage outpatient schedules, doctor calendars, and consultation queues.'}
            {totalElements > 0 && ` · ${totalElements} total bookings`}
          </p>
        </div>
        {can('canScheduleAppointment') && (
          <button className="btn btn-primary" onClick={() => {
            setForm({
              ...emptyForm,
              patientId: user?.role === 'PATIENT' && user.linkedPatientId ? user.linkedPatientId : '',
            });
            setShowModal(true);
          }}>
            <span>+</span> Book Appointment
          </button>
        )}
      </div>

      <div className="table-wrapper">
        <div className="toolbar flex justify-between" style={{ alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div className="flex gap-2" style={{ alignItems: 'center' }}>
            <select
              className="form-select"
              style={{ width: 190 }}
              value={statusFilter}
              onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
            >
              <option value="">All Appointments</option>
              <option value="SCHEDULED">Scheduled</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
            <span className="badge badge-secondary" style={{ fontSize: '0.75rem' }}>
              {appointments.length} showing
            </span>
          </div>
          <div className="flex gap-1" style={{ background: 'var(--surface-2)', padding: '0.3rem', borderRadius: 'var(--radius)', border: '1px solid var(--border)' }}>
            <button 
              className={`btn btn-sm ${viewMode === 'list' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ border: 'none', background: viewMode === 'list' ? 'var(--primary)' : 'transparent', color: viewMode === 'list' ? '#fff' : 'var(--text-2)' }}
              onClick={() => setViewMode('list')}
            >
              ☰ Table View
            </button>
            <button 
              className={`btn btn-sm ${viewMode === 'calendar' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ border: 'none', background: viewMode === 'calendar' ? 'var(--primary)' : 'transparent', color: viewMode === 'calendar' ? '#fff' : 'var(--text-2)' }}
              onClick={() => setViewMode('calendar')}
            >
              📅 Calendar
            </button>
          </div>
        </div>

        {viewMode === 'calendar' ? (
          <div className="calendar-grid mt-3">
            {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(d => (
              <div key={d} className="calendar-header">{d}</div>
            ))}
            {/* Generate mock days for the calendar view (in a real app, this would use a library like date-fns) */}
            {Array.from({ length: 35 }).map((_, i) => {
              const dateNum = (i % 31) + 1;
              const isToday = i === 15;
              const dayAppointments = appointments.filter(a => {
                // Mock matching for demonstration
                const apptDay = parseInt(a.appointmentDate.split('-')[2]);
                return apptDay === dateNum;
              });
              
              return (
                <div key={i} className={`calendar-day ${i < 3 || i > 33 ? 'outside-month' : ''} ${isToday ? 'today' : ''}`}>
                  <div className="calendar-day-date">{dateNum}</div>
                  {dayAppointments.slice(0, 3).map(a => (
                    <div 
                      key={a.appointmentId} 
                      className={`calendar-event ${a.status.toLowerCase()}`}
                      title={`${a.patientName} at ${a.startTime}`}
                      onClick={() => a.status === 'SCHEDULED' ? openReschedule(a) : undefined}
                    >
                      {a.startTime.substring(0,5)} {a.patientName}
                    </div>
                  ))}
                  {dayAppointments.length > 3 && (
                    <div className="text-xs text-center text-muted cursor-pointer" onClick={() => setViewMode('list')}>
                      +{dayAppointments.length - 3} more
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <>
            <table>
          <thead>
            <tr>
              <th>Date &amp; Time</th>
              <th>Patient</th>
              <th>Doctor</th>
              <th>Reason</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              [1, 2, 3].map(i => (
                <tr key={i}><td colSpan={6}><div className="skeleton skeleton-row" /></td></tr>
              ))
            ) : appointments.length === 0 ? (
              <tr><td colSpan={6}>
                <div className="empty-state">
                  <div className="empty-state-icon">📅</div>
                  <div className="empty-state-title">No appointments found</div>
                  <div className="empty-state-desc">Click "New Appointment" to schedule one.</div>
                </div>
              </td></tr>
            ) : appointments.map(a => (
              <tr key={a.appointmentId}>
                <td>
                  <div style={{ fontWeight: 700, color: 'var(--text)' }}>{a.appointmentDate}</div>
                  <div className="text-xs text-muted" style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', marginTop: '0.15rem' }}>
                    <span>🕒</span>
                    <code>{a.startTime ? a.startTime.substring(0, 5) : ''} – {a.endTime ? a.endTime.substring(0, 5) : ''}</code>
                  </div>
                </td>
                <td>
                  {isPatient ? (
                    <span style={{ fontWeight: 700, color: 'var(--text)' }}>{a.patientName}</span>
                  ) : (
                    <Link to={`/patients/${a.patientId}`} style={{ fontWeight: 700, color: 'var(--text)' }}>
                      {a.patientName}
                    </Link>
                  )}
                  <div className="text-xs text-muted" style={{ marginTop: '0.15rem' }}>
                    <code>{a.uniquePatientId || 'MRN-N/A'}</code>
                  </div>
                </td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 600 }}>
                    <span style={{ opacity: 0.8 }}>🩺</span>
                    <span>{a.doctorName?.startsWith('Dr') ? a.doctorName : `Dr. ${a.doctorName}`}</span>
                  </div>
                </td>
                <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  <span style={{ background: 'var(--surface-2)', padding: '0.2rem 0.5rem', borderRadius: 4, fontSize: '0.82rem' }}>
                    {a.reason || 'General Consultation'}
                  </span>
                </td>
                <td>{statusBadge(a.status)}</td>
                <td>
                  <div className="table-actions">
                    {a.status === 'SCHEDULED' && (
                      <>
                        {can('canCompleteAppointment') && (
                          <button
                            className="btn btn-success btn-sm"
                            onClick={() => { setStatusTarget(a); setStatusNotes(''); }}
                            title="Complete Consultation"
                          >
                            ✓ Done
                          </button>
                        )}
                        {can('canRescheduleAppointment') && (
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => openReschedule(a)}
                            title="Reschedule Appointment"
                          >
                            ↕ Move
                          </button>
                        )}
                        {can('canCancelAppointment') && (
                          <button
                            className="btn btn-danger btn-sm"
                            onClick={() => setCancelTarget(a)}
                            title="Cancel Consultation"
                          >
                            ✕
                          </button>
                        )}
                      </>
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
                  <button className="btn btn-secondary btn-sm" onClick={() => setPage(p => p - 1)} disabled={page === 0}>← Prev</button>
                  <button className="btn btn-secondary btn-sm" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next →</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* ── Create Appointment Modal ── */}
      {showModal && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Schedule New Appointment</span>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <form onSubmit={handleSubmit}>
              <div className="modal-body">
                {user?.role === 'PATIENT' ? (
                  <div className="form-group">
                    <label className="form-label">Booking For</label>
                    <input
                      className="form-input"
                      value={`${user.firstName || ''} ${user.lastName || ''} (${user.username})`}
                      disabled
                    />
                  </div>
                ) : (
                  <div className="form-group">
                    <label className="form-label">Patient *</label>
                    <select className="form-select" value={form.patientId} onChange={set('patientId')} required>
                      <option value="">Select a patient…</option>
                      {patients
                        .filter(p => !p.status || p.status === 'ACTIVE')
                        .map(p => {
                        const pid = p.patientId || (p as any).id;
                        return (
                          <option key={pid} value={pid}>
                            {p.firstName} {p.lastName} {p.uniquePatientId ? `(${p.uniquePatientId})` : ''}
                          </option>
                        );
                      })}
                    </select>
                  </div>
                )}
                <div className="form-group">
                  <label className="form-label">Doctor *</label>
                  <select className="form-select" value={form.doctorId} onChange={set('doctorId')} required>
                    <option value="">Select a doctor…</option>
                    {doctors.map(d => (
                      <option key={d.id} value={d.id}>{d.firstName} {d.lastName}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Appointment Date *</label>
                  <input type="date" className="form-input" min={todayDateStr} value={form.appointmentDate} onChange={set('appointmentDate')} required />
                </div>
                {/* Interactive Time-Slot Picker with Conflict Detection */}
                <TimeSlotPicker
                  doctorId={form.doctorId}
                  date={form.appointmentDate}
                  selectedStartTime={form.startTime}
                  selectedEndTime={form.endTime}
                  onSelectSlot={(start, end) => setForm(f => ({ ...f, startTime: start, endTime: end }))}
                />
                <div className="form-group">
                  <label className="form-label">Reason for Visit *</label>
                  <input className="form-input" value={form.reason} onChange={set('reason')} required placeholder="e.g. Annual check-up, Follow-up" />
                </div>
                <div className="form-group">
                  <label className="form-label">Additional Notes</label>
                  <input className="form-input" value={form.notes} onChange={set('notes')} placeholder="Optional notes…" />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Scheduling…' : 'Schedule Appointment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Mark Complete Modal ── */}
      {statusTarget && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setStatusTarget(null)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">Mark as Completed</span>
              <button className="modal-close" onClick={() => setStatusTarget(null)}>×</button>
            </div>
            <div className="modal-body">
              <p className="mb-2">
                Marking appointment for <strong>{statusTarget.patientName}</strong> on{' '}
                {statusTarget.appointmentDate} as completed.
              </p>
              <div className="form-group">
                <label className="form-label">Completion Notes (optional)</label>
                <input
                  className="form-input"
                  value={statusNotes}
                  onChange={e => setStatusNotes(e.target.value)}
                  placeholder="e.g. Treatment administered, follow-up required…"
                />
              </div>
              <div className="form-group mt-2" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <input 
                  type="checkbox" 
                  id="autoBill" 
                  checked={autoBill} 
                  onChange={e => setAutoBill(e.target.checked)}
                  style={{ width: 'auto', margin: 0 }}
                />
                <label htmlFor="autoBill" style={{ margin: 0, fontSize: '0.85rem', cursor: 'pointer' }}>
                  Auto-generate $100 Consultation Bill
                </label>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setStatusTarget(null)}>Cancel</button>
              <button className="btn btn-success" onClick={handleMarkComplete} disabled={updatingStatus}>
                {updatingStatus ? 'Updating…' : '✓ Mark Complete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Reschedule Modal ── */}
      {rescheduleTarget && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setRescheduleTarget(null)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <span className="modal-title">Reschedule Appointment</span>
              <button className="modal-close" onClick={() => setRescheduleTarget(null)}>×</button>
            </div>
            <form onSubmit={handleReschedule}>
              <div className="modal-body">
                <div
                  style={{
                    background: 'rgba(99,102,241,.08)',
                    border: '1px solid rgba(99,102,241,.2)',
                    borderRadius: 'var(--radius)',
                    padding: '.75rem 1rem',
                    marginBottom: '1rem',
                    fontSize: '.875rem',
                  }}
                >
                  Rescheduling: <strong>{rescheduleTarget.patientName}</strong> with{' '}
                  <strong>{rescheduleTarget.doctorName}</strong>
                </div>
                <div className="form-group">
                  <label className="form-label">New Date *</label>
                  <input type="date" className="form-input" min={todayDateStr} value={rescheduleForm.appointmentDate} onChange={setR('appointmentDate')} required />
                </div>
                {/* Interactive Time-Slot Picker with Conflict Detection */}
                <TimeSlotPicker
                  doctorId={rescheduleForm.doctorId}
                  date={rescheduleForm.appointmentDate}
                  selectedStartTime={rescheduleForm.startTime}
                  selectedEndTime={rescheduleForm.endTime}
                  excludeAppointmentId={rescheduleTarget.appointmentId}
                  onSelectSlot={(start, end) => setRescheduleForm(f => ({ ...f, startTime: start, endTime: end }))}
                />
                <div className="form-group">
                  <label className="form-label">Reason *</label>
                  <input className="form-input" value={rescheduleForm.reason} onChange={setR('reason')} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Notes</label>
                  <input className="form-input" value={rescheduleForm.notes} onChange={setR('notes')} placeholder="Reason for rescheduling…" />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setRescheduleTarget(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={rescheduling}>
                  {rescheduling ? 'Rescheduling…' : 'Confirm Reschedule'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Cancel Confirm Modal ── */}
      {cancelTarget && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setCancelTarget(null)}>
          <div className="modal modal-sm">
            <div className="modal-header">
              <span className="modal-title">Cancel Appointment</span>
              <button className="modal-close" onClick={() => setCancelTarget(null)}>×</button>
            </div>
            <div className="modal-body">
              <p>
                Are you sure you want to cancel the appointment for{' '}
                <strong>{cancelTarget.patientName}</strong> on {cancelTarget.appointmentDate} at {cancelTarget.startTime}?
              </p>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setCancelTarget(null)}>Keep</button>
              <button className="btn btn-danger" onClick={handleCancel} disabled={cancelling}>
                {cancelling ? 'Cancelling…' : 'Yes, Cancel'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Appointments;
