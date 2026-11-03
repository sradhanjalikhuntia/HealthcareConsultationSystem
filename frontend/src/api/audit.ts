import apiClient from './axios';
import type { ApiResponse } from '../types/auth';
import type { Page } from '../types/patient';

export interface AuditLog {
  id: string;
  actorId?: string;
  actorUsername?: string;
  actorRole?: string;
  action: string;
  resourceType?: string;
  resourceId?: string;
  details?: string;
  ipAddress?: string;
  eventTimestamp?: string;
  createdAt?: string;
}

export const auditApi = {
  getLogs: async (patientId?: string, page = 0, size = 50) => {
    const response = await apiClient.get<ApiResponse<Page<AuditLog>>>('/patients/audit-logs', {
      params: { patientId, page, size }
    });
    return response.data;
  },
};

