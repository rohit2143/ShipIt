export interface JobQueueStatsResponse {
  pendingCount: number;
  runningCount: number;
  completedCount: number;
  failedCount: number;
  cancelledCount: number;
  totalCount: number;
}

export interface JobResponse {
  id: string;
  deploymentId: string;
  status: string;
  workerId?: string;
  attemptCount: number;
  maxAttempts: number;
  scheduledAt?: string;
  lockedAt?: string;
  finishedAt?: string;
  lastHeartbeat?: string;
  errorMessage?: string;
}
