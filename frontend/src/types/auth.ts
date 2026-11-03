export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  active: boolean;
  createdAt?: string;
  lastLoginAt?: string;
}

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

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}
