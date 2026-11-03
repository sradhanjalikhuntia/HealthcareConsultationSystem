import apiClient from './axios';

export interface Bed {
  id: string;
  bedNumber: string;
  wardType: 'GENERAL' | 'ICU' | 'MATERNITY' | 'PEDIATRIC' | 'ISOLATION' | 'EMERGENCY' | string;
  status: 'AVAILABLE' | 'OCCUPIED' | 'CLEANING' | 'OUT_OF_SERVICE' | 'MAINTENANCE' | string;
  dailyRate: number;
  currentPatientId?: string;
  currentPatientName?: string;
  lastOccupiedAt?: string;
  lastDischargedAt?: string;
  lastSanitizedAt?: string;
  // Compatibility fallbacks
  wardName?: string;
  type?: string;
  pricePerDay?: number;
}

export interface WardSummary {
  totalBeds: number;
  availableBeds: number;
  occupiedBeds: number;
  cleaningBeds: number;
  outOfServiceBeds: number;
  byWard?: Record<string, {
    total: number;
    available: number;
    occupied: number;
    cleaning?: number;
  }>;
}

export const wardApi = {
  getBeds: (wardType?: string, status?: string) =>
    apiClient.get('/wards/beds', {
      params: {
        wardType: wardType && wardType !== 'ALL' ? wardType : undefined,
        status: status && status !== 'ALL' ? status : undefined,
        size: 100
      }
    }),
  getSummary: () => apiClient.get('/wards/summary'),
  admitPatient: (bedId: string, data: { patientId: string; reason?: string; notes?: string }) =>
    apiClient.post(`/wards/beds/${bedId}/admit`, {
      patientId: data.patientId,
      reason: data.reason || data.notes || 'Inpatient admission'
    }),
  dischargePatient: (bedId: string, data?: { dischargeSummary?: string }) =>
    apiClient.post(`/wards/beds/${bedId}/discharge`, data || { dischargeSummary: 'Discharged by clinical team' }),
  markReady: (bedId: string) => apiClient.post(`/wards/beds/${bedId}/ready`, {}),
};
