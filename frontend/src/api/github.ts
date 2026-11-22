import { apiClient } from './client';
import type { 
  GitHubConnection, 
  GitHubRepositoriesListResponse, 
  GitHubConnectedRepository, 
  GitHubBranch 
} from '../types/github';

export const githubApi = {
  getConnections: async (): Promise<GitHubConnection[]> => {
    const response = await apiClient.get<{ success: boolean; data: GitHubConnection[] }>('/github/connections');
    return response.data.data;
  },

  getInstallUrl: async (): Promise<{ installUrl: string; state: string }> => {
    const response = await apiClient.get<{ success: boolean; data: { installUrl: string; state: string } }>(
      '/github/connections/install-url'
    );
    return response.data.data;
  },

  getInstallationRepositories: async (
    connectionId: string, 
    page = 1, 
    perPage = 30
  ): Promise<GitHubRepositoriesListResponse> => {
    const response = await apiClient.get<{ success: boolean; data: GitHubRepositoriesListResponse }>(
      `/github/connections/${connectionId}/repositories?page=${page}&perPage=${perPage}`
    );
    return response.data.data;
  },

  connectRepository: async (
    connectionId: string, 
    githubRepoId: number
  ): Promise<GitHubConnectedRepository> => {
    const response = await apiClient.post<{ success: boolean; data: GitHubConnectedRepository }>(
      '/github/repositories',
      { connectionId, githubRepoId }
    );
    return response.data.data;
  },

  getConnectedRepositories: async (): Promise<GitHubConnectedRepository[]> => {
    const response = await apiClient.get<{ success: boolean; data: GitHubConnectedRepository[] }>('/github/repositories');
    return response.data.data;
  },

  getRepositoryBranches: async (repositoryId: string): Promise<GitHubBranch[]> => {
    const response = await apiClient.get<{ success: boolean; data: GitHubBranch[] }>(
      `/github/repositories/${repositoryId}/branches`
    );
    return response.data.data;
  },
};
