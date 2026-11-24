import { apiClient } from './client';
import type { 
  JobQueueStatsResponse, 
  JobResponse 
} from '../types/job';

export const jobsApi = {
  // Get high-level background queue counters
  getQueueStats: async (): Promise<JobQueueStatsResponse> => {
    const response = await apiClient.get<{ success: boolean; data: JobQueueStatsResponse }>(
      '/jobs/stats'
    );
    return response.data.data;
  },

  // Get latest job for a deployment
  getLatestJobForDeployment: async (deploymentId: string): Promise<JobResponse> => {
    const response = await apiClient.get<{ success: boolean; data: JobResponse }>(
      `/deployments/${deploymentId}/job`
    );
    return response.data.data;
  },

  // Get all background jobs associated with a deployment
  getAllJobsForDeployment: async (deploymentId: string): Promise<JobResponse[]> => {
    const response = await apiClient.get<{ success: boolean; data: JobResponse[] }>(
      `/deployments/${deploymentId}/jobs`
    );
    return response.data.data;
  },
};
