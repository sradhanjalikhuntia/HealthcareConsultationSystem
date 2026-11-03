import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { patientApi } from '../../api/patients';
import { appointmentApi } from '../../api/appointments';
import { useAuth } from '../../context/AuthContext';
import type { PatientResponse } from '../../types/patient';
import type { AppointmentResponse } from '../../api/appointments';

interface SearchResult {
  type: 'patient' | 'appointment' | 'action';
  id: string;
  title: string;
  subtitle: string;
  path: string;
  icon: string;
}

export const GlobalSearch: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const isPatient = user?.role === 'PATIENT';

  const quickActions: SearchResult[] = isPatient ? [
    { type: 'action', id: 'q1', title: 'Go to Dashboard', subtitle: 'Overview & quick health stats', path: '/dashboard', icon: '📊' },
    { type: 'action', id: 'q2', title: 'My Appointments', subtitle: 'Book or view appointments', path: '/appointments', icon: '📅' },
    { type: 'action', id: 'q3', title: 'My Invoices', subtitle: 'Bills & payments', path: '/billing', icon: '💳' },
  ] : [
    { type: 'action', id: 'q1', title: 'Go to Dashboard', subtitle: 'Overview & stats', path: '/dashboard', icon: '📊' },
    { type: 'action', id: 'q2', title: 'All Patients', subtitle: 'Patient directory & EMR', path: '/patients', icon: '🏥' },
    { type: 'action', id: 'q3', title: 'Appointments', subtitle: 'Schedule & calendar', path: '/appointments', icon: '📅' },
    { type: 'action', id: 'q4', title: 'Billing & Invoices', subtitle: 'Accounts & claims', path: '/billing', icon: '💳' },
    { type: 'action', id: 'q5', title: 'Wards & Beds', subtitle: 'Bed allocation & ICU', path: '/wards', icon: '🛏️' },
  ];

  useEffect(() => { inputRef.current?.focus(); }, []);

  const search = useCallback(async (q: string) => {
    if (!q.trim()) { setResults([]); return; }
    setLoading(true);
    try {
      const items: SearchResult[] = [];

      if (isPatient) {
        // Patient only searches their own appointments
        try {
          const ar = await appointmentApi.getAll(0, 20);
          ar.data.content
            .filter((a: AppointmentResponse) =>
              (a.doctorName || '').toLowerCase().includes(q.toLowerCase()) ||
              (a.reason || '').toLowerCase().includes(q.toLowerCase())
            )
            .forEach((a: AppointmentResponse) =>
              items.push({
                type: 'appointment', id: a.appointmentId,
                title: `Consultation with Dr. ${a.doctorName}`,
                subtitle: `${a.appointmentDate} · ${a.startTime} · ${a.status}`,
                path: '/appointments', icon: '📅',
              })
            );
        } catch {
          // ignore
        }
      } else {
        // Staff searches both patient directory and all appointments
        const [pr, ar] = await Promise.allSettled([
          patientApi.getPatients(0, 6, q),
          appointmentApi.getAll(0, 20),
        ]);

        if (pr.status === 'fulfilled') {
          pr.value.data.content.forEach((p: PatientResponse) =>
            items.push({
              type: 'patient', id: p.patientId,
              title: `${p.firstName} ${p.lastName}`,
              subtitle: `${p.uniquePatientId} · ${p.phone} · ${p.status}`,
              path: `/patients/${p.patientId}`, icon: '🏥',
            })
          );
        }
        if (ar.status === 'fulfilled') {
          ar.value.data.content
            .filter((a: AppointmentResponse) =>
              (a.patientName || '').toLowerCase().includes(q.toLowerCase()) ||
              (a.doctorName || '').toLowerCase().includes(q.toLowerCase()) ||
              (a.reason || '').toLowerCase().includes(q.toLowerCase())
            )
            .slice(0, 4)
            .forEach((a: AppointmentResponse) =>
              items.push({
                type: 'appointment', id: a.appointmentId,
                title: `${a.patientName} → Dr. ${a.doctorName}`,
                subtitle: `${a.appointmentDate} · ${a.startTime} · ${a.status}`,
                path: '/appointments', icon: '📅',
              })
            );
        }
      }

      setResults(items);
      setSelected(0);
    } finally {
      setLoading(false);
    }
  }, [isPatient]);

  useEffect(() => {
    const timer = setTimeout(() => search(query), 300);
    return () => clearTimeout(timer);
  }, [query, search]);

  const displayResults = query.trim() ? results : quickActions;

  const handleSelect = (r: SearchResult) => {
    navigate(r.path);
    onClose();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setSelected(s => Math.min(s + 1, displayResults.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setSelected(s => Math.max(s - 1, 0)); }
    else if (e.key === 'Enter' && displayResults[selected]) handleSelect(displayResults[selected]);
    else if (e.key === 'Escape') onClose();
  };

  const typeBadge = (type: string) => {
    const m: Record<string, string> = { patient: 'badge-info', appointment: 'badge-success', action: 'badge-secondary' };
    return <span className={`badge ${m[type] || 'badge-secondary'}`} style={{ fontSize: '.6rem' }}>{type}</span>;
  };

  return (
    <div className="cmd-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="cmd-palette">
        <div style={{ position: 'relative' }}>
          <span style={{ position: 'absolute', left: '1.25rem', top: '50%', transform: 'translateY(-50%)', opacity: .5, fontSize: '1rem' }}>🔍</span>
          <input
            ref={inputRef}
            className="cmd-input"
            style={{ paddingLeft: '2.75rem' }}
            placeholder={isPatient ? 'Search your appointments or navigate…' : 'Search patients, appointments, or navigate…'}
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
          />
        </div>
        <div className="cmd-results">
          {loading && (
            <div className="cmd-empty">
              <div style={{ display: 'inline-block', width: 16, height: 16, border: '2px solid var(--border-light)', borderTopColor: 'var(--primary)', borderRadius: '50%', animation: 'spin 0.7s linear infinite', marginRight: '.5rem', verticalAlign: 'middle' }} />
              Searching…
            </div>
          )}
          {!loading && query && results.length === 0 && (
            <div className="cmd-empty">No results found for "<strong>{query}</strong>"</div>
          )}
          {!loading && displayResults.length > 0 && (
            <>
              <div className="cmd-section-header">
                {query ? `${results.length} result${results.length !== 1 ? 's' : ''}` : 'Quick Navigation'}
              </div>
              {displayResults.map((r, i) => (
                <div
                  key={r.id}
                  className={`cmd-item${i === selected ? ' selected' : ''}`}
                  onClick={() => handleSelect(r)}
                  onMouseEnter={() => setSelected(i)}
                >
                  <span className="cmd-item-icon">{r.icon}</span>
                  <div className="cmd-item-main">
                    <div className="cmd-item-title">{r.title}</div>
                    <div className="cmd-item-sub">{r.subtitle}</div>
                  </div>
                  {typeBadge(r.type)}
                </div>
              ))}
            </>
          )}
        </div>
        <div className="cmd-footer">
          <span className="cmd-hint"><span className="cmd-key">↑↓</span> Navigate</span>
          <span className="cmd-hint"><span className="cmd-key">↵</span> Open</span>
          <span className="cmd-hint"><span className="cmd-key">Esc</span> Close</span>
          <span style={{ flex: 1 }} />
          <span className="cmd-hint" style={{ opacity: .5 }}>Ctrl+K to open</span>
        </div>
      </div>
    </div>
  );
};
