import apiClient from './axios';
import type { Page } from '../types/patient';
import type { ApiResponse } from '../types/auth';

export interface AppointmentResponse {
  appointmentId: string;
  patientId: string;
  uniquePatientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  reason: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
  notes: string;
  billingGenerated: boolean;
  createdAt: string;
}

export interface AppointmentRequest {
  patientId: string;
  doctorId: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  reason: string;
  notes?: string;
}

export interface AppointmentStatsResponse {
  totalCount: number;
  scheduledCount: number;
  completedCount: number;
  cancelledCount: number;
}

export const appointmentApi = {
  getAll: async (page = 0, size = 10, status?: string, patientId?: string) => {
    let url = `/appointments?page=${page}&size=${size}&sort=appointmentDate`;
    if (status) url += `&status=${status}`;
    if (patientId) url += `&patientId=${patientId}`;
    const response = await apiClient.get<ApiResponse<Page<AppointmentResponse>>>(url);
    return response.data;
  },
  getById: async (id: string) => {
    const response = await apiClient.get<ApiResponse<AppointmentResponse>>(`/appointments/${id}`);
    return response.data;
  },
  getStats: async () => {
    const response = await apiClient.get<ApiResponse<AppointmentStatsResponse>>('/appointments/stats/summary');
    return response.data;
  },
  create: async (data: AppointmentRequest) => {
    const response = await apiClient.post<ApiResponse<AppointmentResponse>>('/appointments', data);
    return response.data;
  },
  reschedule: async (id: string, data: AppointmentRequest) => {
    const response = await apiClient.put<ApiResponse<AppointmentResponse>>(`/appointments/${id}`, data);
    return response.data;
  },
  getAvailability: async (doctorId: string, date: string) => {
    const response = await apiClient.get<ApiResponse<AppointmentResponse[]>>(`/appointments/doctor/${doctorId}/availability?date=${date}`);
    return response.data;
  },
  cancel: async (id: string) => {
    const response = await apiClient.post<ApiResponse<AppointmentResponse>>(`/appointments/${id}/cancel`, {});
    return response.data;
  },
  updateStatus: async (id: string, status: string, notes?: string) => {
    const response = await apiClient.post<ApiResponse<AppointmentResponse>>(`/appointments/${id}/status`, { status, notes });
    return response.data;
  },
};
