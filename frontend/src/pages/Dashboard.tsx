import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import { patientApi } from '../api/patients';
import { appointmentApi } from '../api/appointments';
import { billingApi } from '../api/billing';
import { referralApi } from '../api/referrals';
import { useAuth } from '../context/AuthContext';
import { usePermissions } from '../hooks/usePermissions';

const StatCard = ({
  label,
  value,
  icon,
  trend,
  color,
  bgColor,
}: {
  label: string;
  value: any;
  icon: string;
  trend?: string;
  color?: string;
  bgColor?: string;
}) => (
  <div className="stat-card">
    <div>
      <div className="stat-label">{label}</div>
      <div className="stat-value" style={{ color: color || 'var(--text)' }}>
        {value === undefined || value === null ? '—' : value}
      </div>
      {trend && (
        <div style={{ fontSize: '0.75rem', marginTop: '0.4rem', color: trend.includes('↑') ? 'var(--success)' : 'var(--text-3)', fontWeight: 600 }}>
          {trend}
        </div>
      )}
    </div>
    <div className="stat-icon" style={{ background: bgColor || 'rgba(255, 255, 255, 0.04)' }}>
      {icon}
    </div>
  </div>
);

const Dashboard: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { can, isPatient, isDoctor, isNurse, isReceptionist, isBillingAdmin, isAdmin } = usePermissions();
  const [patientStats, setPatientStats] = useState<any>(null);
  const [apptStats, setApptStats] = useState<any>(null);
  const [billingStats, setBillingStats] = useState<any>(null);
  const [recentPatients, setRecentPatients] = useState<any[]>([]);
  const [recentAppts, setRecentAppts] = useState<any[]>([]);
  const [chartData, setChartData] = useState<any[]>([]);
  const [demoData, setDemoData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [incomingReferrals, setIncomingReferrals] = useState<any[]>([]);

  useEffect(() => {
    let isMounted = true;
    const load = async (isInitial = false) => {
      try {
        if (isInitial) setLoading(true);
        if (isPatient) {
          try {
            const ra = await appointmentApi.getAll(0, 5);
            if (isMounted && ra.data?.content) setRecentAppts(ra.data.content);
          } catch {
            // Patient might not have booked appointments yet
          }
        } else {
          const promises: Promise<any>[] = [];

          if (isAdmin || isDoctor || isNurse || isReceptionist || isBillingAdmin) {
            promises.push(patientApi.getPatientSummary().then(r => {
              if (isMounted) setPatientStats(r.data);
            }).catch(() => {}));
            promises.push(patientApi.getPatients(0, 5).then(r => {
              if (isMounted) setRecentPatients(r.data?.content || []);
            }).catch(() => {}));
          }

          if (isDoctor) {
            promises.push(
              referralApi.getIncoming()
                .then(r => {
                  const raw = (r as any).data;
                  const data = raw?.data !== undefined ? raw.data : raw;
                  if (isMounted) setIncomingReferrals(Array.isArray(data) ? data : []);
                })
                .catch(() => {})
            );
          }

          promises.push(
            appointmentApi.getStats()
              .then(r => {
                if (isMounted) setApptStats(r.data);
              })
              .catch(err => {
                if (err.response?.status === 403) {
                  console.warn('No permission to access appointment stats');
                }
              })
          );
          promises.push(
            appointmentApi.getAll(0, 5)
              .then(r => {
                if (isMounted) setRecentAppts(r.data?.content || []);
              })
              .catch(err => {
                if (err.response?.status === 403) {
                  console.warn('No permission to access appointment list');
                }
              })
          );

          if (isAdmin || isBillingAdmin || isReceptionist) {
            promises.push(
              billingApi.getBillingSummary().then(r => {
                if (isMounted) {
                  setBillingStats(r.data);
                  if (r.data?.revenueTrend && Array.isArray(r.data.revenueTrend)) {
                    setChartData(r.data.revenueTrend.map((t: any) => ({
                      name: t.name,
                      revenue: t.revenue || 0,
                      invoices: t.invoiceCount || 0
                    })));
                  }
                }
              }).catch(() => {})
            );
          }

          await Promise.allSettled(promises);
        }
      } finally {
        if (isMounted && isInitial) {
          setLoading(false);
        }
      }
    };

    load(true);

    // Auto-refresh interval (every 4 seconds) for real-time live data
    const interval = setInterval(() => {
      load(false);
    }, 4000);

    const handleFocus = () => {
      load(false);
    };
    window.addEventListener('focus', handleFocus);

    return () => {
      isMounted = false;
      clearInterval(interval);
      window.removeEventListener('focus', handleFocus);
    };
  }, [isPatient, isAdmin, isDoctor, isNurse, isReceptionist, isBillingAdmin]);

  useEffect(() => {
    if (patientStats?.demographics) {
      const demo = patientStats.demographics;
      setDemoData([
        { name: 'Pediatric (0-18)', value: demo['0-18'] || 0, color: '#38bdf8' },
        { name: 'Adult (19-35)', value: demo['19-35'] || 0, color: '#6366f1' },
        { name: 'Middle Age (36-60)', value: demo['36-60'] || 0, color: '#10b981' },
        { name: 'Senior (60+)', value: demo['60+'] || 0, color: '#f59e0b' },
      ]);
    }
  }, [patientStats]);

  const statusBadge = (s: string) => {
    const map: Record<string, string> = {
      ACTIVE: 'badge-success', SCHEDULED: 'badge-info', COMPLETED: 'badge-success',
      CANCELLED: 'badge-danger', PENDING: 'badge-warning', PAID: 'badge-success',
    };
    return <span className={`badge ${map[s] || 'badge-secondary'}`}>{s}</span>;
  };

  const getGreetingHeader = () => {
    if (isPatient) return `Welcome back, ${user?.firstName} 👋`;
    if (isDoctor) return `Welcome back, Dr. ${user?.firstName} 👋`;
    if (isNurse) return `Welcome back, Nurse ${user?.firstName} 👋`;
    if (isReceptionist) return `Welcome back, ${user?.firstName} 👋 (Front Desk)`;
    if (isBillingAdmin) return `Welcome back, ${user?.firstName} 👋 (Billing & Accounts)`;
    if (isAdmin) return `Welcome back, Administrator ${user?.firstName} 👋`;
    return `Welcome back, ${user?.firstName} 👋`;
  };

  const getGreetingSubtitle = () => {
    if (isPatient) return 'Welcome to your ApexCare Patient Portal. Manage your doctor appointments, view lab results, and settle medical invoices securely.';
    if (isDoctor) return 'Clinical Command Center · Overview of today’s patient appointments, clinical rounds, vitals tracking, and medication orders.';
    if (isNurse) return 'Inpatient Care Command · Monitor room occupancy, bedside vitals logs, and patient admission flows.';
    if (isReceptionist) return 'Front Desk Operations · Instant patient registration, appointment scheduling, queue allocation, and cash collections.';
    if (isBillingAdmin) return 'Institutional Accounts & Finance · Real-time revenue insights, insurance claim processing, and outstanding balances.';
    return 'Institutional Operations Deck · High-level clinical analytics, department metrics, and system activity.';
  };

  if (isPatient) {
    return (
      <div className="fade-in">
        {/* Patient Hero Banner */}
        <div className="hero-banner">
          <div>
            <div className="hero-title">{getGreetingHeader()}</div>
            <p className="hero-subtitle">{getGreetingSubtitle()}</p>
          </div>
          <button className="btn btn-primary btn-lg" onClick={() => navigate('/appointments')}>
            <span>📅</span>
            <span>+ Book New Consultation</span>
          </button>
        </div>

        {/* Patient Digital Health Card & Quick Dial Grid */}
        <div className="grid-2 mb-4">
          {/* Digital Medical ID Card */}
          <div className="card" style={{ background: 'linear-gradient(135deg, rgba(14, 165, 233, 0.12) 0%, rgba(99, 102, 241, 0.15) 100%)', borderColor: 'rgba(99, 102, 241, 0.3)' }}>
            <div className="flex justify-between items-center mb-3">
              <div className="flex items-center gap-2">
                <span style={{ fontSize: '1.4rem' }}>🏥</span>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800 }}>ApexCare Digital Health Card</h3>
                  <span className="text-xs text-muted">Universal Patient Identifier (UPI)</span>
                </div>
              </div>
              <span className="badge badge-success">● Verified Patient</span>
            </div>

            <div className="flex items-center gap-3 mt-3">
              <div className="avatar" style={{ width: 54, height: 54, fontSize: '1.25rem' }}>
                {user?.firstName?.[0] || 'P'}
              </div>
              <div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700 }}>{user?.firstName} {user?.lastName}</div>
                <div className="text-sm text-muted">Patient ID: <code style={{ color: 'var(--accent)', fontWeight: 600 }}>{user?.linkedPatientId ? user.linkedPatientId.slice(0, 8).toUpperCase() : 'APEX-PAT-REG'}</code></div>
                <div className="text-xs text-muted mt-1">Hospital Emergency Support: Dial 108 or 1800-APEX</div>
              </div>
            </div>

            <div className="flex gap-2 mt-4">
              <button className="btn btn-secondary btn-sm" onClick={() => navigate('/billing')}>
                <span>💳</span>
                <span>My Invoices</span>
              </button>
              <button className="btn btn-primary btn-sm" onClick={() => navigate('/appointments')}>
                <span>📅</span>
                <span>Consultations</span>
              </button>
            </div>
          </div>

          {/* Emergency 24x7 Ambulance & Helpline Box */}
          <div className="card" style={{ borderColor: 'rgba(244, 63, 94, 0.35)', background: 'linear-gradient(135deg, rgba(244, 63, 94, 0.08) 0%, rgba(15, 23, 42, 0.9) 100%)' }}>
            <div className="flex items-center gap-2 mb-2">
              <span style={{ fontSize: '1.5rem' }}>🚑</span>
              <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: 'var(--danger)' }}>Emergency Response 24x7</h3>
            </div>
            <p className="text-sm text-muted mb-3">
              Instant access to emergency trauma care, critical dispatch, and hospital admissions.
            </p>

            <div className="grid-2 gap-2">
              <div style={{ padding: '0.85rem', background: 'rgba(244, 63, 94, 0.12)', border: '1px solid rgba(244, 63, 94, 0.25)', borderRadius: 'var(--radius)' }}>
                <div style={{ fontSize: '0.75rem', color: '#fca5a5', fontWeight: 700, textTransform: 'uppercase' }}>Ambulance Dispatch</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#ffffff', marginTop: '0.2rem' }}>108</div>
                <div className="text-xs text-muted">Toll-Free Nationwide</div>
              </div>

              <div style={{ padding: '0.85rem', background: 'rgba(99, 102, 241, 0.12)', border: '1px solid rgba(99, 102, 241, 0.25)', borderRadius: 'var(--radius)' }}>
                <div style={{ fontSize: '0.75rem', color: '#a5b4fc', fontWeight: 700, textTransform: 'uppercase' }}>Hospital Helpline</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 800, color: '#ffffff', marginTop: '0.2rem' }}>1800-APEX</div>
                <div className="text-xs text-muted">24/7 Patient Guidance</div>
              </div>
            </div>
          </div>
        </div>

        {/* My Appointments Card */}
        <div className="card mb-4">
          <div className="card-title">
            <span>My Upcoming Consultations</span>
            <Link to="/appointments" className="btn btn-secondary btn-sm">View Calendar & Book →</Link>
          </div>

          {recentAppts.length === 0 && !loading && (
            <div className="empty-state" style={{ padding: '3rem 1rem' }}>
              <div style={{ fontSize: '3rem', marginBottom: '0.75rem' }}>📅</div>
              <h4 style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: '0.35rem' }}>No Appointments Scheduled</h4>
              <p className="text-muted text-sm" style={{ maxWidth: 440, margin: '0 auto 1.25rem auto' }}>
                You currently have no scheduled appointments. Book a consultation with one of our specialized doctors today.
              </p>
              <button className="btn btn-primary" onClick={() => navigate('/appointments')}>Book Consultation Now</button>
            </div>
          )}

          {recentAppts.length > 0 && (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Doctor Consultation</th>
                    <th>Date & Slot</th>
                    <th>Department / Reason</th>
                    <th className="text-right">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {recentAppts.map(a => (
                    <tr key={a.appointmentId}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{a.doctorName || 'Senior Consultant'}</div>
                        <div className="text-xs text-muted">Appointment #{a.appointmentId?.slice(0, 8)}</div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{a.appointmentDate}</div>
                        <div className="text-xs text-muted">{a.startTime}</div>
                      </td>
                      <td>{a.reason || 'General Routine Consultation'}</td>
                      <td className="text-right">{statusBadge(a.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="fade-in">
      {/* Staff Hero Banner */}
      <div className="hero-banner">
        <div>
          <div className="hero-title">{getGreetingHeader()}</div>
          <p className="hero-subtitle">{getGreetingSubtitle()}</p>
        </div>

        <div className="flex gap-2">
          {can('canRegisterPatient') && (
            <button className="btn btn-primary" onClick={() => navigate('/patients')}>
              <span>+</span>
              <span>Register Patient</span>
            </button>
          )}
          {can('canScheduleAppointment') && (
            <button className="btn btn-secondary" onClick={() => navigate('/appointments')}>
              <span>📅</span>
              <span>Schedule</span>
            </button>
          )}
        </div>
      </div>

      {/* Role-tailored Stat Grid */}
      <div className="grid-4 mb-4">
        {(isAdmin || isDoctor || isNurse || isReceptionist || isBillingAdmin) && (
          <>
            <StatCard
              label="Total Patients"
              value={patientStats?.totalCount ?? patientStats?.totalPatients}
              icon="🏥"
              trend="↑ Active EMR Records"
              color="var(--accent)"
              bgColor="rgba(56, 189, 248, 0.1)"
            />
            <StatCard
              label="Active Inpatients"
              value={patientStats?.activeCount ?? patientStats?.inpatientCount ?? patientStats?.activeInpatients}
              icon="💚"
              trend="Current Admitted"
              color="var(--success)"
              bgColor="rgba(16, 185, 129, 0.1)"
            />
          </>
        )}
        <StatCard
          label="Total Consultations"
          value={apptStats?.totalCount ?? apptStats?.totalAppointments}
          icon="📅"
          trend="Hospital Wide"
          color="var(--primary)"
          bgColor="rgba(99, 102, 241, 0.1)"
        />
        <StatCard
          label="Scheduled Today"
          value={apptStats?.scheduledCount ?? apptStats?.todayAppointments}
          icon="🗓️"
          trend="Pending Queue"
          color="var(--warning)"
          bgColor="rgba(245, 158, 11, 0.1)"
        />
        <StatCard
          label="Completed Visits"
          value={apptStats?.completedCount ?? apptStats?.completedConsultations}
          icon="✅"
          trend="Successful Visits"
          color="var(--success)"
          bgColor="rgba(16, 185, 129, 0.1)"
        />
        {(isAdmin || isBillingAdmin || isReceptionist) && (
          <>
            <StatCard
              label="Total Revenue"
              value={billingStats ? `$${Number(billingStats.totalRevenue || 0).toLocaleString()}` : '$0'}
              icon="💰"
              trend="↑ 14.2% this month"
              color="var(--success)"
              bgColor="rgba(16, 185, 129, 0.1)"
            />
            <StatCard
              label="Pending Invoices"
              value={billingStats?.pendingInvoices ?? billingStats?.pendingBills ?? 0}
              icon="⏳"
              trend="Awaiting Payment"
              color="var(--warning)"
              bgColor="rgba(245, 158, 11, 0.1)"
            />
            <StatCard
              label="Cleared Bills"
              value={billingStats?.paidInvoices ?? billingStats?.paidBills ?? 0}
              icon="💳"
              trend="Settled Transactions"
              color="var(--accent)"
              bgColor="rgba(56, 189, 248, 0.1)"
            />
          </>
        )}
      </div>

      {/* Doctor-Specific Patient & Clinical Panel */}
      {isDoctor && (
        <>
          <div className="grid-2 mb-4">
            {/* Today's Appointments Queue */}
            <div className="card">
              <div className="card-title">
                <span>🩺 My Patients Queue Today</span>
                <Link to="/appointments" className="btn btn-secondary btn-sm">Full Schedule →</Link>
              </div>
              {(() => {
                const today = new Date().toISOString().slice(0, 10);
                const todayAppts = recentAppts.filter(a => a.appointmentDate === today);
                if (todayAppts.length === 0) {
                  return (
                    <div className="empty-state" style={{ padding: '1.5rem 1rem' }}>
                      <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🗓️</div>
                      <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>No appointments scheduled for today</div>
                      <div className="text-sm text-muted">Your patient queue is clear for today.</div>
                    </div>
                  );
                }
                return (
                  <div className="table-wrapper">
                    <table>
                      <thead>
                        <tr>
                          <th>Patient</th>
                          <th>Time</th>
                          <th>Reason</th>
                          <th>Status</th>
                          <th className="text-right">Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {todayAppts.map(a => (
                          <tr key={a.appointmentId}>
                            <td>
                              <div style={{ fontWeight: 600 }}>{a.patientName}</div>
                              <div className="text-xs text-muted">#{a.appointmentId?.slice(0, 8)}</div>
                            </td>
                            <td>
                              <div style={{ fontWeight: 500 }}>{a.startTime}</div>
                              <div className="text-xs text-muted">{a.endTime}</div>
                            </td>
                            <td className="text-sm">{a.reason || 'Consultation'}</td>
                            <td>{statusBadge(a.status)}</td>
                            <td className="text-right">
                              <Link to={`/patients/${a.patientId}`} className="btn btn-primary btn-sm">
                                View Patient →
                              </Link>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                );
              })()}
            </div>

            {/* Incoming Referrals for Doctor */}
            <div className="card">
              <div className="card-title">
                <span>📨 Incoming Patient Referrals</span>
                <span className="badge badge-info">{incomingReferrals.filter(r => r.status === 'PENDING').length} Pending</span>
              </div>
              {incomingReferrals.length === 0 ? (
                <div className="empty-state" style={{ padding: '1.5rem 1rem' }}>
                  <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>📭</div>
                  <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>No incoming referrals</div>
                  <div className="text-sm text-muted">No specialists have referred patients to you yet.</div>
                </div>
              ) : (
                <div className="table-wrapper">
                  <table>
                    <thead>
                      <tr>
                        <th>Patient</th>
                        <th>Specialty</th>
                        <th>Priority</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {incomingReferrals.slice(0, 5).map((r: any) => (
                        <tr key={r.id}>
                          <td>
                            <div style={{ fontWeight: 600 }}>{r.patientName || 'Patient'}</div>
                            <div className="text-xs text-muted">From: Dr. {r.referringDoctorName || 'Referring Doctor'}</div>
                          </td>
                          <td className="text-sm">{r.targetSpecialty}</td>
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
                </div>
              )}
            </div>
          </div>
        </>
      )}

      {/* Analytics Charts (for Admin & Billing) */}
      {(isAdmin || isBillingAdmin) && (
        <div className="grid-2 mb-4">
          <div className="card" style={{ height: 380 }}>
            <div className="card-title">
              <span>Revenue & Consultation Growth Trend</span>
              <span className="badge badge-success">Live Sync</span>
            </div>
            <div style={{ height: 'calc(100% - 40px)', paddingBottom: '1rem' }}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorRev" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10b981" stopOpacity={0.35}/>
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                    </linearGradient>
                    <linearGradient id="colorAppt" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6366f1" stopOpacity={0.35}/>
                      <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
                  <XAxis dataKey="name" stroke="var(--text-3)" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis yAxisId="left" stroke="var(--text-3)" fontSize={12} tickLine={false} axisLine={false} tickFormatter={v => `$${v}`} />
                  <YAxis yAxisId="right" orientation="right" stroke="var(--text-3)" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip 
                    contentStyle={{ backgroundColor: 'var(--surface-solid)', borderColor: 'var(--border-light)', borderRadius: '12px', color: 'var(--text)', boxShadow: '0 10px 25px rgba(0,0,0,0.5)' }}
                    itemStyle={{ color: 'var(--text)' }}
                  />
                  <Area yAxisId="left" type="monotone" dataKey="revenue" stroke="#10b981" strokeWidth={2.5} fillOpacity={1} fill="url(#colorRev)" />
                  <Area yAxisId="right" type="monotone" dataKey="invoices" stroke="#6366f1" strokeWidth={2.5} fillOpacity={1} fill="url(#colorAppt)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="card" style={{ height: 380 }}>
            <div className="card-title">
              <span>Patient Age Demographics</span>
              <span className="badge badge-info">Distribution</span>
            </div>
            {demoData.length === 0 || demoData.every(d => d.value === 0) ? (
              <div className="empty-state" style={{ height: 'calc(100% - 70px)', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center' }}>
                <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>👥</div>
                <div style={{ fontWeight: 600 }}>No demographic data available</div>
                <div className="text-sm text-muted">Patient age profiles will appear here once recorded.</div>
              </div>
            ) : (
              <>
                <div style={{ height: 'calc(100% - 70px)' }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={demoData} cx="50%" cy="50%" innerRadius={65} outerRadius={105} paddingAngle={5} dataKey="value">
                        {demoData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip 
                        contentStyle={{ backgroundColor: 'var(--surface-solid)', borderColor: 'var(--border-light)', borderRadius: '12px', color: 'var(--text)' }}
                        itemStyle={{ color: 'var(--text)' }}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex justify-center gap-4 mt-1 flex-wrap">
                  {demoData.map(d => (
                    <div key={d.name} className="flex items-center gap-1 text-xs">
                      <div style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: d.color }} />
                      <span className="text-muted">{d.name} ({d.value})</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Recent Activity Section */}
      <div className="grid-2 mb-4">
        {/* Recent Patients */}
        {(isAdmin || isDoctor || isNurse || isReceptionist || isBillingAdmin) && (
          <div className="card">
            <div className="card-title">
              <span>Recent Patient Registrations</span>
              <Link to="/patients" className="btn btn-secondary btn-sm">All Records →</Link>
            </div>
            {recentPatients.length === 0 && !loading && (
              <div className="empty-state"><div className="empty-state-desc">No patients registered yet.</div></div>
            )}
            <div className="table-wrapper">
              <table>
                <tbody>
                  {recentPatients.map(p => (
                    <tr key={p.patientId}>
                      <td style={{ width: 50 }}>
                        <div className="avatar" style={{ width: 34, height: 34, fontSize: '0.8rem' }}>
                          {p.firstName?.[0] || 'P'}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{p.firstName} {p.lastName}</div>
                        <div className="text-xs text-muted">{p.uniquePatientId} · {p.phone}</div>
                      </td>
                      <td className="text-right">{statusBadge(p.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Recent Appointments */}
        <div className="card">
          <div className="card-title">
            <span>Recent Appointments</span>
            <Link to="/appointments" className="btn btn-secondary btn-sm">Full Schedule →</Link>
          </div>
          {recentAppts.length === 0 && !loading && (
            <div className="empty-state"><div className="empty-state-desc">No appointments found.</div></div>
          )}
          <div className="table-wrapper">
            <table>
              <tbody>
                {recentAppts.map(a => (
                  <tr key={a.appointmentId}>
                    <td style={{ width: 50 }}>
                      <div className="avatar" style={{ width: 34, height: 34, fontSize: '0.8rem', background: 'linear-gradient(135deg, #10b981, #06b6d4)' }}>
                        📅
                      </div>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{a.patientName}</div>
                      <div className="text-xs text-muted">Dr. {a.doctorName} · {a.appointmentDate} ({a.startTime})</div>
                    </td>
                    <td className="text-right">{statusBadge(a.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Quick Launchpad Deck */}
      <div className="card">
        <div className="card-title">
          <span>Clinical Operations Launchpad</span>
          <span className="text-xs text-muted">Shortcuts & Reports</span>
        </div>
        <div className="flex gap-2 flex-wrap">
          {can('canRegisterPatient') && (
            <button className="btn btn-primary" onClick={() => navigate('/patients')}>
              <span>+</span>
              <span>Register Patient</span>
            </button>
          )}
          {can('canScheduleAppointment') && (
            <button className="btn btn-secondary" onClick={() => navigate('/appointments')}>
              <span>📅</span>
              <span>Schedule Consultation</span>
            </button>
          )}
          {can('canCreateInvoice') && (
            <button className="btn btn-secondary" onClick={() => navigate('/billing')}>
              <span>💳</span>
              <span>Generate Invoice</span>
            </button>
          )}
          {can('canViewWards') && (
            <button className="btn btn-secondary" onClick={() => navigate('/wards')}>
              <span>🛏️</span>
              <span>Wards & Bed Allocation</span>
            </button>
          )}
          {can('canViewPharmacy') && (
            <button className="btn btn-secondary" onClick={() => navigate('/pharmacy')}>
              <span>💊</span>
              <span>Pharmacy Inventory</span>
            </button>
          )}
          {can('canManageUsers') && (
            <button className="btn btn-secondary" onClick={() => navigate('/admin')}>
              <span>⚙️</span>
              <span>User Administration</span>
            </button>
          )}
          <button className="btn btn-secondary" onClick={() => window.print()}>
            <span>🖨️</span>
            <span>Print Executive Summary</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
