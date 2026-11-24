import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Activity, 
  Server, 
  Cpu, 
  HardDrive, 
  RefreshCw, 
  Square, 
  ExternalLink, 
  Terminal, 
  ShieldCheck, 
  CheckCircle2, 
  Clock, 
  FolderGit2, 
  Zap,
  Box,
  AlertCircle,
  FileText
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { monitoringApi } from '../api/monitoring';
import { containersApi } from '../api/containers';
import type { DeploymentHealthReport } from '../types/monitoring';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../context/ToastContext';

export const ContainersPage: React.FC = () => {
  const { activeProject, projects, selectProject } = useProjects();
  const navigate = useNavigate();
  const toast = useToast();

  const [healthReport, setHealthReport] = useState<DeploymentHealthReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRestarting, setIsRestarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);

  // Container Logs Modal State
  const [isLogsOpen, setIsLogsOpen] = useState(false);
  const [containerLogs, setContainerLogs] = useState('');
  const [isLoadingLogs, setIsLoadingLogs] = useState(false);

  const fetchHealth = useCallback(async (silent = false) => {
    if (!activeProject) return;
    if (!silent) setIsLoading(true);
    try {
      const report = await monitoringApi.getProjectActiveHealth(activeProject.id);
      setHealthReport(report);
    } catch {
      // If 404 or no active deployment, report is null
      setHealthReport(null);
    } finally {
      if (!silent) setIsLoading(false);
    }
  }, [activeProject?.id]);

  useEffect(() => {
    fetchHealth(false);
    const interval = setInterval(() => {
      fetchHealth(true);
    }, 6000);
    return () => clearInterval(interval);
  }, [fetchHealth]);

  const deploymentId = healthReport?.deploymentId;

  const handleRestart = async () => {
    if (!deploymentId) {
      toast.error('No active deployment available to restart.');
      return;
    }

    setIsRestarting(true);
    try {
      await containersApi.restartContainer(deploymentId);
      toast.success('Docker container restarted successfully.');
      await fetchHealth(false);
    } catch {
      toast.error('Failed to restart container.');
    } finally {
      setIsRestarting(false);
    }
  };

  const handleStop = async () => {
    if (!deploymentId) {
      toast.error('No active deployment available to stop.');
      return;
    }

    if (!window.confirm('Are you sure you want to stop this container? Ingress traffic will receive 502/503 errors until restarted.')) {
      return;
    }

    setIsStopping(true);
    try {
      await containersApi.stopContainer(deploymentId);
      toast.info('Docker container stopped.');
      await fetchHealth(false);
    } catch {
      toast.error('Failed to stop container.');
    } finally {
      setIsStopping(false);
    }
  };

  const handleOpenLogs = async () => {
    if (!deploymentId) return;
    setIsLogsOpen(true);
    setIsLoadingLogs(true);
    try {
      const res = await containersApi.getContainerLogs(deploymentId, 150);
      setContainerLogs(res.logs || 'No container output recorded yet.');
    } catch {
      setContainerLogs('Failed to retrieve container logs from Docker engine daemon.');
    } finally {
      setIsLoadingLogs(false);
    }
  };

  const formatUptime = (seconds?: number) => {
    if (!seconds || seconds <= 0) return '0s';
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    if (hrs > 0) return `${hrs}h ${mins}m`;
    if (mins > 0) return `${mins}m ${secs}s`;
    return `${secs}s`;
  };

  if (!activeProject) {
    return (
      <Card className="p-12 text-center space-y-4">
        <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
          <FolderGit2 className="w-7 h-7" />
        </div>
        <div>
          <h3 className="text-base font-semibold text-white">No Project Selected</h3>
          <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
            Select an active project from the dropdown to inspect its Docker container status, memory limits, and health probes.
          </p>
        </div>
      </Card>
    );
  }

  const isRunning = healthReport?.containerRunning ?? false;
  const metrics = healthReport?.metrics;
  const containerId = healthReport?.containerId || 'Not Allocated';
  const hostPort = healthReport?.hostPort || activeProject.port;
  const availability = healthReport?.availability || 'UNKNOWN';

  return (
    <div className="space-y-6">
      {/* Top Header & Project Selector */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Badge variant="cyan" dot={true}>
              Docker Engine Live
            </Badge>
            <span className="text-xs text-slate-500 font-mono">Module 7 & 9 Connected</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Active Containers & Telemetry
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Real-time Docker daemon virtualization, actuator probes, and hardware utilization metrics.
          </p>
        </div>

        {projects.length > 1 && (
          <div className="flex items-center gap-2 bg-[#111629] border border-[#252D46] rounded-xl px-3 py-1.5 text-xs shadow-sm">
            <Box className="w-4 h-4 text-[#8B5CF6]" />
            <select
              value={activeProject.id}
              onChange={(e) => selectProject(e.target.value)}
              className="bg-transparent text-white font-medium focus:outline-none cursor-pointer pr-2"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id} className="bg-[#0D1120] text-slate-200">
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {isLoading && !healthReport ? (
        <Card className="p-8 space-y-4">
          <Skeleton className="h-8 w-1/3" />
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        </Card>
      ) : !healthReport || !healthReport.deploymentId ? (
        /* Empty State: No live deployment */
        <Card className="p-12 text-center space-y-4">
          <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
            <Server className="w-7 h-7" />
          </div>
          <div className="space-y-1">
            <h3 className="text-base font-semibold text-white">No Active Container</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Project &quot;<span className="text-white font-semibold">{activeProject.name}</span>&quot; does not currently have a running container deployment.
            </p>
          </div>
          <Button
            onClick={() => navigate('/deployments')}
            variant="primary"
            size="md"
            leftIcon={<Zap className="w-4 h-4" />}
          >
            Go to Deployments
          </Button>
        </Card>
      ) : (
        /* Main Container Control Deck */
        <Card className="relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#8B5CF6] to-transparent" />
          <CardHeader className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-4 border-b border-[#252D46]">
            <div className="space-y-1.5">
              <div className="flex flex-wrap items-center gap-2.5">
                <span
                  className={`w-2.5 h-2.5 rounded-full ${
                    isRunning
                      ? 'bg-[#10B981] shadow-[0_0_8px_rgba(16,185,129,0.7)] animate-pulse'
                      : 'bg-rose-500 shadow-[0_0_8px_rgba(239,68,68,0.7)]'
                  }`}
                />
                <h2 className="text-base font-bold text-white font-mono tracking-tight">
                  shipit-c-{activeProject.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}-{hostPort || '8080'}
                </h2>
                <span className="px-2 py-0.5 rounded text-[10px] bg-[#080B14] font-mono border border-[#252D46] text-[#A78BFA]">
                  ID: {containerId.substring(0, 12)}
                </span>
                <Badge
                  variant={
                    availability === 'HEALTHY'
                      ? 'success'
                      : availability === 'DEGRADED'
                      ? 'warning'
                      : availability === 'STOPPED'
                      ? 'neutral'
                      : 'danger'
                  }
                >
                  {availability}
                </Badge>
              </div>
              <p className="text-xs text-slate-400 font-mono">
                Deployment:{' '}
                <span className="text-slate-200">
                  DEP-{deploymentId?.substring(0, 8)}
                </span>{' '}
                • Restarts:{' '}
                <span className="text-[#A78BFA] font-bold">
                  {metrics?.restartCount ?? 0}
                </span>{' '}
                • Consecutive Failures:{' '}
                <span className="text-slate-200">
                  {healthReport.consecutiveFailures}
                </span>
              </p>
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto">
              <Button
                onClick={handleRestart}
                isLoading={isRestarting}
                variant="secondary"
                size="sm"
                leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isRestarting ? 'animate-spin' : ''}`} />}
              >
                Restart
              </Button>

              {isRunning ? (
                <Button
                  onClick={handleStop}
                  isLoading={isStopping}
                  variant="danger"
                  size="sm"
                  leftIcon={<Square className="w-3.5 h-3.5" />}
                >
                  Stop
                </Button>
              ) : (
                <Button
                  onClick={handleRestart}
                  variant="primary"
                  size="sm"
                  leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
                >
                  Start
                </Button>
              )}

              <Button
                onClick={handleOpenLogs}
                variant="ghost"
                size="sm"
                leftIcon={<FileText className="w-3.5 h-3.5 text-slate-400" />}
              >
                Docker Logs
              </Button>

              <Button
                onClick={() => navigate('/deployments')}
                variant="ghost"
                size="sm"
                leftIcon={<Terminal className="w-3.5 h-3.5 text-slate-400" />}
              >
                Pipeline
              </Button>
            </div>
          </CardHeader>

          {/* Telemetry Metric Grid */}
          <CardContent className="p-5">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Health Probe */}
              <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex flex-col justify-between">
                <div>
                  <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5 mb-1.5">
                    <Activity className="w-3.5 h-3.5 text-[#10B981]" />
                    <span>Readiness Probe</span>
                  </span>
                  <div className="text-[#10B981] font-bold text-sm tracking-tight flex items-center gap-1.5 font-mono">
                    {healthReport.httpProbePassed ? (
                      <>
                        <span>UP (HTTP {healthReport.httpStatus || 200})</span>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                      </>
                    ) : (
                      <span className="text-rose-400 flex items-center gap-1">
                        <AlertCircle className="w-3.5 h-3.5" />
                        <span>FAIL ({healthReport.httpStatus ? `HTTP ${healthReport.httpStatus}` : 'OFFLINE'})</span>
                      </span>
                    )}
                  </div>
                </div>
                <div className="mt-3 text-[10px] text-slate-500 font-mono flex items-center justify-between border-t border-[#252D46]/60 pt-2">
                  <span>/actuator/health</span>
                  <span>{new Date(healthReport.lastCheckedAt).toLocaleTimeString()}</span>
                </div>
              </div>

              {/* Host Port Binding */}
              <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex flex-col justify-between">
                <div>
                  <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5 mb-1.5">
                    <Server className="w-3.5 h-3.5 text-[#3B82F6]" />
                    <span>Host Port Binding</span>
                  </span>
                  <div className="text-white font-mono font-bold text-sm tracking-tight flex items-center gap-1.5">
                    <span>{hostPort || 'Unassigned'} : 8080/TCP</span>
                    {hostPort && (
                      <a
                        href={`http://localhost:${hostPort}`}
                        target="_blank"
                        rel="noreferrer"
                        className="text-[#60A5FA] hover:text-[#93C5FD] transition-colors"
                        title="Open container endpoint"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                      </a>
                    )}
                  </div>
                </div>
                <div className="mt-3 text-[10px] text-slate-500 font-mono flex items-center justify-between border-t border-[#252D46]/60 pt-2">
                  <span className="text-[#60A5FA]">localhost:{hostPort || 8080}</span>
                  <span>Collision-free</span>
                </div>
              </div>

              {/* Memory Allocation */}
              <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex flex-col justify-between">
                <div>
                  <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5 mb-1.5">
                    <HardDrive className="w-3.5 h-3.5 text-[#A78BFA]" />
                    <span>Memory Allocation</span>
                  </span>
                  <div className="text-white font-mono font-bold text-sm tracking-tight">
                    {metrics?.memoryUsageFormatted || '0B / 512 MB'}
                  </div>
                </div>
                <div className="mt-3 border-t border-[#252D46]/60 pt-2 space-y-1.5">
                  <div className="w-full bg-[#111629] h-1.5 rounded-full overflow-hidden border border-[#252D46]">
                    <div
                      className="bg-gradient-to-r from-[#8B5CF6] to-[#3B82F6] h-full rounded-full transition-all duration-500"
                      style={{ width: `${Math.min(100, Math.max(0, metrics?.memoryPercent ?? 0))}%` }}
                    />
                  </div>
                  <div className="text-[10px] text-slate-500 font-mono flex justify-between">
                    <span>Usage: {metrics?.memoryPercent?.toFixed(1) ?? '0.0'}%</span>
                    <span>Limit: 512 MB</span>
                  </div>
                </div>
              </div>

              {/* CPU & PIDs */}
              <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex flex-col justify-between">
                <div>
                  <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5 mb-1.5">
                    <Cpu className="w-3.5 h-3.5 text-amber-400" />
                    <span>CPU & Process Threads</span>
                  </span>
                  <div className="text-white font-mono font-bold text-sm tracking-tight">
                    {metrics?.cpuPercent?.toFixed(1) ?? '0.0'}% • {metrics?.pidsCount ?? 0} PIDs
                  </div>
                </div>
                <div className="mt-3 text-[10px] text-slate-500 font-mono flex items-center justify-between border-t border-[#252D46]/60 pt-2">
                  <span className="flex items-center gap-1">
                    <Clock className="w-3 h-3 text-slate-600" />
                    Uptime: {formatUptime(metrics?.uptimeSeconds)}
                  </span>
                  <span>cgroups v2</span>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Network & Security Architecture Spec */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Isolation & Container Security */}
        <Card className="p-4 space-y-3">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-200">
            <ShieldCheck className="w-4 h-4 text-[#10B981]" />
            <span>Process Isolation & Sandbox</span>
          </div>
          <p className="text-xs text-slate-400 leading-relaxed">
            All workload containers run inside rootless Docker namespaces with dropped Linux capabilities (<code className="font-mono text-[#A78BFA]">CAP_DROP ALL</code>), read-only root filesystems where applicable, and dynamic ephemeral port assignments.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Badge variant="purple" size="xs">Rootless UID 10001</Badge>
            <Badge variant="blue" size="xs">Seccomp Profile</Badge>
            <Badge variant="cyan" size="xs">No New Privileges</Badge>
          </div>
        </Card>

        {/* Dynamic Ingress Routing */}
        <Card className="p-4 space-y-3">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-200">
            <Zap className="w-4 h-4 text-[#F59E0B]" />
            <span>Dynamic Ingress & Zero-Downtime</span>
          </div>
          <p className="text-xs text-slate-400 leading-relaxed">
            When a new deployment succeeds, traffic switches seamlessly from the predecessor container once the readiness probe returns <code className="font-mono text-[#10B981]">HTTP 200</code>. Stale containers are drained with a 30-second graceful grace period.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Badge variant="success" size="xs">Zero-Downtime</Badge>
            <Badge variant="warning" size="xs">30s Grace Drain</Badge>
            <Badge variant="neutral" size="xs">Keepalive 65s</Badge>
          </div>
        </Card>
      </div>

      {/* Docker Logs Inspection Modal */}
      <Modal
        isOpen={isLogsOpen}
        onClose={() => setIsLogsOpen(false)}
        title={
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-[#8B5CF6]" />
            <span>Docker Engine Container Logs (stdout/stderr)</span>
          </div>
        }
        size="lg"
      >
        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
            <span>Container: {containerId.substring(0, 12)}</span>
            <Button
              variant="ghost"
              size="xs"
              onClick={handleOpenLogs}
              isLoading={isLoadingLogs}
              leftIcon={<RefreshCw className="w-3 h-3" />}
            >
              Refresh
            </Button>
          </div>

          <div className="p-3 rounded-xl bg-[#06080F] border border-[#252D46] font-mono text-[11px] text-slate-200 max-h-[400px] overflow-y-auto whitespace-pre-wrap select-text terminal-scanlines">
            {isLoadingLogs ? (
              <div className="py-8 text-center text-slate-500 font-mono">
                Streaming container logs from Docker daemon...
              </div>
            ) : (
              containerLogs
            )}
          </div>

          <div className="flex justify-end pt-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setIsLogsOpen(false)}
            >
              Close
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
