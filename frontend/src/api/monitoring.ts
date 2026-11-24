import { apiClient } from './client';
import type { 
  DeploymentHealthReport, 
  DeploymentTimelineEntry, 
  ProjectDeploymentHistoryResponse 
} from '../types/monitoring';

export const monitoringApi = {
  // Get active health report & metrics for a project's current deployment
  getProjectActiveHealth: async (projectId: string): Promise<DeploymentHealthReport> => {
    const response = await apiClient.get<{ success: boolean; data: DeploymentHealthReport }>(
      `/projects/${projectId}/health`
    );
    return response.data.data;
  },

  // Get health report & metrics for a specific deployment
  getDeploymentHealth: async (deploymentId: string): Promise<DeploymentHealthReport> => {
    const response = await apiClient.get<{ success: boolean; data: DeploymentHealthReport }>(
      `/deployments/${deploymentId}/health`
    );
    return response.data.data;
  },

  // Get aggregated historical rollout statistics and timeline
  getProjectDeploymentHistory: async (
    projectId: string, 
    page = 0, 
    size = 20
  ): Promise<ProjectDeploymentHistoryResponse> => {
    const response = await apiClient.get<{ success: boolean; data: ProjectDeploymentHistoryResponse }>(
      `/projects/${projectId}/deployment-history?page=${page}&size=${size}`
    );
    return response.data.data;
  },

  // Get detailed execution timeline for a single deployment
  getDeploymentTimeline: async (deploymentId: string): Promise<DeploymentTimelineEntry> => {
    const response = await apiClient.get<{ success: boolean; data: DeploymentTimelineEntry }>(
      `/deployments/${deploymentId}/timeline`
    );
    return response.data.data;
  },
};
