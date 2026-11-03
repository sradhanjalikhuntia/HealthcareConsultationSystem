import apiClient from './axios';
import { PatientRequest, PatientResponse, Page } from '../types/patient';
import { ApiResponse } from '../types/auth';

export const patientApi = {
  getPatients: async (page = 0, size = 10, query?: string, status?: string) => {
    let url = `/patients?page=${page}&size=${size}&sort=lastName`;
    if (query) url += `&q=${encodeURIComponent(query)}`;
    if (status) url += `&status=${status}`;
    
    const response = await apiClient.get<ApiResponse<Page<PatientResponse>>>(url);
    return response.data;
  },
  
  getPatientSummary: async () => {
    const response = await apiClient.get<ApiResponse<Record<string, any>>>('/patients/summary');
    return response.data;
  },

  getPatient: async (id: string) => {
    const response = await apiClient.get<ApiResponse<PatientResponse>>(`/patients/${id}`);
    return response.data;
  },

  createPatient: async (data: PatientRequest) => {
    const response = await apiClient.post<ApiResponse<PatientResponse>>('/patients', data);
    return response.data;
  },

  updatePatient: async (id: string, data: PatientRequest) => {
    const response = await apiClient.put<ApiResponse<PatientResponse>>(`/patients/${id}`, data);
    return response.data;
  },

  deactivatePatient: async (id: string) => {
    const response = await apiClient.delete<ApiResponse<void>>(`/patients/${id}`);
    return response.data;
  },

  reactivatePatient: async (id: string) => {
    const response = await apiClient.post<ApiResponse<PatientResponse>>(`/patients/${id}/activate`, {});
    return response.data;
  }
};
