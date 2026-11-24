export type AvailabilityStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'STOPPED' | 'UNKNOWN';

export interface ContainerMetrics {
  containerId: string;
  status: string;
  running: boolean;
  cpuPercent: number;
  memoryUsageFormatted: string;
  memoryUsageBytes: number;
  memoryLimitBytes: number;
  memoryPercent: number;
  pidsCount: number;
  restartCount: number;
  startedAt?: string;
  uptimeSeconds: number;
  collectedAt: string;
}

export interface DeploymentHealthReport {
  deploymentId?: string;
  projectId?: string;
  containerId?: string;
  hostPort?: number;
  availability: AvailabilityStatus;
  httpStatus?: number;
  httpProbePassed: boolean;
  containerRunning: boolean;
  consecutiveFailures: number;
  metrics?: ContainerMetrics;
  lastCheckedAt: string;
  details?: string;
}

export interface DeploymentTimelineEntry {
  deploymentId: string;
  status: string;
  trigger: string;
  commitSha?: string;
  commitBranch?: string;
  commitMessage?: string;
  commitAuthor?: string;
  dockerImageTag?: string;
  durationSeconds?: number;
  queuedAt?: string;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
  containerPort?: number;
}

export interface ProjectDeploymentHistoryResponse {
  projectId: string;
  projectName: string;
  totalDeployments: number;
  successfulDeployments: number;
  failedDeployments: number;
  cancelledDeployments: number;
  successRatePercent: number;
  averageDurationSeconds: number;
  activeDeployment?: DeploymentTimelineEntry;
  timeline: DeploymentTimelineEntry[];
  page: number;
  size: number;
  hasMore: boolean;
}
