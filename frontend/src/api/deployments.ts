import { apiClient, tokenStorage } from './client';
import type { 
  Deployment, 
  DeploymentLogEntry 
} from '../types/deployment';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface TriggerDeploymentParams {
  branch?: string;
  commitSha?: string;
}

export const deploymentsApi = {
  // Trigger a new manual deployment for a project
  triggerDeployment: async (projectId: string, params?: TriggerDeploymentParams): Promise<Deployment> => {
    const response = await apiClient.post<{ success: boolean; data: Deployment }>(
      `/projects/${projectId}/deployments`,
      params || {}
    );
    return response.data.data;
  },

  // Get paginated deployments for a project
  getProjectDeployments: async (
    projectId: string, 
    page = 0, 
    size = 20
  ): Promise<PageResponse<Deployment>> => {
    const response = await apiClient.get<{ success: boolean; data: PageResponse<Deployment> }>(
      `/projects/${projectId}/deployments?page=${page}&size=${size}`
    );
    return response.data.data;
  },

  // Get details of a single deployment
  getDeployment: async (id: string): Promise<Deployment> => {
    const response = await apiClient.get<{ success: boolean; data: Deployment }>(
      `/deployments/${id}`
    );
    return response.data.data;
  },

  // Cancel an in-progress deployment
  cancelDeployment: async (id: string): Promise<Deployment> => {
    const response = await apiClient.post<{ success: boolean; data: Deployment }>(
      `/deployments/${id}/cancel`
    );
    return response.data.data;
  },

  // Retry a deployment
  retryDeployment: async (id: string): Promise<Deployment> => {
    const response = await apiClient.post<{ success: boolean; data: Deployment }>(
      `/deployments/${id}/retry`
    );
    return response.data.data;
  },

  // Get historical logs
  getHistoricalLogs: async (
    id: string, 
    stage?: string, 
    since?: number
  ): Promise<{ logs: DeploymentLogEntry[]; totalLines: number; hasMore: boolean }> => {
    const params = new URLSearchParams();
    if (stage) params.append('stage', stage);
    if (since !== undefined) params.append('since', since.toString());
    const query = params.toString() ? `?${params.toString()}` : '';
    const response = await apiClient.get<{ 
      success: boolean; 
      data: { logs: DeploymentLogEntry[]; totalLines: number; hasMore: boolean } 
    }>(`/deployments/${id}/logs/history${query}`);
    return response.data.data;
  },

  // Download raw log URL
  getDownloadLogsUrl: (id: string): string => {
    return `/api/v1/deployments/${id}/logs/download`;
  },

  // Stream logs in real-time via SSE
  streamLogs: (
    id: string,
    callbacks: {
      onConnected?: (data: { deploymentId: string; status?: string; message: string }) => void;
      onLog?: (log: DeploymentLogEntry) => void;
      onStatus?: (status: string, message: string) => void;
      onComplete?: (finalStatus: string) => void;
      onError?: (err: Event | Error) => void;
    }
  ): (() => void) => {
    const token = tokenStorage.getAccessToken();
    const url = `/api/v1/deployments/${id}/logs/stream${token ? `?token=${encodeURIComponent(token)}` : ''}`;
    
    const eventSource = new EventSource(url);

    eventSource.addEventListener('connected', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data);
        callbacks.onConnected?.(data);
      } catch {
        // silent
      }
    });

    eventSource.addEventListener('log', (e: MessageEvent) => {
      try {
        const log = JSON.parse(e.data);
        callbacks.onLog?.(log);
      } catch {
        // silent
      }
    });

    eventSource.addEventListener('status', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data);
        callbacks.onStatus?.(data.status, data.message);
      } catch {
        // silent
      }
    });

    eventSource.addEventListener('complete', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data);
        callbacks.onComplete?.(data.finalStatus);
      } catch {
        // silent
      }
      eventSource.close();
    });

    eventSource.onerror = (err) => {
      callbacks.onError?.(err);
    };

    return () => {
      eventSource.close();
    };
  },
};
