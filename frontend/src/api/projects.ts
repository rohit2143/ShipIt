import { apiClient } from './client';
import type { 
  ProjectSummary, 
  ProjectDetail, 
  CreateProjectRequest, 
  UpdateProjectRequest 
} from '../types/project';

export const projectsApi = {
  getProjects: async (): Promise<ProjectSummary[]> => {
    const response = await apiClient.get<{ success: boolean; data: ProjectSummary[] }>('/projects');
    return response.data.data;
  },

  getProject: async (id: string): Promise<ProjectDetail> => {
    const response = await apiClient.get<{ success: boolean; data: ProjectDetail }>(`/projects/${id}`);
    return response.data.data;
  },

  createProject: async (request: CreateProjectRequest): Promise<ProjectDetail> => {
    const response = await apiClient.post<{ success: boolean; data: ProjectDetail }>('/projects', request);
    return response.data.data;
  },

  updateProject: async (id: string, request: UpdateProjectRequest): Promise<ProjectDetail> => {
    const response = await apiClient.put<{ success: boolean; data: ProjectDetail }>(`/projects/${id}`, request);
    return response.data.data;
  },

  deleteProject: async (id: string): Promise<void> => {
    await apiClient.delete(`/projects/${id}`);
  },
};
