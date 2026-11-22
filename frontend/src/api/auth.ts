import { apiClient, tokenStorage } from './client';
import type { 
  AuthResponse, 
  LoginRequest, 
  RegisterRequest, 
  User, 
  TokenResponse,
  OAuthLoginRequest
} from '../types/auth';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<{ success: boolean; data: AuthResponse }>(
      '/auth/login', 
      credentials
    );
    const authData = response.data.data;
    tokenStorage.setTokens(authData.tokens.accessToken, authData.tokens.refreshToken);
    return authData;
  },

  loginWithGitHub: async (data: OAuthLoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<{ success: boolean; data: AuthResponse }>(
      '/auth/oauth/github',
      data
    );
    const authData = response.data.data;
    tokenStorage.setTokens(authData.tokens.accessToken, authData.tokens.refreshToken);
    return authData;
  },

  getGitHubAuthUrl: async (): Promise<string> => {
    try {
      const response = await apiClient.get<{ success: boolean; data: { url: string } }>(
        '/auth/oauth/github/url'
      );
      if (response.data?.data?.url) {
        return response.data.data.url;
      }
    } catch {
      // Endpoint might not exist on older backend builds, fallback to frontend config
    }

    const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID;
    if (!clientId) {
      throw new Error(
        'GitHub OAuth client ID is not configured. Set VITE_GITHUB_CLIENT_ID in your frontend .env file or use email authentication.'
      );
    }
    const redirectUri = window.location.origin + '/auth/callback/github';
    return `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(
      redirectUri
    )}&scope=read:user,user:email`;
  },

  loginWithGoogle: async (data: OAuthLoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<{ success: boolean; data: AuthResponse }>(
      '/auth/oauth/google',
      data
    );
    const authData = response.data.data;
    tokenStorage.setTokens(authData.tokens.accessToken, authData.tokens.refreshToken);
    return authData;
  },

  getGoogleAuthUrl: async (): Promise<string> => {
    try {
      const response = await apiClient.get<{ success: boolean; data: { url: string } }>(
        '/auth/oauth/google/url'
      );
      if (response.data?.data?.url) {
        return response.data.data.url;
      }
    } catch {
      // Endpoint might not exist on older backend builds, fallback to frontend config
    }

    const clientId =
      import.meta.env.VITE_GOOGLE_CLIENT_ID ||
      '78964977858-fmiilinvlrp4bb39ee6o7qs77eeh5jn1.apps.googleusercontent.com';
    if (!clientId) {
      throw new Error(
        'Google OAuth client ID is not configured. Set VITE_GOOGLE_CLIENT_ID in your frontend .env file or use email authentication.'
      );
    }
    const redirectUri = window.location.origin + '/auth/callback/google';
    return `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${encodeURIComponent(
      redirectUri
    )}&response_type=code&scope=openid%20email%20profile&access_type=offline&prompt=select_account`;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<{ success: boolean; data: AuthResponse }>(
      '/auth/register', 
      data
    );
    const authData = response.data.data;
    tokenStorage.setTokens(authData.tokens.accessToken, authData.tokens.refreshToken);
    return authData;
  },

  logout: async (): Promise<void> => {
    const refreshToken = tokenStorage.getRefreshToken();
    try {
      if (refreshToken) {
        await apiClient.post('/auth/logout', { refreshToken });
      }
    } finally {
      tokenStorage.clearTokens();
    }
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<{ success: boolean; data: User }>('/users/me');
    return response.data.data;
  },

  refreshSession: async (refreshToken: string): Promise<TokenResponse> => {
    const response = await apiClient.post<{ success: boolean; data: TokenResponse }>(
      '/auth/refresh',
      { refreshToken }
    );
    const tokens = response.data.data;
    tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
    return tokens;
  },
};
