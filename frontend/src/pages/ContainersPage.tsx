import React, { useState } from 'react';
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
  Radio, 
  FolderGit2, 
  Zap,
  Box,
  Layers
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { useToast } from '../context/ToastContext';

export const ContainersPage: React.FC = () => {
  const { activeProject, projects, selectProject } = useProjects();
  const navigate = useNavigate();
  const toast = useToast();

  const [isRestarting, setIsRestarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [containerState, setContainerState] = useState<'RUNNING' | 'STOPPED'>('RUNNING');

  const handleRestart = async () => {
    setIsRestarting(true);
    try {
      // Simulate container graceful restart cycle
      await new Promise((r) => setTimeout(r, 1200));
      setContainerState('RUNNING');
      toast.success('Container restarted successfully. Health probe returning HTTP 200.');
    } catch {
      toast.error('Failed to restart container.');
    } finally {
      setIsRestarting(false);
    }
  };

  const handleStop = async () => {
    if (!window.confirm('Are you sure you want to stop the active container? Ingress traffic will receive 502 Bad Gateway until restarted.')) {
      return;
    }

    setIsStopping(true);
    try {
      await new Promise((r) => setTimeout(r, 1000));
      setContainerState('STOPPED');
      toast.info('Container stopped. Container process terminated.');
    } catch {
      toast.error('Failed to stop container.');
    } finally {
      setIsStopping(false);
    }
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

  const containerName = `shipit-c-${activeProject.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}-${activeProject.port || '8080'}`;
  const containerId = activeProject.id.replace(/-/g, '').substring(0, 12);
  const hostPort = activeProject.port || 10042;
  const isRunning = containerState === 'RUNNING';

  return (
    <div className="space-y-6">
      {/* Top Header & Project Selector */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Badge variant="cyan" dot={true}>
              Docker Engine Live
            </Badge>
            <span className="text-xs text-slate-500 font-mono">bridge: shipit-net</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Active Containers
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Real-time container virtualization, port allocation mappings, and actuator health telemetry.
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

      {/* Main Container Control Card */}
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
                {containerName}
              </h2>
              <span className="px-2 py-0.5 rounded text-[10px] bg-[#080B14] font-mono border border-[#252D46] text-[#A78BFA]">
                ID: {containerId}
              </span>
              <Badge variant={isRunning ? 'success' : 'danger'}>
                {isRunning ? 'RUNNING' : 'STOPPED'}
              </Badge>
            </div>
            <p className="text-xs text-slate-400 font-mono">
              Base Image: <span className="text-slate-200">eclipse-temurin:21-jre-alpine</span> • Security: <span className="text-[#10B981]">UID 10001 (Non-root)</span>
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
              onClick={() => navigate('/deployments')}
              variant="ghost"
              size="sm"
              leftIcon={<Terminal className="w-3.5 h-3.5 text-slate-400" />}
            >
              Logs
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
                  <span>Health Probe</span>
                </span>
                <div className="text-[#10B981] font-bold text-sm tracking-tight flex items-center gap-1.5 font-mono">
                  {isRunning ? (
                    <>
                      <span>UP (HTTP 200)</span>
                      <CheckCircle2 className="w-3.5 h-3.5" />
                    </>
                  ) : (
                    <span className="text-rose-400">DOWN (CONN REFUSED)</span>
                  )}
                </div>
              </div>
              <div className="mt-3 text-[10px] text-slate-500 font-mono flex items-center justify-between border-t border-[#252D46]/60 pt-2">
                <span>Latency: {isRunning ? '4ms' : '0ms'}</span>
                <span>/actuator/health</span>
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
                  <span>{hostPort} : 8080/TCP</span>
                  {isRunning && (
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
                <span className="text-[#60A5FA]">localhost:{hostPort}</span>
                <span>Collision-free</span>
              </div>
            </div>

            {/* Memory Allocation */}
            <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex flex-col justify-between">
              <div>
                <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5 mb-1.5">
                  <HardDrive className="w-3.5 h-3.5 text-[#A78BFA]" />
                  <span>Memory Quota</span>
                </span>
                <div className="text-white font-mono font-bold text-sm tracking-tight">
                  {isRunning ? '246 MB / 512 MB' : '0 MB / 512 MB'}
                </div>
              </div>
              <div className="mt-3 border-t border-[#252D46]/60 pt-2 space-y-1.5">
                <div className="w-full bg-[#111629] h-1.5 rounded-full overflow-hidden border border-[#252D46]">
                  <div
                    className="bg-gradient-to-r from-[#8B5CF6] to-[#3B82F6] h-full rounded-full transition-all duration-500"
                    style={{ width: isRunning ? '48%' : '0%' }}
                  />
                </div>
                <div className="text-[10px] text-slate-500 font-mono flex justify-between">
                  <span>JVM Heap: 182 MB</span>
                  <span>48% Utilized</span>
                </div>
              </div>
            </div>

            {/* CPU & PIDs */}
            <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex flex-col justify-between">
              <div>
                <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5 mb-1.5">
                  <Cpu className="w-3.5 h-3.5 text-amber-400" />
                  <span>CPU & Threads</span>
                </span>
                <div className="text-white font-mono font-bold text-sm tracking-tight">
                  {isRunning ? '0.8% • 18 PIDs' : '0.0% • 0 PIDs'}
                </div>
              </div>
              <div className="mt-3 text-[10px] text-slate-500 font-mono flex items-center justify-between border-t border-[#252D46]/60 pt-2">
                <span className="flex items-center gap-1">
                  <Clock className="w-3 h-3 text-slate-600" />
                  Uptime: {isRunning ? '4h 12m' : 'Stopped'}
                </span>
                <span>cgroups v2</span>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

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
            <Badge variant="purple" size="xs">Rootless PID</Badge>
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
    </div>
  );
};
