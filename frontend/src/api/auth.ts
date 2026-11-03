import apiClient from './axios';
import type { ApiResponse } from '../types/auth';

export interface AuthTokenResponse {
  token?: string;
  accessToken?: string;
  tokenType: string;
  expiresIn?: number;
  expiresAt?: string;
  userId?: string;
  username?: string;
  role?: string;
  createdAt?: string;
  lastLoginAt?: string;
}

export interface CaptchaResponse {
  captchaId: string;
  challengeSvg: string;
  expiresAt: string;
}

export interface SendOtpResponse {
  email: string;
  message: string;
  expiresInSeconds: number;
}

export interface UsernameCheckResponse {
  available: boolean;
  username: string;
  message: string;
  suggestions?: string[];
}

export const authApi = {
  getCaptcha: async () => {
    const response = await apiClient.get<ApiResponse<CaptchaResponse>>('/auth/captcha');
    return response.data;
  },
  checkUsername: async (username: string) => {
    const response = await apiClient.get<ApiResponse<UsernameCheckResponse>>('/auth/check-username', {
      params: { username },
    });
    return response.data;
  },
  login: async (identifier: string, password: string, captchaId?: string, captchaAnswer?: string) => {
    const response = await apiClient.post<ApiResponse<AuthTokenResponse>>('/auth/login', {
      username: identifier,
      email: identifier,
      password,
      captchaId,
      captchaAnswer,
    });
    return response.data;
  },
  sendOtp: async (email: string, username?: string, captchaId?: string, captchaAnswer?: string) => {
    const response = await apiClient.post<ApiResponse<SendOtpResponse>>('/auth/send-otp', {
      email,
      username,
      captchaId,
      captchaAnswer,
    });
    return response.data;
  },
  verifyOtp: async (email: string, otp: string) => {
    const response = await apiClient.post<ApiResponse<boolean>>('/auth/verify-otp', {
      email,
      otp,
    });
    return response.data;
  },
  register: async (data: {
    username: string;
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    role: string;
    doctorCode?: string;
    adminCode?: string;
    captchaId?: string;
    captchaAnswer?: string;
    otp: string;
  }) => {
    const response = await apiClient.post<ApiResponse<AuthTokenResponse>>('/auth/register', data);
    return response.data;
  },
  me: async () => {
    const response = await apiClient.get<ApiResponse<any>>('/auth/me');
    return response.data;
  },
};
