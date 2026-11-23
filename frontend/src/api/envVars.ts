import { apiClient } from './client';
import type { 
  EnvironmentVariable, 
  UpsertEnvVarRequest, 
  BatchEnvVarsRequest 
} from '../types/envVar';

export const envVarsApi = {
  getEnvVars: async (projectId: string): Promise<EnvironmentVariable[]> => {
    const response = await apiClient.get<{ success: boolean; data: EnvironmentVariable[] }>(
      `/projects/${projectId}/env`
    );
    return response.data.data;
  },

  getEnvVar: async (projectId: string, key: string): Promise<EnvironmentVariable> => {
    const response = await apiClient.get<{ success: boolean; data: EnvironmentVariable }>(
      `/projects/${projectId}/env/${encodeURIComponent(key)}`
    );
    return response.data.data;
  },

  upsertEnvVar: async (
    projectId: string, 
    request: UpsertEnvVarRequest
  ): Promise<EnvironmentVariable> => {
    const response = await apiClient.post<{ success: boolean; data: EnvironmentVariable }>(
      `/projects/${projectId}/env`,
      request
    );
    return response.data.data;
  },

  batchUpsertEnvVars: async (
    projectId: string, 
    request: BatchEnvVarsRequest
  ): Promise<EnvironmentVariable[]> => {
    const response = await apiClient.post<{ success: boolean; data: EnvironmentVariable[] }>(
      `/projects/${projectId}/env/batch`,
      request
    );
    return response.data.data;
  },

  deleteEnvVar: async (projectId: string, key: string): Promise<void> => {
    await apiClient.delete(`/projects/${projectId}/env/${encodeURIComponent(key)}`);
  },
};
