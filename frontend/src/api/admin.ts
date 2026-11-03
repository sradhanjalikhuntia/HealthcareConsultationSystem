import apiClient from './axios';
import type { ApiResponse } from '../types/auth';

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  enabled: boolean;
  createdAt?: string;
  lastLoginAt?: string;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  role?: string;
  enabled?: boolean;
}

export const adminApi = {
  getUsers: async (page = 0, size = 20) => {
    const response = await apiClient.get<ApiResponse<any>>(`/auth/users?page=${page}&size=${size}`);
    return response.data;
  },
  getUser: async (id: string) => {
    const response = await apiClient.get<ApiResponse<UserResponse>>(`/auth/users/${id}`);
    return response.data;
  },
  updateUser: async (id: string, data: UpdateUserRequest) => {
    const response = await apiClient.put<ApiResponse<UserResponse>>(`/auth/users/${id}`, data);
    return response.data;
  },
  approveUser: async (id: string) => {
    const response = await apiClient.post<ApiResponse<UserResponse>>(`/auth/users/${id}/approve`, {});
    return response.data;
  },
  getDoctors: async () => {
    const response = await apiClient.get<ApiResponse<UserResponse[]>>('/auth/doctors');
    return response.data;
  },
};
