import React, { createContext, useContext, useState, useEffect } from 'react';
import type { User, LoginRequest, RegisterRequest, OAuthLoginRequest } from '../types/auth';
import { authApi } from '../api/auth';
import { tokenStorage } from '../api/client';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  loginWithGitHub: (data: OAuthLoginRequest) => Promise<void>;
  loginWithGoogle: (data: OAuthLoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = async () => {
    try {
      const currentUser = await authApi.getCurrentUser();
      setUser(currentUser);
    } catch {
      setUser(null);
      tokenStorage.clearTokens();
    }
  };

  useEffect(() => {
    let isMounted = true;

    const initializeAuth = async () => {
      const token = tokenStorage.getAccessToken();
      if (!token) {
        if (isMounted) setIsLoading(false);
        return;
      }

      try {
        const currentUser = await authApi.getCurrentUser();
        if (isMounted) setUser(currentUser);
      } catch {
        if (isMounted) {
          setUser(null);
          tokenStorage.clearTokens();
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    initializeAuth();

    const handleAuthExpired = () => {
      setUser(null);
      tokenStorage.clearTokens();
    };

    window.addEventListener('shipit:auth_expired', handleAuthExpired);
    return () => {
      isMounted = false;
      window.removeEventListener('shipit:auth_expired', handleAuthExpired);
    };
  }, []);

  const login = async (credentials: LoginRequest) => {
    setIsLoading(true);
    try {
      const authData = await authApi.login(credentials);
      setUser(authData.user);
    } finally {
      setIsLoading(false);
    }
  };

  const loginWithGitHub = async (data: OAuthLoginRequest) => {
    setIsLoading(true);
    try {
      const authData = await authApi.loginWithGitHub(data);
      setUser(authData.user);
    } finally {
      setIsLoading(false);
    }
  };

  const loginWithGoogle = async (data: OAuthLoginRequest) => {
    setIsLoading(true);
    try {
      const authData = await authApi.loginWithGoogle(data);
      setUser(authData.user);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data: RegisterRequest) => {
    setIsLoading(true);
    try {
      const authData = await authApi.register(data);
      setUser(authData.user);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        loginWithGitHub,
        loginWithGoogle,
        register,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
