import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { wardApi, Bed, WardSummary } from '../api/wards';
import { patientApi } from '../api/patients';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import { usePermissions } from '../hooks/usePermissions';
import type { PatientResponse } from '../types/patient';

interface WardMeta {
  label: string;
  icon: string;
  desc: string;
  color: string;
  defaultRate: number;
}

const WARD_METADATA: Record<string, WardMeta> = {
  GENERAL: { label: 'General Inpatient', icon: '🏥', desc: 'Standard medical-surgical care', color: '#38bdf8', defaultRate: 1500 },
  ICU: { label: 'Intensive Care Unit (ICU)', icon: '🚨', desc: 'Critical telemetry & life support', color: '#f43f5e', defaultRate: 5000 },
  EMERGENCY: { label: 'Emergency Ward', icon: '🚑', desc: 'Acute triage & trauma monitoring', color: '#fb923c', defaultRate: 3000 },
  MATERNITY: { label: 'Maternity Care', icon: '👶', desc: 'Labor, delivery & post-natal', color: '#ec4899', defaultRate: 2500 },
  PEDIATRIC: { label: 'Pediatric Care', icon: '🧸', desc: 'Specialized infant & child care', color: '#a855f7', defaultRate: 2000 },
  ISOLATION: { label: 'Isolation Unit', icon: '🛡️', desc: 'Bio-containment negative pressure', color: '#eab308', defaultRate: 3500 },
};

const Wards: React.FC = () => {
  const { showToast } = useToast();
  const { user } = useAuth();
  const { can } = usePermissions();

  // Core Data
  const [beds, setBeds] = useState<Bed[]>([]);
  const [summary, setSummary] = useState<WardSummary | null>(null);
  const [patients, setPatients] = useState<PatientResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  // Filtering & View
  const [selectedWard, setSelectedWard] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'table' | 'grid'>('table');

  // Modals
  const [admitBed, setAdmitBed] = useState<Bed | null>(null);
  const [dischargeBed, setDischargeBed] = useState<Bed | null>(null);
  const [selectedPatientId, setSelectedPatientId] = useState('');
  const [admissionReason, setAdmissionReason] = useState('');
  const [dischargeNotes, setDischargeNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Fetch live telemetry from backend
  const loadData = useCallback(async (isBackground = false) => {
    if (!isBackground) setLoading(true);
    else setIsRefreshing(true);

    try {
      // Fetch summary and bed list in parallel
      const [bedsRes, summaryRes] = await Promise.all([
        wardApi.getBeds(),
        wardApi.getSummary().catch(() => null)
      ]);

      const payload = (bedsRes as any)?.data?.data !== undefined ? (bedsRes as any).data.data : (bedsRes as any)?.data;
      const list: Bed[] = payload?.content || (Array.isArray(payload) ? payload : []);
      setBeds(Array.isArray(list) ? list : []);

      const summaryPayload = (summaryRes as any)?.data?.data !== undefined ? (summaryRes as any).data.data : (summaryRes as any)?.data;
      if (summaryPayload && typeof summaryPayload === 'object') {
        setSummary(summaryPayload);
      }
      setLastUpdated(new Date());
    } catch {
      if (!isBackground) {
        setBeds([]);
        showToast('Failed to load ward data', 'error');
      }
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, [showToast]);

  // Initial load and periodic 8-second real-time telemetry polling
  useEffect(() => {
    if (user && user.role !== 'PATIENT') {
      loadData(false);

      // Fetch patients for admission modal
      patientApi.getPatients(0, 100, undefined, 'ACTIVE').then(res => {
        const payload = (res as any)?.data?.content || (res as any)?.content || [];
        setPatients(Array.isArray(payload) ? payload : []);
      }).catch(() => {});

      // 8-second real-time polling
      const pollTimer = setInterval(() => {
        loadData(true);
      }, 8000);

      return () => clearInterval(pollTimer);
    }
  }, [user, loadData]);

  // Computed Census Counters (derived directly from real-time bed collection)
  const totalBeds = beds.length || summary?.totalBeds || 0;
  const occupiedBeds = beds.filter(b => b.status === 'OCCUPIED').length;
  const availableBeds = beds.filter(b => b.status === 'AVAILABLE').length;
  const cleaningBeds = beds.filter(b => b.status === 'CLEANING' || b.status === 'MAINTENANCE').length;

  const occupancyRate = totalBeds > 0 ? ((occupiedBeds / totalBeds) * 100).toFixed(1) : '0.0';
  const availableRate = totalBeds > 0 ? ((availableBeds / totalBeds) * 100).toFixed(1) : '0.0';

  // Ward breakdown calculation
  const wardBreakdown = useMemo(() => {
    const map: Record<string, { total: number; occupied: number; available: number; cleaning: number }> = {};
    Object.keys(WARD_METADATA).forEach(w => {
      map[w] = { total: 0, occupied: 0, available: 0, cleaning: 0 };
    });

    beds.forEach(bed => {
      const type = (bed.wardType || 'GENERAL').toUpperCase();
      if (!map[type]) {
        map[type] = { total: 0, occupied: 0, available: 0, cleaning: 0 };
      }
      const bStatus = (bed.status || '').toUpperCase();
      map[type].total += 1;
      if (bStatus === 'OCCUPIED') map[type].occupied += 1;
      else if (bStatus === 'AVAILABLE') map[type].available += 1;
      else if (bStatus === 'CLEANING' || bStatus === 'MAINTENANCE') map[type].cleaning += 1;
    });

    return map;
  }, [beds]);

  // Filtered beds
  const filteredBeds = useMemo(() => {
    return beds.filter(bed => {
      const bWard = (bed.wardType || '').toUpperCase();
      const bStatus = (bed.status || '').toUpperCase();

      // Ward filter
      if (selectedWard !== 'ALL' && bWard !== selectedWard.toUpperCase()) return false;

      // Status filter
      if (selectedStatus !== 'ALL') {
        if (selectedStatus === 'CLEANING') {
          if (bStatus !== 'CLEANING' && bStatus !== 'MAINTENANCE') return false;
        } else if (bStatus !== selectedStatus.toUpperCase()) {
          return false;
        }
      }

      // Search query (bed number, ward type, or patient name)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const bedNum = (bed.bedNumber || '').toLowerCase();
        const wardName = (bed.wardType || '').toLowerCase();
        const patientName = (bed.currentPatientName || '').toLowerCase();
        return bedNum.includes(q) || wardName.includes(q) || patientName.includes(q);
      }

      return true;
    });
  }, [beds, selectedWard, selectedStatus, searchQuery]);

  // Actions
  const openAdmitModal = (bed: Bed) => {
    setAdmitBed(bed);
    if (patients.length === 0) {
      patientApi.getPatients(0, 100, undefined, 'ACTIVE').then(res => {
        const payload = (res as any)?.data?.content || (res as any)?.content || [];
        setPatients(Array.isArray(payload) ? payload : []);
      }).catch(() => {});
    }
  };

  const handleAdmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!admitBed || !selectedPatientId) return;
    setSubmitting(true);
    try {
      await wardApi.admitPatient(admitBed.id, {
        patientId: selectedPatientId,
        reason: admissionReason || 'Admitted to ward bed'
      });
      showToast(`Patient successfully admitted to Bed ${admitBed.bedNumber}`, 'success');
      setAdmitBed(null);
      setSelectedPatientId('');
      setAdmissionReason('');
      await loadData(false);
    } catch (err: any) {
      showToast(err.response?.data?.message || err.message || 'Failed to admit patient', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDischarge = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!dischargeBed) return;
    setSubmitting(true);
    try {
      const res = await wardApi.dischargePatient(dischargeBed.id, {
        dischargeSummary: dischargeNotes || 'Discharge completed. Terminal bed cleaning scheduled.'
      });
      const data = (res as any)?.data?.data || (res as any)?.data || res;
      const charge = Number(data?.totalCharge);
      const chargeText = !isNaN(charge) && charge > 0 ? ` | $${charge.toLocaleString()} auto-added to patient bill` : '';
      showToast(`Patient discharged from Bed ${dischargeBed.bedNumber}${chargeText}. Bed moved to Cleaning.`, 'success');
      setDischargeBed(null);
      setDischargeNotes('');
      await loadData(false);
    } catch (err: any) {
      showToast(err.response?.data?.message || err.message || 'Failed to discharge patient', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReady = async (bed: Bed) => {
    try {
      await wardApi.markReady(bed.id);
      showToast(`Bed ${bed.bedNumber} sanitized and marked ready for intake`, 'success');
      await loadData(false);
    } catch (err: any) {
      showToast(err.response?.data?.message || err.message || 'Failed to mark bed ready', 'error');
    }
  };

  // Status Badge Helper
  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'AVAILABLE':
        return (
          <span className="badge badge-success">
            <span style={{ display: 'inline-block', width: 7, height: 7, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 6px #10b981' }} />
            AVAILABLE (LEFT)
          </span>
        );
      case 'OCCUPIED':
        return (
          <span className="badge badge-danger">
            <span style={{ display: 'inline-block', width: 7, height: 7, borderRadius: '50%', background: '#f43f5e', boxShadow: '0 0 6px #f43f5e' }} />
            FULL (OCCUPIED)
          </span>
        );
      case 'CLEANING':
      case 'MAINTENANCE':
        return (
          <span className="badge badge-warning">
            <span style={{ display: 'inline-block', width: 7, height: 7, borderRadius: '50%', background: '#f59e0b', boxShadow: '0 0 6px #f59e0b' }} />
            CLEANING / SANITIZING
          </span>
        );
      default:
        return (
          <span className="badge badge-secondary">
            {status}
          </span>
        );
    }
  };

  return (
    <div className="fade-in">
      {/* Page Header with Real-Time Telemetry Indicator */}
      <div className="page-header" style={{ marginBottom: '1.5rem' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.35rem' }}>
            <h1 className="page-title" style={{ margin: 0 }}>Inpatient Wards &amp; Real-Time Bed Census</h1>
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.45rem',
                padding: '0.25rem 0.75rem',
                borderRadius: '9999px',
                fontSize: '0.75rem',
                fontWeight: 700,
                background: 'rgba(16, 185, 129, 0.15)',
                color: '#10b981',
                border: '1px solid rgba(16, 185, 129, 0.3)',
                boxShadow: '0 0 12px rgba(16, 185, 129, 0.2)'
              }}
            >
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: '#10b981',
                  animation: 'pulse 1.8s infinite'
                }}
              />
              REAL-TIME CENSUS ACTIVE
            </div>
          </div>
          <p className="page-subtitle" style={{ margin: 0 }}>
            Live room occupancy, ward capacity telemetry, and immediate patient bed allocation.
          </p>
        </div>

        {/* Sync Status & Action */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
          <div style={{ textAlign: 'right', fontSize: '0.8rem', color: 'var(--text-3)' }}>
            <div>Last Telemetry Sync:</div>
            <strong style={{ color: 'var(--text-2)' }}>{lastUpdated.toLocaleTimeString()}</strong>
          </div>
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => loadData(false)}
            disabled={isRefreshing || loading}
            title="Refresh bed census telemetry immediately"
            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}
          >
            <span style={{ display: 'inline-block', transform: isRefreshing ? 'rotate(360deg)' : 'none', transition: 'transform 0.5s linear' }}>
              🔄
            </span>
            {isRefreshing ? 'Syncing...' : 'Sync Now'}
          </button>
        </div>
      </div>

      {/* TOP REAL-TIME STATS: TOTAL, FULL, LEFT, CLEANING */}
      <div
        className="stat-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1.25rem',
          marginBottom: '2rem'
        }}
      >
        {/* TOTAL BEDS */}
        <div
          className="stat-card"
          style={{
            background: 'var(--surface)',
            border: '1px solid rgba(99, 102, 241, 0.25)',
            boxShadow: '0 8px 24px rgba(99, 102, 241, 0.1)',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <div style={{ flex: 1 }}>
            <div className="stat-label">TOTAL HOSPITAL BEDS</div>
            <div className="stat-value" style={{ color: 'var(--text)' }}>
              {totalBeds} <span style={{ fontSize: '1rem', fontWeight: 500, color: 'var(--text-3)' }}>Beds</span>
            </div>
            <div style={{ marginTop: '0.65rem', fontSize: '0.78rem', color: 'var(--text-2)' }}>
              Across 6 specialized clinical wards
            </div>
          </div>
          <div className="stat-icon" style={{ background: 'rgba(99, 102, 241, 0.15)', borderColor: 'rgba(99, 102, 241, 0.3)' }}>
            🛏️
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              height: 4,
              background: 'var(--primary-gradient)'
            }}
          />
        </div>

        {/* FULL / OCCUPIED BEDS */}
        <div
          className="stat-card"
          style={{
            background: 'var(--surface)',
            border: '1px solid rgba(244, 63, 94, 0.3)',
            boxShadow: '0 8px 24px rgba(244, 63, 94, 0.1)',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <div style={{ flex: 1 }}>
            <div className="stat-label" style={{ color: '#fb7185' }}>CURRENTLY FULL (OCCUPIED)</div>
            <div className="stat-value" style={{ color: '#f43f5e' }}>
              {occupiedBeds} <span style={{ fontSize: '1rem', fontWeight: 500, color: '#fb7185' }}>Full</span>
            </div>
            <div style={{ marginTop: '0.65rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <div style={{ flex: 1, height: 6, background: 'rgba(244, 63, 94, 0.15)', borderRadius: 4, overflow: 'hidden' }}>
                <div style={{ width: `${occupancyRate}%`, height: '100%', background: '#f43f5e', borderRadius: 4 }} />
              </div>
              <span style={{ fontSize: '0.78rem', fontWeight: 700, color: '#f43f5e' }}>
                {occupancyRate}%
              </span>
            </div>
          </div>
          <div className="stat-icon" style={{ background: 'rgba(244, 63, 94, 0.15)', borderColor: 'rgba(244, 63, 94, 0.3)' }}>
            🔴
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              height: 4,
              background: 'linear-gradient(90deg, #f43f5e, #fb7185)'
            }}
          />
        </div>

        {/* LEFT / AVAILABLE BEDS */}
        <div
          className="stat-card"
          style={{
            background: 'var(--surface)',
            border: '1px solid rgba(16, 185, 129, 0.3)',
            boxShadow: '0 8px 24px rgba(16, 185, 129, 0.1)',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <div style={{ flex: 1 }}>
            <div className="stat-label" style={{ color: '#34d399' }}>VACANT BEDS LEFT (AVAILABLE)</div>
            <div className="stat-value" style={{ color: '#10b981' }}>
              {availableBeds} <span style={{ fontSize: '1rem', fontWeight: 500, color: '#34d399' }}>Left</span>
            </div>
            <div style={{ marginTop: '0.65rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <div style={{ flex: 1, height: 6, background: 'rgba(16, 185, 129, 0.15)', borderRadius: 4, overflow: 'hidden' }}>
                <div style={{ width: `${availableRate}%`, height: '100%', background: '#10b981', borderRadius: 4 }} />
              </div>
              <span style={{ fontSize: '0.78rem', fontWeight: 700, color: '#10b981' }}>
                {availableRate}%
              </span>
            </div>
          </div>
          <div className="stat-icon" style={{ background: 'rgba(16, 185, 129, 0.15)', borderColor: 'rgba(16, 185, 129, 0.3)' }}>
            🟢
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              height: 4,
              background: 'linear-gradient(90deg, #10b981, #34d399)'
            }}
          />
        </div>

        {/* CLEANING / SANITIZING */}
        <div
          className="stat-card"
          style={{
            background: 'var(--surface)',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            boxShadow: '0 8px 24px rgba(245, 158, 11, 0.1)',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <div style={{ flex: 1 }}>
            <div className="stat-label" style={{ color: '#fbbf24' }}>CLEANING &amp; SANITIZING</div>
            <div className="stat-value" style={{ color: '#f59e0b' }}>
              {cleaningBeds} <span style={{ fontSize: '1rem', fontWeight: 500, color: '#fbbf24' }}>Beds</span>
            </div>
            <div style={{ marginTop: '0.65rem', fontSize: '0.78rem', color: 'var(--text-2)' }}>
              Terminal disinfection before next intake
            </div>
          </div>
          <div className="stat-icon" style={{ background: 'rgba(245, 158, 11, 0.15)', borderColor: 'rgba(245, 158, 11, 0.3)' }}>
            🧹
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              height: 4,
              background: 'linear-gradient(90deg, #f59e0b, #fbbf24)'
            }}
          />
        </div>
      </div>

      {/* WARD CENSUS MATRIX (INTERACTIVE CARDS PER WARD) */}
      <div style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem' }}>
          <div>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
              Ward Capacity &amp; Occupancy Telemetry
            </h3>
            <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--text-3)' }}>
              Click any ward card to quickly filter bed inventory.
            </p>
          </div>
          {selectedWard !== 'ALL' && (
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => setSelectedWard('ALL')}
            >
              Clear Filter ({selectedWard})
            </button>
          )}
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: '1rem'
          }}
        >
          {Object.entries(WARD_METADATA).map(([wKey, wMeta]) => {
            const data = wardBreakdown[wKey] || { total: 0, occupied: 0, available: 0, cleaning: 0 };
            const isSelected = selectedWard === wKey;
            const wardOccRate = data.total > 0 ? ((data.occupied / data.total) * 100).toFixed(0) : '0';

            return (
              <div
                key={wKey}
                onClick={() => setSelectedWard(isSelected ? 'ALL' : wKey)}
                style={{
                  background: isSelected ? 'var(--surface-3)' : 'var(--surface)',
                  border: isSelected ? `2px solid ${wMeta.color}` : '1px solid var(--border)',
                  borderRadius: 'var(--radius)',
                  padding: '1rem 1.15rem',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  boxShadow: isSelected ? `0 0 20px ${wMeta.color}33` : 'var(--shadow-sm)',
                  position: 'relative'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span style={{ fontSize: '1.25rem' }}>{wMeta.icon}</span>
                    <strong style={{ fontSize: '0.92rem', color: 'var(--text)' }}>{wMeta.label}</strong>
                  </div>
                  <span className="badge badge-secondary" style={{ fontSize: '0.7rem' }}>
                    {data.total} Units
                  </span>
                </div>

                <div style={{ display: 'flex', gap: '0.65rem', marginBottom: '0.75rem', fontSize: '0.78rem' }}>
                  <span style={{ color: '#10b981', fontWeight: 600 }}>🟢 {data.available} Left</span>
                  <span style={{ color: '#f43f5e', fontWeight: 600 }}>🔴 {data.occupied} Full</span>
                  {data.cleaning > 0 && (
                    <span style={{ color: '#f59e0b', fontWeight: 600 }}>🧹 {data.cleaning} Clean</span>
                  )}
                </div>

                {/* Ward Progress Bar */}
                <div style={{ height: 6, background: 'var(--surface-2)', borderRadius: 3, overflow: 'hidden' }}>
                  <div
                    style={{
                      width: `${wardOccRate}%`,
                      height: '100%',
                      background: wMeta.color,
                      borderRadius: 3
                    }}
                  />
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '0.35rem', fontSize: '0.7rem', color: 'var(--text-3)' }}>
                  <span>Occupancy</span>
                  <span style={{ fontWeight: 700, color: 'var(--text-2)' }}>{wardOccRate}%</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* BED ALLOCATION INVENTORY SECTION */}
      <div className="table-wrapper" style={{ padding: '1.25rem' }}>
        {/* Toolbar & Filters */}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '1rem',
            marginBottom: '1.25rem',
            paddingBottom: '1rem',
            borderBottom: '1px solid var(--border)'
          }}
        >
          {/* Search Box */}
          <div style={{ flex: '1 1 280px', maxWidth: 400 }}>
            <input
              type="text"
              className="form-input"
              placeholder="🔍 Search Bed #, Ward, or Patient..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={{ padding: '0.55rem 0.9rem', fontSize: '0.85rem' }}
            />
          </div>

          {/* Status Filter Pills */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', alignItems: 'center' }}>
            <button
              className={`btn btn-sm ${selectedStatus === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setSelectedStatus('ALL')}
            >
              All Beds ({totalBeds})
            </button>
            <button
              className={`btn btn-sm ${selectedStatus === 'AVAILABLE' ? 'btn-success' : 'btn-secondary'}`}
              onClick={() => setSelectedStatus('AVAILABLE')}
            >
              🟢 Left / Available ({availableBeds})
            </button>
            <button
              className={`btn btn-sm ${selectedStatus === 'OCCUPIED' ? 'btn-danger' : 'btn-secondary'}`}
              onClick={() => setSelectedStatus('OCCUPIED')}
            >
              🔴 Full / Occupied ({occupiedBeds})
            </button>
            <button
              className={`btn btn-sm ${selectedStatus === 'CLEANING' ? 'btn-secondary' : 'btn-secondary'}`}
              onClick={() => setSelectedStatus('CLEANING')}
              style={{
                borderColor: selectedStatus === 'CLEANING' ? '#f59e0b' : undefined,
                color: selectedStatus === 'CLEANING' ? '#fbbf24' : undefined,
                background: selectedStatus === 'CLEANING' ? 'rgba(245, 158, 11, 0.15)' : undefined
              }}
            >
              🧹 Cleaning ({cleaningBeds})
            </button>

            {/* View Mode Toggle */}
            <div style={{ display: 'inline-flex', background: 'var(--surface-2)', borderRadius: 'var(--radius)', padding: 2, marginLeft: '0.5rem' }}>
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => setViewMode('table')}
                style={{
                  padding: '0.35rem 0.75rem',
                  background: viewMode === 'table' ? 'var(--primary)' : 'transparent',
                  color: viewMode === 'table' ? '#fff' : 'var(--text-3)',
                  border: 'none',
                  borderRadius: 'calc(var(--radius) - 2px)'
                }}
              >
                📋 Table
              </button>
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => setViewMode('grid')}
                style={{
                  padding: '0.35rem 0.75rem',
                  background: viewMode === 'grid' ? 'var(--primary)' : 'transparent',
                  color: viewMode === 'grid' ? '#fff' : 'var(--text-3)',
                  border: 'none',
                  borderRadius: 'calc(var(--radius) - 2px)'
                }}
              >
                🛏️ Grid Map
              </button>
            </div>
          </div>
        </div>

        {/* CONTENT: TABLE VIEW */}
        {viewMode === 'table' ? (
          <table>
            <thead>
              <tr>
                <th>Ward Section</th>
                <th>Bed Identifier</th>
                <th>Daily Rate</th>
                <th>Real-Time Status</th>
                <th>Admitted Patient</th>
                <th className="text-right">Allocation Action</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="text-center py-4" style={{ color: 'var(--text-3)' }}>
                    <span style={{ display: 'inline-block', animation: 'spin 1s infinite' }}>🔄</span> Loading real-time bed telemetry...
                  </td>
                </tr>
              ) : filteredBeds.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-4" style={{ color: 'var(--text-3)' }}>
                    No beds matched current criteria ({searchQuery ? `Search: "${searchQuery}"` : ''} Ward: {selectedWard}, Status: {selectedStatus})
                  </td>
                </tr>
              ) : (
                filteredBeds.map(bed => {
                  const meta = WARD_METADATA[bed.wardType] || {
                    label: bed.wardType,
                    icon: '🏥',
                    desc: '',
                    color: 'var(--primary)'
                  };

                  return (
                    <tr key={bed.id}>
                      {/* Ward Section */}
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                          <span style={{ fontSize: '1.25rem' }}>{meta.icon}</span>
                          <div>
                            <div style={{ fontWeight: 700, color: 'var(--text)' }}>
                              {meta.label}
                            </div>
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-3)' }}>
                              {meta.desc}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Bed Identifier */}
                      <td>
                        <span
                          style={{
                            fontFamily: 'monospace',
                            fontWeight: 700,
                            fontSize: '0.9rem',
                            background: 'var(--surface-2)',
                            color: 'var(--text)',
                            padding: '0.3rem 0.65rem',
                            borderRadius: 'var(--radius-sm)',
                            border: '1px solid var(--border)'
                          }}
                        >
                          BED-{bed.bedNumber}
                        </span>
                      </td>

                      {/* Daily Rate */}
                      <td>
                        <div style={{ fontWeight: 600, color: 'var(--text-2)' }}>
                          ${(bed.dailyRate || meta.defaultRate || 1500).toLocaleString()}
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-3)', fontWeight: 400 }}> / day</span>
                        </div>
                      </td>

                      {/* Status */}
                      <td>{renderStatusBadge(bed.status)}</td>

                      {/* Admitted Patient */}
                      <td>
                        {bed.status === 'OCCUPIED' && bed.currentPatientName ? (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                            <div
                              style={{
                                width: 32,
                                height: 32,
                                borderRadius: '50%',
                                background: 'rgba(244, 63, 94, 0.15)',
                                color: '#f43f5e',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontWeight: 700,
                                fontSize: '0.8rem',
                                border: '1px solid rgba(244, 63, 94, 0.3)'
                              }}
                            >
                              {bed.currentPatientName.charAt(0)}
                            </div>
                            <div>
                              <strong style={{ color: 'var(--text)', display: 'block' }}>
                                {bed.currentPatientName}
                              </strong>
                              <span style={{ fontSize: '0.75rem', color: 'var(--text-3)' }}>
                                Admitted Inpatient
                              </span>
                            </div>
                          </div>
                        ) : bed.status === 'CLEANING' || bed.status === 'MAINTENANCE' ? (
                          <span style={{ color: '#f59e0b', fontSize: '0.82rem' }}>
                            🧴 Sanitation in progress
                          </span>
                        ) : (
                          <span style={{ color: '#10b981', fontSize: '0.82rem' }}>
                            ✨ Ready for admission
                          </span>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="text-right">
                        <div className="table-actions">
                          {bed.status === 'AVAILABLE' && can('canAdmitBed') && (
                            <button
                              className="btn btn-primary btn-sm"
                              onClick={() => openAdmitModal(bed)}
                            >
                              📥 Admit Patient
                            </button>
                          )}

                          {bed.status === 'OCCUPIED' && can('canDischargeBed') && (
                            <button
                              className="btn btn-danger btn-sm"
                              onClick={() => setDischargeBed(bed)}
                            >
                              📤 Discharge
                            </button>
                          )}

                          {(bed.status === 'CLEANING' || bed.status === 'MAINTENANCE' || bed.status === 'OUT_OF_SERVICE') && can('canMarkBedReady') && (
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => handleReady(bed)}
                              style={{ borderColor: 'rgba(245, 158, 11, 0.4)', color: '#fbbf24' }}
                            >
                              🧹 Mark Ready
                            </button>
                          )}

                          {!((bed.status === 'AVAILABLE' && can('canAdmitBed')) ||
                             (bed.status === 'OCCUPIED' && can('canDischargeBed')) ||
                             ((bed.status === 'CLEANING' || bed.status === 'MAINTENANCE' || bed.status === 'OUT_OF_SERVICE') && can('canMarkBedReady'))) && (
                            <span style={{ color: 'var(--text-3)', fontSize: '0.8rem', fontStyle: 'italic' }}>
                              👁️ View Only
                            </span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        ) : (
          /* CONTENT: INTERACTIVE VISUAL GRID MAP */
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: '1.25rem',
              paddingTop: '0.5rem'
            }}
          >
            {filteredBeds.map(bed => {
              const meta = WARD_METADATA[bed.wardType] || { label: bed.wardType, icon: '🏥', color: '#6366f1' };
              const isOccupied = bed.status === 'OCCUPIED';
              const isAvailable = bed.status === 'AVAILABLE';
              const isCleaning = bed.status === 'CLEANING' || bed.status === 'MAINTENANCE';

              const borderGlow = isOccupied ? '#f43f5e' : isAvailable ? '#10b981' : '#f59e0b';

              return (
                <div
                  key={bed.id}
                  style={{
                    background: 'var(--surface)',
                    border: `1px solid ${borderGlow}55`,
                    borderRadius: 'var(--radius)',
                    padding: '1.25rem',
                    position: 'relative',
                    overflow: 'hidden',
                    boxShadow: 'var(--shadow-sm)'
                  }}
                >
                  {/* Top Status Color Bar */}
                  <div
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      height: 4,
                      background: borderGlow
                    }}
                  />

                  {/* Bed Header */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: 'var(--text-3)', marginBottom: '0.2rem' }}>
                        <span>{meta.icon}</span>
                        <span>{meta.label}</span>
                      </div>
                      <div style={{ fontSize: '1.2rem', fontWeight: 800, fontFamily: 'monospace', color: 'var(--text)' }}>
                        BED-{bed.bedNumber}
                      </div>
                    </div>
                    {renderStatusBadge(bed.status)}
                  </div>

                  {/* Price info */}
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-2)', marginBottom: '1rem' }}>
                    Rate: <strong>${(bed.dailyRate || 1500).toLocaleString()}</strong> / day
                  </div>

                  {/* Patient Info or Status Details */}
                  <div
                    style={{
                      background: 'var(--surface-2)',
                      padding: '0.75rem',
                      borderRadius: 'var(--radius-sm)',
                      marginBottom: '1rem',
                      fontSize: '0.85rem'
                    }}
                  >
                    {isOccupied ? (
                      <div>
                        <div style={{ fontSize: '0.7rem', color: '#fb7185', fontWeight: 700, textTransform: 'uppercase' }}>
                          Admitted Patient
                        </div>
                        <div style={{ fontWeight: 700, color: 'var(--text)', marginTop: '0.2rem' }}>
                          👤 {bed.currentPatientName || 'Patient'}
                        </div>
                      </div>
                    ) : isCleaning ? (
                      <div style={{ color: '#fbbf24', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <span>🧹</span> Undergoing terminal disinfection
                      </div>
                    ) : (
                      <div style={{ color: '#34d399', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <span>✨</span> Vacant &amp; ready for patient
                      </div>
                    )}
                  </div>

                  {/* Action Button */}
                  <div>
                    {isAvailable && can('canAdmitBed') && (
                      <button
                        className="btn btn-primary btn-sm"
                        style={{ width: '100%' }}
                        onClick={() => openAdmitModal(bed)}
                      >
                        📥 Admit Patient
                      </button>
                    )}

                    {isOccupied && can('canDischargeBed') && (
                      <button
                        className="btn btn-danger btn-sm"
                        style={{ width: '100%' }}
                        onClick={() => setDischargeBed(bed)}
                      >
                        📤 Discharge Patient
                      </button>
                    )}

                    {isCleaning && can('canMarkBedReady') && (
                      <button
                        className="btn btn-secondary btn-sm"
                        style={{ width: '100%', borderColor: 'rgba(245, 158, 11, 0.4)', color: '#fbbf24' }}
                        onClick={() => handleReady(bed)}
                      >
                        🧹 Mark Ready (Available)
                      </button>
                    )}

                    {!((isAvailable && can('canAdmitBed')) || (isOccupied && can('canDischargeBed')) || (isCleaning && can('canMarkBedReady'))) && (
                      <div className="text-center text-xs text-muted" style={{ padding: '0.4rem 0', fontStyle: 'italic', color: 'var(--text-3)' }}>
                        👁️ View Only
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ADMIT PATIENT MODAL */}
      {admitBed && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setAdmitBed(null)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <div>
                <span className="modal-title">Admit Patient to Bed</span>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-3)', marginTop: '0.2rem' }}>
                  Bed: <strong style={{ color: 'var(--primary)' }}>BED-{admitBed.bedNumber}</strong> ({WARD_METADATA[admitBed.wardType]?.label || admitBed.wardType})
                </div>
              </div>
              <button className="modal-close" onClick={() => setAdmitBed(null)}>×</button>
            </div>
            <form onSubmit={handleAdmit}>
              <div className="modal-body">
                <div
                  style={{
                    background: 'var(--surface-2)',
                    padding: '0.75rem 1rem',
                    borderRadius: 'var(--radius)',
                    marginBottom: '1.25rem',
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: '0.85rem'
                  }}
                >
                  <div>
                    <span style={{ color: 'var(--text-3)' }}>Ward Type: </span>
                    <strong>{WARD_METADATA[admitBed.wardType]?.label || admitBed.wardType}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-3)' }}>Daily Tariff: </span>
                    <strong style={{ color: 'var(--success)' }}>${(admitBed.dailyRate || 1500).toLocaleString()} / day</strong>
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Select Registered Inpatient *</label>
                  <select
                    className="form-select"
                    value={selectedPatientId}
                    onChange={e => setSelectedPatientId(e.target.value)}
                    required
                  >
                    <option value="">Choose patient for admission...</option>
                    {patients.map(p => (
                      <option key={p.patientId || (p as any).id} value={p.patientId || (p as any).id}>
                        {p.firstName} {p.lastName} — ({p.uniquePatientId}) [{p.gender}, {p.bloodGroup || 'N/A'}]
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Admission Clinical Reason / Notes</label>
                  <textarea
                    className="form-input"
                    value={admissionReason}
                    onChange={e => setAdmissionReason(e.target.value)}
                    placeholder="e.g. Post-operative observation, severe pneumonia, acute emergency triage..."
                    rows={3}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setAdmitBed(null)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting || !selectedPatientId}>
                  {submitting ? 'Admitting...' : 'Confirm Patient Admission'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DISCHARGE PATIENT MODAL */}
      {dischargeBed && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setDischargeBed(null)}>
          <div className="modal modal-md">
            <div className="modal-header">
              <div>
                <span className="modal-title">Discharge Inpatient</span>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-3)', marginTop: '0.2rem' }}>
                  Releasing Bed: <strong style={{ color: '#f43f5e' }}>BED-{dischargeBed.bedNumber}</strong>
                </div>
              </div>
              <button className="modal-close" onClick={() => setDischargeBed(null)}>×</button>
            </div>
            <form onSubmit={handleDischarge}>
              <div className="modal-body">
                <div
                  style={{
                    background: 'rgba(244, 63, 94, 0.08)',
                    border: '1px solid rgba(244, 63, 94, 0.25)',
                    padding: '0.85rem 1.15rem',
                    borderRadius: 'var(--radius)',
                    marginBottom: '1rem'
                  }}
                >
                  <div style={{ fontSize: '0.75rem', color: '#fb7185', fontWeight: 700, textTransform: 'uppercase' }}>
                    Currently Admitted Patient
                  </div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--text)', marginTop: '0.25rem' }}>
                    {dischargeBed.currentPatientName || 'Admitted Patient'}
                  </div>
                  <div style={{ fontSize: '0.78rem', color: 'var(--text-3)', marginTop: '0.35rem' }}>
                    Notice: Upon discharge, the bed will automatically enter <strong>CLEANING</strong> status for terminal sanitization.
                  </div>
                </div>

                {/* Real-time Bed Billing Preview */}
                {(() => {
                  const stayDays = (() => {
                    if (!dischargeBed?.lastOccupiedAt) return 1;
                    const time = new Date(dischargeBed.lastOccupiedAt).getTime();
                    if (isNaN(time)) return 1;
                    const diffDays = Math.ceil((Date.now() - time) / (1000 * 60 * 60 * 24));
                    return isNaN(diffDays) || diffDays < 1 ? 1 : diffDays;
                  })();
                  const bedRate = Number(dischargeBed?.dailyRate) || 1500;
                  const totalEst = bedRate * stayDays;
                  return (
                    <div style={{
                      background: 'rgba(16, 185, 129, 0.08)',
                      border: '1px solid rgba(16, 185, 129, 0.3)',
                      padding: '0.85rem 1.15rem',
                      borderRadius: 'var(--radius)',
                      marginBottom: '1.25rem'
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                        <span style={{ fontSize: '0.75rem', color: '#10b981', fontWeight: 700, textTransform: 'uppercase' }}>
                          ⚡ Real-Time Bed Billing Reflection
                        </span>
                        <span className="badge badge-success text-xs">Auto-Billed</span>
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.5rem', textAlign: 'center', margin: '0.4rem 0' }}>
                        <div style={{ background: 'rgba(0,0,0,0.25)', padding: '0.45rem', borderRadius: 4 }}>
                          <div style={{ fontSize: '0.68rem', color: 'var(--text-3)' }}>Stay Duration</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 700 }}>{stayDays} Day{stayDays > 1 ? 's' : ''}</div>
                        </div>
                        <div style={{ background: 'rgba(0,0,0,0.25)', padding: '0.45rem', borderRadius: 4 }}>
                          <div style={{ fontSize: '0.68rem', color: 'var(--text-3)' }}>Daily Rate</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 700 }}>${bedRate}</div>
                        </div>
                        <div style={{ background: 'rgba(0,0,0,0.25)', padding: '0.45rem', borderRadius: 4 }}>
                          <div style={{ fontSize: '0.68rem', color: '#10b981' }}>Total Charge</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 800, color: '#10b981' }}>${totalEst.toLocaleString()}</div>
                        </div>
                      </div>
                      <div style={{ fontSize: '0.74rem', color: 'var(--text-2)', marginTop: '0.35rem' }}>
                        💰 This <strong>${totalEst.toLocaleString()}</strong> room charge will automatically be added to the patient&apos;s invoice in real-time.
                      </div>
                    </div>
                  );
                })()}

                <div className="form-group">
                  <label className="form-label">Clinical Discharge Summary / Exit Notes</label>
                  <textarea
                    className="form-input"
                    value={dischargeNotes}
                    onChange={e => setDischargeNotes(e.target.value)}
                    placeholder="e.g. Patient recovered well, vitals stable, prescribed 5-day antibiotic course, follow-up in 1 week..."
                    rows={3}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setDischargeBed(null)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-danger" disabled={submitting}>
                  {submitting ? 'Discharging...' : 'Confirm Patient Discharge'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Wards;
