import apiClient from './axios';

export interface LabOrder {
  id: string;
  patientId: string;
  patientName: string;
  doctorId?: string;
  testName: string;
  category?: string;
  priority?: string;
  status: 'PENDING' | 'SAMPLE_COLLECTED' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED';
  result?: string;
  cost?: number;
  orderDate: string;
  completedDate?: string;
}

export const labApi = {
  getOrders: (page = 0, size = 20) => apiClient.get('/billing/lab/orders', { params: { page, size } }),
  getPatientOrders: (patientId: string) => apiClient.get(`/billing/lab/orders/patient/${patientId}`),
  createOrder: (data: Partial<LabOrder>) => apiClient.post('/billing/lab/orders', data),
  updateResult: (id: string, data: { result?: string; resultSummary?: string; referenceRange?: string; abnormalFlag?: string; resultFileUrl?: string; status?: string }) =>
    apiClient.put(`/billing/lab/orders/${id}/results`, {
      resultSummary: data.resultSummary || data.result || 'Analysis completed and verified.',
      referenceRange: data.referenceRange || 'Standard reference range',
      abnormalFlag: data.abnormalFlag || 'NORMAL',
      resultFileUrl: data.resultFileUrl,
    }),
};
