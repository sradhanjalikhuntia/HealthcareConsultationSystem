import React, { useEffect, useState } from 'react';
import { appointmentApi, type AppointmentResponse } from '../api/appointments';

export interface TimeSlot {
  start: string;
  end: string;
  label: string;
}

const MORNING_SLOTS: TimeSlot[] = [
  { start: '09:00', end: '09:30', label: '09:00 AM' },
  { start: '09:30', end: '10:00', label: '09:30 AM' },
  { start: '10:00', end: '10:30', label: '10:00 AM' },
  { start: '10:30', end: '11:00', label: '10:30 AM' },
  { start: '11:00', end: '11:30', label: '11:00 AM' },
  { start: '11:30', end: '12:00', label: '11:30 AM' },
  { start: '12:00', end: '12:30', label: '12:00 PM' },
  { start: '12:30', end: '13:00', label: '12:30 PM' },
];

const AFTERNOON_SLOTS: TimeSlot[] = [
  { start: '14:00', end: '14:30', label: '02:00 PM' },
  { start: '14:30', end: '15:00', label: '02:30 PM' },
  { start: '15:00', end: '15:30', label: '03:00 PM' },
  { start: '15:30', end: '16:00', label: '03:30 PM' },
  { start: '16:00', end: '16:30', label: '04:00 PM' },
  { start: '16:30', end: '17:00', label: '04:30 PM' },
  { start: '17:00', end: '17:30', label: '05:00 PM' },
  { start: '17:30', end: '18:00', label: '05:30 PM' },
];

interface TimeSlotPickerProps {
  doctorId: string;
  date: string;
  selectedStartTime: string;
  selectedEndTime: string;
  excludeAppointmentId?: string;
  onSelectSlot: (start: string, end: string) => void;
}

export const TimeSlotPicker: React.FC<TimeSlotPickerProps> = ({
  doctorId,
  date,
  selectedStartTime,
  selectedEndTime,
  excludeAppointmentId,
  onSelectSlot,
}) => {
  const [booked, setBooked] = useState<AppointmentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [showCustom, setShowCustom] = useState(false);

  useEffect(() => {
    if (!doctorId || !date) {
      setBooked([]);
      return;
    }

    let isMounted = true;
    setLoading(true);

    appointmentApi
      .getAvailability(doctorId, date)
      .then(res => {
        if (!isMounted) return;
        const list = Array.isArray(res.data) ? res.data : [];
        const filtered = excludeAppointmentId
          ? list.filter(a => a.appointmentId !== excludeAppointmentId)
          : list;
        setBooked(filtered);
      })
      .catch(() => {
        if (isMounted) setBooked([]);
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [doctorId, date, excludeAppointmentId]);

  const now = new Date();
  const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const currentTimeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const isPastDate = date ? date < todayStr : false;

  const isSlotPast = (slot: TimeSlot) => {
    if (isPastDate) return true;
    if (date === todayStr) {
      return slot.start <= currentTimeStr;
    }
    return false;
  };

  // Check if slot overlaps with any active booked appointments
  const isSlotBooked = (slot: TimeSlot) => {
    const slotStart = slot.start;
    const slotEnd = slot.end;

    return booked.some(b => {
      const bStart = b.startTime.substring(0, 5);
      const bEnd = b.endTime.substring(0, 5);
      // Overlap condition: start < bEnd AND end > bStart
      return slotStart < bEnd && slotEnd > bStart;
    });
  };

  const isSlotSelected = (slot: TimeSlot) => {
    return (
      selectedStartTime.substring(0, 5) === slot.start &&
      selectedEndTime.substring(0, 5) === slot.end
    );
  };

  if (!doctorId || !date) {
    return (
      <div style={{
        padding: '1rem',
        background: 'rgba(255, 255, 255, 0.02)',
        border: '1px dashed var(--border)',
        borderRadius: 'var(--radius)',
        textAlign: 'center',
        color: 'var(--text-3)',
        fontSize: '0.85rem'
      }}>
        👆 Select a doctor and date above to load live available time slots.
      </div>
    );
  }

  const renderSlotGroup = (title: string, icon: string, slots: TimeSlot[]) => (
    <div className="mb-3">
      <div className="flex items-center gap-2 mb-2">
        <span>{icon}</span>
        <span style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--text-2)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
          {title}
        </span>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(95px, 1fr))',
        gap: '0.5rem'
      }}>
        {slots.map(slot => {
          const bookedFlag = isSlotBooked(slot);
          const pastFlag = isSlotPast(slot);
          const selectedFlag = isSlotSelected(slot);
          const isDisabled = bookedFlag || pastFlag;

          return (
            <button
              key={slot.start}
              type="button"
              disabled={isDisabled}
              onClick={() => onSelectSlot(slot.start, slot.end)}
              title={pastFlag ? 'This time slot has already passed' : bookedFlag ? 'Already booked by another patient' : 'Available'}
              style={{
                padding: '0.55rem 0.4rem',
                borderRadius: 'var(--radius-sm)',
                fontSize: '0.8rem',
                fontWeight: selectedFlag ? 700 : 600,
                cursor: isDisabled ? 'not-allowed' : 'pointer',
                transition: 'all 0.15s ease',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '0.2rem',
                border: selectedFlag
                  ? '2px solid var(--primary)'
                  : bookedFlag
                  ? '1px solid rgba(239, 68, 68, 0.2)'
                  : pastFlag
                  ? '1px solid rgba(255, 255, 255, 0.04)'
                  : '1px solid var(--border)',
                background: selectedFlag
                  ? 'linear-gradient(135deg, rgba(99, 102, 241, 0.3) 0%, rgba(139, 92, 246, 0.25) 100%)'
                  : bookedFlag
                  ? 'rgba(239, 68, 68, 0.05)'
                  : pastFlag
                  ? 'rgba(255, 255, 255, 0.02)'
                  : 'var(--surface-solid)',
                color: selectedFlag
                  ? '#ffffff'
                  : bookedFlag
                  ? 'rgba(239, 68, 68, 0.6)'
                  : pastFlag
                  ? 'var(--text-3)'
                  : 'var(--text)',
                textDecoration: bookedFlag ? 'line-through' : 'none',
                opacity: pastFlag ? 0.45 : bookedFlag ? 0.6 : 1,
                boxShadow: selectedFlag ? '0 0 12px rgba(99, 102, 241, 0.4)' : undefined
              }}
            >
              <span>{slot.label}</span>
              <span style={{
                fontSize: '0.65rem',
                fontWeight: 700,
                textDecoration: 'none',
                color: selectedFlag
                  ? '#a5b4fc'
                  : bookedFlag
                  ? 'var(--danger)'
                  : pastFlag
                  ? 'var(--text-3)'
                  : 'var(--success)'
              }}>
                {selectedFlag ? '✓ Chosen' : bookedFlag ? 'Booked' : pastFlag ? 'Passed' : 'Available'}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );

  return (
    <div style={{
      background: 'rgba(255, 255, 255, 0.02)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius)',
      padding: '1rem',
      marginBottom: '1rem'
    }}>
      {/* Header & Legend */}
      <div className="flex justify-between items-center mb-3 flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <span style={{ fontSize: '1rem' }}>🕒</span>
          <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>Real-Time Availability Calendar</span>
          {loading && <span className="spinner" style={{ width: 14, height: 14 }} />}
        </div>

        {/* Legend */}
        <div className="flex items-center gap-3 text-xs text-muted">
          <span className="flex items-center gap-1">
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--success)' }} />
            Available
          </span>
          <span className="flex items-center gap-1">
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--danger)' }} />
            Booked
          </span>
          <span className="flex items-center gap-1">
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--text-3)' }} />
            Passed
          </span>
          <span className="flex items-center gap-1">
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--primary)' }} />
            Selected
          </span>
        </div>
      </div>

      {/* Past Date Alert */}
      {isPastDate && (
        <div style={{
          padding: '0.6rem 0.85rem',
          background: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          borderRadius: 'var(--radius-sm)',
          fontSize: '0.8rem',
          color: 'var(--danger)',
          fontWeight: 600,
          marginBottom: '0.85rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem'
        }}>
          <span>🚫</span>
          <span>The date {date} has already passed. Please choose today or a future date.</span>
        </div>
      )}

      {/* Selected Slot Feedback Banner */}
      {!isPastDate && date === todayStr && selectedStartTime && selectedStartTime <= currentTimeStr && (
        <div style={{
          padding: '0.5rem 0.75rem',
          background: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.25)',
          borderRadius: 'var(--radius-sm)',
          fontSize: '0.8rem',
          color: 'var(--danger)',
          fontWeight: 600,
          marginBottom: '0.85rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem'
        }}>
          <span>⚠️</span>
          <span>This time slot ({selectedStartTime}) has already passed today. Please pick an upcoming time.</span>
        </div>
      )}

      {selectedStartTime && selectedEndTime ? (
        <div style={{
          padding: '0.5rem 0.75rem',
          background: 'rgba(16, 185, 129, 0.1)',
          border: '1px solid rgba(16, 185, 129, 0.25)',
          borderRadius: 'var(--radius-sm)',
          fontSize: '0.8rem',
          color: 'var(--success)',
          fontWeight: 600,
          marginBottom: '0.85rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span>✓ Confirmed Time Slot: {selectedStartTime} – {selectedEndTime} (No Conflicts Detected)</span>
          <button
            type="button"
            className="text-xs"
            style={{ background: 'none', color: 'var(--text-3)', textDecoration: 'underline' }}
            onClick={() => setShowCustom(c => !c)}
          >
            {showCustom ? 'Hide Custom Time' : 'Custom Time...'}
          </button>
        </div>
      ) : (
        <div style={{
          padding: '0.5rem 0.75rem',
          background: 'rgba(245, 158, 11, 0.1)',
          border: '1px solid rgba(245, 158, 11, 0.25)',
          borderRadius: 'var(--radius-sm)',
          fontSize: '0.8rem',
          color: 'var(--warning)',
          fontWeight: 600,
          marginBottom: '0.85rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span>⚠️ Please pick an available 30-minute consultation slot below</span>
          <button
            type="button"
            className="text-xs"
            style={{ background: 'none', color: 'var(--text-3)', textDecoration: 'underline' }}
            onClick={() => setShowCustom(c => !c)}
          >
            {showCustom ? 'Hide Custom Time' : 'Custom Time...'}
          </button>
        </div>
      )}

      {/* Custom Time inputs if toggled */}
      {showCustom && (
        <div className="form-row mb-3 p-2" style={{ background: 'var(--surface-solid)', borderRadius: 'var(--radius-sm)' }}>
          <div className="form-group mb-0">
            <label className="form-label text-xs">Custom Start</label>
            <input
              type="time"
              className="form-input"
              style={{ padding: '0.4rem 0.6rem', fontSize: '0.85rem' }}
              value={selectedStartTime}
              onChange={e => onSelectSlot(e.target.value, selectedEndTime)}
            />
          </div>
          <div className="form-group mb-0">
            <label className="form-label text-xs">Custom End</label>
            <input
              type="time"
              className="form-input"
              style={{ padding: '0.4rem 0.6rem', fontSize: '0.85rem' }}
              value={selectedEndTime}
              onChange={e => onSelectSlot(selectedStartTime, e.target.value)}
            />
          </div>
        </div>
      )}

      {/* Sessions Grid */}
      {renderSlotGroup('Morning Sessions', '🌅', MORNING_SLOTS)}
      {renderSlotGroup('Afternoon & Evening Sessions', '🌇', AFTERNOON_SLOTS)}
    </div>
  );
};
