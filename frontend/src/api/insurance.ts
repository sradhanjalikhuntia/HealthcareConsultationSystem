import apiClient from './axios';

export interface InsuranceClaim {
  id: string;
  patientId: string;
  patientName: string;
  provider: string;
  policyNumber: string;
  claimAmount: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'PAID';
  notes?: string;
  createdAt: string;
}

export const insuranceApi = {
  getClaims: (page = 0, size = 20) => apiClient.get('/billing/insurance/claims', { params: { page, size } }),
  getPatientClaims: (patientId: string) => apiClient.get(`/billing/insurance/claims/patient/${patientId}`),
  createClaim: (data: Partial<InsuranceClaim>) => apiClient.post('/billing/insurance/claims', data),
  adjudicate: (id: string, data: { status: string; notes?: string }) => apiClient.put(`/billing/insurance/claims/${id}/adjudicate`, data),
};
