import React, { createContext, useContext, useState, useEffect } from 'react';
import apiClient from '../api/axios';

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  enabled: boolean;
  linkedPatientId?: string;
  doctorCode?: string;
  createdAt?: string;
  lastLoginAt?: string;
}

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  loading: boolean;
  tokenExpiresAt: number | null;
  login: (token: string) => Promise<void>;
  logout: () => void;
}

/** Decode JWT payload and extract expiry timestamp (ms) */
const decodeTokenExpiry = (token: string): number | null => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
};

const AuthContext = createContext<AuthContextType>({
  user: null, isAuthenticated: false, loading: true,
  tokenExpiresAt: null,
  login: async () => {}, logout: () => {},
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [tokenExpiresAt, setTokenExpiresAt] = useState<number | null>(() => {
    const stored = localStorage.getItem('tokenExpiresAt');
    return stored ? parseInt(stored, 10) : null;
  });

  const fetchUser = async () => {
    try {
      const res = await apiClient.get('/auth/me');
      setUser(res.data.data);
    } catch {
      setUser(null);
      localStorage.removeItem('token');
      localStorage.removeItem('tokenExpiresAt');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (localStorage.getItem('token')) fetchUser();
    else setLoading(false);
  }, []);

  const login = async (token: string) => {
    localStorage.setItem('token', token);
    const expiry = decodeTokenExpiry(token);
    if (expiry) {
      localStorage.setItem('tokenExpiresAt', expiry.toString());
      setTokenExpiresAt(expiry);
    }
    await fetchUser();
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('tokenExpiresAt');
    setUser(null);
    setTokenExpiresAt(null);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, loading, tokenExpiresAt, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
