import apiClient from './axios';
import { Page } from '../types/patient';
import { ApiResponse } from '../types/auth';

export interface BillItem {
  id: string;
  description: string;
  amount: number;
  quantity: number;
}

export interface BillResponse {
  id: string;
  patientId: string;
  patientName: string;
  uniquePatientId?: string;
  appointmentId?: string;
  consultationFee?: number;
  medicineCharges?: number;
  labCharges?: number;
  wardCharges?: number;
  otherCharges?: number;
  subtotalAmount?: number;
  discountPercentage?: number;
  discountAmount?: number;
  taxPercentage?: number;
  taxAmount?: number;
  totalAmount: number;
  paymentStatus: 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED' | 'CANCELLED';
  notes?: string;
  items?: BillItem[];
  createdAt: string;
  updatedAt: string;
  paidAt?: string;
}

export interface BillRequest {
  patientId: string;
  uniquePatientId?: string;
  appointmentId?: string;
  consultationFee: number;
  medicineCharges: number;
  labCharges: number;
  wardCharges?: number;
  otherCharges: number;
  discountPercentage: number;
  taxPercentage: number;
  notes?: string;
}

export interface BillingSummaryResponse {
  totalRevenue: number;
  pendingAmount: number;
  totalInvoices: number;
  paidInvoices: number;
  pendingInvoices: number;
  revenueTrend?: { name: string; revenue: number }[];
}

export interface PaymentRequest {
  paymentMethod: string;
  amount: number;
  transactionReference?: string;
  paymentToken?: string;
  notes?: string;
}

export interface PaymentReceiptResponse {
  receiptId: string;
  billId: string;
  patientName: string;
  amountPaid: number;
  paymentMethod: string;
  date: string;
  transactionReference?: string;
}

export interface ChargeLineItem {
  type: string;
  description: string;
  amount: number;
  date: string;
}

export interface UnbilledChargesSummary {
  patientId: string;
  consultationFee: number;
  medicineCharges: number;
  labCharges: number;
  wardCharges: number;
  otherCharges: number;
  totalCharges: number;
  items: ChargeLineItem[];
}

export const billingApi = {
  getAllBills: async (page = 0, size = 10, status?: string) => {
    let url = `/billing?page=${page}&size=${size}&sortBy=createdAt&direction=desc`;
    if (status) url += `&status=${status}`;
    const response = await apiClient.get<ApiResponse<Page<BillResponse>>>(url);
    return response.data;
  },

  getBillsByPatient: async (patientId: string) => {
    const response = await apiClient.get<ApiResponse<BillResponse[]>>(`/billing/patient/${patientId}`);
    return response.data;
  },

  getBillById: async (id: string) => {
    const response = await apiClient.get<ApiResponse<BillResponse>>(`/billing/${id}`);
    return response.data;
  },

  getBillingSummary: async () => {
    const response = await apiClient.get<ApiResponse<BillingSummaryResponse>>('/billing/stats/summary');
    return response.data;
  },

  createBill: async (data: BillRequest) => {
    const response = await apiClient.post<ApiResponse<BillResponse>>('/billing', data);
    return response.data;
  },

  processPayment: async (id: string, data: PaymentRequest) => {
    const response = await apiClient.post<ApiResponse<PaymentReceiptResponse>>(`/billing/${id}/pay`, data);
    return response.data;
  },

  getReceipt: async (id: string) => {
    const response = await apiClient.get<ApiResponse<PaymentReceiptResponse>>(`/billing/${id}/receipt`);
    return response.data;
  },

  getUnbilledSummary: async (patientId: string) => {
    const response = await apiClient.get<ApiResponse<UnbilledChargesSummary>>(`/billing/patient/${patientId}/unbilled-summary`);
    return response.data;
  },

  generateAutoBill: async (patientId: string) => {
    const response = await apiClient.post<ApiResponse<BillResponse>>(`/billing/patient/${patientId}/auto-bill`);
    return response.data;
  },

  generateBillForAppointment: async (data: {
    patientId: string;
    appointmentId: string;
    consultationFee?: number;
    notes?: string;
  }) => {
    const response = await apiClient.post<ApiResponse<BillResponse>>('/billing/generate', data);
    return response.data;
  }
};
