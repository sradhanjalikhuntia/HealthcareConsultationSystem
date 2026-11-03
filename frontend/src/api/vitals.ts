import apiClient from './axios';

export interface Vitals {
  id: string;
  patientId: string;
  recordedBy?: string;
  heartRate?: number;
  bloodPressure?: string;
  systolicBp?: number;
  diastolicBp?: number;
  temperature?: number;
  respiratoryRate?: number;
  spO2?: number;
  oxygenSaturation?: number;
  weight?: number;
  height?: number;
  notes?: string;
  recordedAt: string;
}

export const vitalsApi = {
  addVitals: (data: Partial<Vitals>) => apiClient.post('/vitals', data),
  getPatientVitals: (patientId: string) => apiClient.get(`/vitals/patient/${patientId}`),
  getLatestVitals: (patientId: string) => apiClient.get(`/vitals/patient/${patientId}/latest`),
};
