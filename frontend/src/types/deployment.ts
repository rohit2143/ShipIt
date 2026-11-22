export type DeploymentStatus =
  | 'QUEUED'
  | 'CLONING'
  | 'DETECTING'
  | 'BUILDING'
  | 'STARTING'
  | 'HEALTH_CHECKING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED';

export type DeploymentTrigger = 'MANUAL' | 'WEBHOOK' | 'RETRY';

export type DeploymentStage =
  | 'QUEUED'
  | 'CLONING'
  | 'DETECTING'
  | 'BUILDING'
  | 'STARTING'
  | 'HEALTH_CHECKING';

export interface Deployment {
  id: string;
  projectId: string;
  projectName?: string;
  status: DeploymentStatus;
  trigger: DeploymentTrigger;
  commitSha?: string;
  commitAuthor?: string;
  commitMessage?: string;
  commitBranch?: string;
  branch?: string;
  queuedAt?: string;
  startedAt?: string;
  finishedAt?: string;
  durationSeconds?: number;
  errorMessage?: string;
  dockerImageTag?: string;
  containerId?: string;
  containerPort?: number;
  allocatedPort?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DeploymentLogEntry {
  id?: string;
  deploymentId?: string;
  sequenceNumber: number;
  stage: DeploymentStage | string;
  source?: 'SYSTEM' | 'BUILD' | 'CONTAINER' | string;
  message: string;
  timestamp: string;
}

export interface ContainerMetrics {
  cpuPercentage: number;
  memoryUsageBytes: number;
  memoryLimitBytes: number;
  memoryUsagePercentage: number;
  pidsCount: number;
  restartCount: number;
  uptimeSeconds: number;
}

export interface DeploymentHealthReport {
  deploymentId: string;
  status: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'UNKNOWN';
  httpStatusCode?: number;
  responseLatencyMs?: number;
  consecutiveFailures: number;
  lastCheckedAt: string;
  metrics?: ContainerMetrics;
}
