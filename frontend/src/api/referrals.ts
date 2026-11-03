import apiClient from './axios';

export interface Referral {
  id: string;
  patientId: string;
  patientName?: string;
  referringDoctorId?: string;
  referringDoctorName?: string;
  referredDoctorId?: string;
  referredDoctorName?: string;
  targetSpecialty: string;
  priority?: 'ROUTINE' | 'URGENT' | 'EMERGENCY';
  clinicalSummary?: string;
  referralQuestion?: string;
  reason?: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'COMPLETED';
  acceptedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateReferralPayload {
  patientId: string;
  targetSpecialty: string;
  priority?: 'ROUTINE' | 'URGENT' | 'EMERGENCY';
  clinicalSummary?: string;
  referralQuestion?: string;
}

export const referralApi = {
  createReferral: (data: CreateReferralPayload) => apiClient.post('/referrals', data),
  getIncoming: () => apiClient.get('/referrals/incoming'),
  getOutgoing: () => apiClient.get('/referrals/outgoing'),
  getPatientReferrals: (patientId: string) => apiClient.get(`/referrals/patient/${patientId}`),
  updateStatus: (id: string, newStatus: string) => apiClient.put(`/referrals/${id}/status?status=${newStatus}`, { status: newStatus }),
};
