import { apiClient } from './client';
import type { 
  ContainerInfo, 
  ContainerLogsResponse 
} from '../types/container';

export const containersApi = {
  // Inspect a container for a deployment
  inspectContainer: async (deploymentId: string): Promise<ContainerInfo> => {
    const response = await apiClient.get<{ success: boolean; data: ContainerInfo }>(
      `/deployments/${deploymentId}/container`
    );
    return response.data.data;
  },

  // Gracefully restart container
  restartContainer: async (deploymentId: string): Promise<ContainerInfo> => {
    const response = await apiClient.post<{ success: boolean; message: string; data: ContainerInfo }>(
      `/deployments/${deploymentId}/container/restart`
    );
    return response.data.data;
  },

  // Stop container
  stopContainer: async (deploymentId: string): Promise<ContainerInfo> => {
    const response = await apiClient.post<{ success: boolean; message: string; data: ContainerInfo }>(
      `/deployments/${deploymentId}/container/stop`
    );
    return response.data.data;
  },

  // Retrieve raw stdout/stderr logs directly from Docker engine
  getContainerLogs: async (deploymentId: string, lines = 100): Promise<ContainerLogsResponse> => {
    const response = await apiClient.get<{ success: boolean; data: ContainerLogsResponse }>(
      `/deployments/${deploymentId}/container/logs?lines=${lines}`
    );
    return response.data.data;
  },
};
