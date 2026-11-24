export type ContainerStatus = 'STARTING' | 'RUNNING' | 'STOPPING' | 'STOPPED' | 'FAILED' | 'REMOVED';

export interface ContainerInfo {
  containerId: string;
  containerName: string;
  deploymentId: string;
  imageTag: string;
  status: ContainerStatus;
  running: boolean;
  hostPort: number;
  containerPort: number;
  startedAt?: string;
  finishedAt?: string;
  exitCode?: number;
  healthStatus?: string;
  ipAddress?: string;
}

export interface ContainerLogsResponse {
  deploymentId: string;
  containerId: string;
  lines: number;
  logs: string;
}
