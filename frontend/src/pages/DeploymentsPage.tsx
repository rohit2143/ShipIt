import React, { useState, useEffect, useRef } from 'react';
import { 
  Rocket, 
  RefreshCw, 
  XCircle, 
  Download, 
  CheckCircle2, 
  Clock, 
  Loader2, 
  AlertCircle,
  FolderGit2,
  Search,
  ArrowDownCircle,
  History,
  GitBranch,
  ExternalLink,
  ChevronRight,
  Terminal as TerminalIcon,
  Copy,
  Check
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { deploymentsApi } from '../api/deployments';
import type { Deployment, DeploymentLogEntry, DeploymentStatus } from '../types/deployment';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../context/ToastContext';

const STAGES = [
  { key: 'QUEUED', label: '1. QUEUED', title: 'Job Enqueued' },
  { key: 'CLONING', label: '2. CLONING', title: 'Git Shallow Clone' },
  { key: 'DETECTING', label: '3. DETECTING', title: 'Stack Detection' },
  { key: 'BUILDING', label: '4. BUILDING', title: 'Docker Build' },
  { key: 'STARTING', label: '5. STARTING', title: 'Port & Container' },
  { key: 'HEALTH_CHECKING', label: '6. HEALTH', title: 'Readiness Probe' },
];

const STAGE_ORDER: Record<string, number> = {
  QUEUED: 0,
  CLONING: 1,
  DETECTING: 2,
  BUILDING: 3,
  STARTING: 4,
  HEALTH_CHECKING: 5,
  SUCCESS: 6,
  FAILED: 6,
  CANCELLED: 6,
};

export const DeploymentsPage: React.FC = () => {
  const { activeProject, projects, selectProject } = useProjects();
  const toast = useToast();

  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [selectedDeployment, setSelectedDeployment] = useState<Deployment | null>(null);
  const [isLoadingDeployments, setIsLoadingDeployments] = useState(false);
  const [isTriggering, setIsTriggering] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);

  // Terminal & SSE State
  const [logs, setLogs] = useState<DeploymentLogEntry[]>([]);
  const [sseState, setSseState] = useState<'CONNECTING' | 'CONNECTED' | 'COMPLETED' | 'DISCONNECTED'>('DISCONNECTED');
  const [autoScroll, setAutoScroll] = useState(true);
  const [filterQuery, setFilterQuery] = useState('');
  const [copiedLogs, setCopiedLogs] = useState(false);
  const terminalEndRef = useRef<HTMLDivElement | null>(null);

  // 1. Fetch deployments when active project changes
  useEffect(() => {
    if (!activeProject) {
      setDeployments([]);
      setSelectedDeployment(null);
      return;
    }

    const loadDeployments = async () => {
      setIsLoadingDeployments(true);
      try {
        const page = await deploymentsApi.getProjectDeployments(activeProject.id, 0, 30);
        setDeployments(page.content);
        if (page.content.length > 0) {
          setSelectedDeployment((prev) => {
            if (prev && page.content.some((d) => d.id === prev.id)) {
              return page.content.find((d) => d.id === prev.id)!;
            }
            return page.content[0];
          });
        } else {
          setSelectedDeployment(null);
        }
      } catch {
        setDeployments([]);
        setSelectedDeployment(null);
      } finally {
        setIsLoadingDeployments(false);
      }
    };

    loadDeployments();
  }, [activeProject?.id]);

  // 2. Stream logs or load historical logs for the selected deployment
  useEffect(() => {
    if (!selectedDeployment) {
      setLogs([]);
      setSseState('DISCONNECTED');
      return;
    }

    setLogs([]);
    setSseState('CONNECTING');

    // First load initial historical logs
    deploymentsApi.getHistoricalLogs(selectedDeployment.id)
      .then((res) => {
        if (res.logs && res.logs.length > 0) {
          setLogs(res.logs);
        }
      })
      .catch(() => {});

    // If terminal state, we are done
    if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(selectedDeployment.status)) {
      setSseState('COMPLETED');
      return;
    }

    // Subscribe to live SSE stream
    const unsubscribe = deploymentsApi.streamLogs(selectedDeployment.id, {
      onConnected: () => {
        setSseState('CONNECTED');
      },
      onLog: (newLog) => {
        setLogs((prev) => {
          if (prev.some((l) => l.sequenceNumber === newLog.sequenceNumber)) {
            return prev;
          }
          return [...prev, newLog];
        });
      },
      onStatus: (newStatus) => {
        setSelectedDeployment((prev) => prev ? { ...prev, status: newStatus as DeploymentStatus } : prev);
        setDeployments((prev) => 
          prev.map((d) => d.id === selectedDeployment.id ? { ...d, status: newStatus as DeploymentStatus } : d)
        );
      },
      onComplete: (finalStatus) => {
        setSelectedDeployment((prev) => prev ? { ...prev, status: finalStatus as DeploymentStatus } : prev);
        setDeployments((prev) => 
          prev.map((d) => d.id === selectedDeployment.id ? { ...d, status: finalStatus as DeploymentStatus } : d)
        );
        setSseState('COMPLETED');
      },
      onError: () => {
        setSseState('DISCONNECTED');
      },
    });

    return () => {
      unsubscribe();
    };
  }, [selectedDeployment?.id]);

  // Auto-scroll terminal
  useEffect(() => {
    if (autoScroll && terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  // Handle Trigger Deployment
  const handleTriggerDeployment = async () => {
    if (!activeProject || isTriggering) return;
    setIsTriggering(true);

    try {
      const newDep = await deploymentsApi.triggerDeployment(activeProject.id, {
        branch: activeProject.deploymentBranch || 'main',
      });
      setDeployments((prev) => [newDep, ...prev]);
      setSelectedDeployment(newDep);
      toast.success(`Deployment job triggered for branch ${activeProject.deploymentBranch || 'main'}`);
    } catch {
      toast.error('Failed to trigger deployment. Please ensure a worker is available.');
    } finally {
      setIsTriggering(false);
    }
  };

  // Handle Cancel Deployment
  const handleCancelDeployment = async () => {
    if (!selectedDeployment || isCancelling) return;
    if (!window.confirm('Are you sure you want to cancel this deployment?')) return;

    setIsCancelling(true);
    try {
      const updated = await deploymentsApi.cancelDeployment(selectedDeployment.id);
      setSelectedDeployment(updated);
      setDeployments((prev) => prev.map((d) => d.id === updated.id ? updated : d));
      toast.info('Deployment has been cancelled.');
    } catch {
      toast.error('Failed to cancel deployment.');
    } finally {
      setIsCancelling(false);
    }
  };

  // Handle Retry Deployment
  const handleRetryDeployment = async () => {
    if (!selectedDeployment) return;
    try {
      const newDep = await deploymentsApi.retryDeployment(selectedDeployment.id);
      setDeployments((prev) => [newDep, ...prev]);
      setSelectedDeployment(newDep);
      toast.success('Re-deployment enqueued successfully.');
    } catch {
      toast.error('Failed to retry deployment.');
    }
  };

  // Copy Logs to Clipboard
  const handleCopyLogs = () => {
    const rawText = logs.map((l) => `[${l.stage}] ${l.message}`).join('\n');
    navigator.clipboard.writeText(rawText);
    setCopiedLogs(true);
    toast.success('Deployment logs copied to clipboard');
    setTimeout(() => setCopiedLogs(false), 2000);
  };

  // Filtered log lines
  const filteredLogs = filterQuery
    ? logs.filter((l) => l.message.toLowerCase().includes(filterQuery.toLowerCase()) || l.stage.toLowerCase().includes(filterQuery.toLowerCase()))
    : logs;

  // Calculate current stage index
  const currentStageIndex = selectedDeployment ? STAGE_ORDER[selectedDeployment.status] ?? 0 : 0;

  if (!activeProject) {
    return (
      <Card className="p-12 text-center space-y-4">
        <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
          <FolderGit2 className="w-7 h-7" />
        </div>
        <div>
          <h3 className="text-base font-semibold text-white">No Project Selected</h3>
          <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
            Select or create a project from the top navigation to inspect its deployment lifecycle, active container, and real-time streaming logs.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {/* Top Action & Project Selector Bar */}
      <Card className="p-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#8B5CF6]/20 to-[#3B82F6]/20 border border-[#8B5CF6]/30 text-[#A78BFA] flex items-center justify-center shadow-inner">
              <Rocket className="w-5 h-5 text-[#A78BFA]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <select
                  value={activeProject.id}
                  onChange={(e) => selectProject(e.target.value)}
                  className="font-bold text-sm text-white bg-[#080B14] border border-[#252D46] rounded-lg px-2.5 py-1 cursor-pointer focus:outline-none focus:border-[#8B5CF6] transition-colors"
                >
                  {projects.map((p) => (
                    <option key={p.id} value={p.id} className="bg-[#0D1120] text-slate-200">
                      {p.name}
                    </option>
                  ))}
                </select>
                <span className="text-xs font-mono text-slate-400 hidden sm:inline">
                  {activeProject.repositoryFullName || 'Local Repo'}
                </span>
              </div>
              <div className="flex items-center gap-2.5 mt-1 text-xs font-mono text-slate-400">
                <span className="flex items-center gap-1">
                  <GitBranch className="w-3 h-3 text-slate-500" />
                  <span className="text-slate-200 font-medium">{activeProject.deploymentBranch || 'main'}</span>
                </span>
                {activeProject.port && (
                  <>
                    <span className="text-slate-600">•</span>
                    <a
                      href={`http://localhost:${activeProject.port}`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-[#60A5FA] hover:underline flex items-center gap-1 font-mono"
                    >
                      Port: {activeProject.port}
                      <ExternalLink className="w-2.5 h-2.5" />
                    </a>
                  </>
                )}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto">
            <Button
              onClick={handleTriggerDeployment}
              isLoading={isTriggering}
              variant="primary"
              size="md"
              leftIcon={<Rocket className="w-4 h-4" />}
            >
              Deploy Now
            </Button>
          </div>
        </div>
      </Card>

      {/* Main Deployment Console */}
      {isLoadingDeployments ? (
        <Card className="p-12 text-center space-y-3">
          <Loader2 className="w-7 h-7 animate-spin text-[#8B5CF6] mx-auto" />
          <p className="text-xs text-slate-400 font-mono">Loading deployment pipeline...</p>
        </Card>
      ) : deployments.length === 0 ? (
        <Card className="p-12 text-center space-y-4">
          <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
            <History className="w-7 h-7" />
          </div>
          <div>
            <h3 className="text-base font-semibold text-white">No Deployments Executed</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
              Trigger your first deployment to build and containerize branch <span className="text-[#A78BFA] font-mono">{activeProject.deploymentBranch || 'main'}</span> with the Docker engine.
            </p>
          </div>
          <Button
            onClick={handleTriggerDeployment}
            isLoading={isTriggering}
            variant="primary"
            size="md"
            leftIcon={<Rocket className="w-4 h-4" />}
          >
            Deploy Now
          </Button>
        </Card>
      ) : (
        <div className="space-y-6">
          {/* Deployment Lifecycle Card */}
          {selectedDeployment && (
            <Card className="p-5 space-y-5 relative overflow-hidden">
              <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 pb-4 border-b border-[#252D46]">
                <div className="space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-[#080B14] border border-[#252D46] text-[#A78BFA]">
                      DEP-{selectedDeployment.id.substring(0, 8)}
                    </span>
                    <h2 className="text-base font-bold text-white">Deployment Pipeline</h2>
                    <Badge
                      variant={
                        selectedDeployment.status === 'SUCCESS'
                          ? 'success'
                          : selectedDeployment.status === 'FAILED'
                          ? 'danger'
                          : selectedDeployment.status === 'CANCELLED'
                          ? 'neutral'
                          : 'purple'
                      }
                      dot={!['SUCCESS', 'FAILED', 'CANCELLED'].includes(selectedDeployment.status)}
                    >
                      {selectedDeployment.status}
                    </Badge>
                  </div>

                  <p className="text-xs text-slate-400 font-mono flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span>
                      Trigger: <span className="text-slate-200 font-semibold">{selectedDeployment.trigger}</span>
                    </span>
                    <span className="text-slate-600">•</span>
                    <span>
                      Branch: <span className="text-slate-200 font-semibold">{selectedDeployment.commitBranch || selectedDeployment.branch || 'main'}</span>
                    </span>
                    {selectedDeployment.commitSha && (
                      <>
                        <span className="text-slate-600">•</span>
                        <span>
                          Commit: <span className="text-slate-200">{selectedDeployment.commitSha.substring(0, 7)}</span>
                        </span>
                      </>
                    )}
                    {selectedDeployment.durationSeconds && (
                      <>
                        <span className="text-slate-600">•</span>
                        <span>
                          Duration: <span className="text-[#10B981] font-bold">{selectedDeployment.durationSeconds}s</span>
                        </span>
                      </>
                    )}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {!['SUCCESS', 'FAILED', 'CANCELLED'].includes(selectedDeployment.status) ? (
                    <Button
                      onClick={handleCancelDeployment}
                      isLoading={isCancelling}
                      variant="danger"
                      size="sm"
                      leftIcon={<XCircle className="w-3.5 h-3.5" />}
                    >
                      Cancel Deployment
                    </Button>
                  ) : (
                    <Button
                      onClick={handleRetryDeployment}
                      variant="primary"
                      size="sm"
                      leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
                    >
                      Re-deploy
                    </Button>
                  )}

                  <a
                    href={deploymentsApi.getDownloadLogsUrl(selectedDeployment.id)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <Button
                      variant="secondary"
                      size="sm"
                      leftIcon={<Download className="w-3.5 h-3.5" />}
                    >
                      Raw Logs
                    </Button>
                  </a>
                </div>
              </div>

              {/* 6-Stage Stepper */}
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5 pt-1 text-xs">
                {STAGES.map((s, idx) => {
                  const isCompleted = currentStageIndex > idx || selectedDeployment.status === 'SUCCESS';
                  const isCurrent = currentStageIndex === idx && !['SUCCESS', 'FAILED', 'CANCELLED'].includes(selectedDeployment.status);
                  const isFailed = selectedDeployment.status === 'FAILED' && currentStageIndex === idx;

                  let borderClass = 'border-[#252D46] bg-[#080B14]/60 text-slate-500';
                  let icon = <Clock className="w-3.5 h-3.5 text-slate-600" />;

                  if (isCompleted) {
                    borderClass = 'border-[#10B981]/40 bg-[#10B981]/10 text-[#10B981] shadow-[0_0_12px_rgba(16,185,129,0.15)]';
                    icon = <CheckCircle2 className="w-3.5 h-3.5 text-[#10B981]" />;
                  } else if (isCurrent) {
                    borderClass = 'border-[#8B5CF6]/50 bg-[#8B5CF6]/15 text-[#A78BFA] shadow-[0_0_15px_rgba(139,92,246,0.25)] animate-pulse';
                    icon = <Loader2 className="w-3.5 h-3.5 animate-spin text-[#A78BFA]" />;
                  } else if (isFailed) {
                    borderClass = 'border-rose-500/50 bg-rose-500/15 text-rose-400 shadow-[0_0_12px_rgba(244,63,94,0.15)]';
                    icon = <AlertCircle className="w-3.5 h-3.5 text-rose-400" />;
                  }

                  return (
                    <div
                      key={s.key}
                      className={`flex flex-col p-3 rounded-xl border transition-all duration-200 ${borderClass}`}
                    >
                      <span className="text-[10px] font-semibold flex items-center gap-1.5 font-mono">
                        {icon}
                        <span>{s.label}</span>
                      </span>
                      <span className="text-[11px] font-medium mt-1 truncate">{s.title}</span>
                    </div>
                  );
                })}
              </div>
            </Card>
          )}

          {/* Terminal Log Stream Window */}
          <div className="rounded-2xl border border-[#252D46] bg-[#06080F] text-slate-200 overflow-hidden shadow-2xl flex flex-col">
            {/* Terminal Top Titlebar */}
            <div className="px-4 py-3 bg-[#0D1120] border-b border-[#252D46] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs">
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-1.5">
                  <span className="w-3 h-3 rounded-full bg-rose-500/80 border border-rose-500/40"></span>
                  <span className="w-3 h-3 rounded-full bg-amber-500/80 border border-amber-500/40"></span>
                  <span className="w-3 h-3 rounded-full bg-emerald-500/80 border border-emerald-500/40"></span>
                </div>
                <div className="flex items-center gap-2 font-mono text-slate-400 text-[11px]">
                  <TerminalIcon className="w-3.5 h-3.5 text-[#8B5CF6]" />
                  <span>shipit-worker@node0: ~/deployments/{selectedDeployment?.id.substring(0, 8)}.log</span>
                </div>
                <Badge
                  variant={
                    sseState === 'CONNECTED'
                      ? 'success'
                      : sseState === 'CONNECTING'
                      ? 'warning'
                      : sseState === 'COMPLETED'
                      ? 'blue'
                      : 'neutral'
                  }
                  dot={sseState === 'CONNECTED'}
                >
                  SSE: {sseState}
                </Badge>
              </div>

              <div className="flex items-center gap-2 w-full sm:w-auto">
                {/* Search in logs */}
                <div className="relative flex-1 sm:flex-initial">
                  <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    placeholder="Search logs..."
                    value={filterQuery}
                    onChange={(e) => setFilterQuery(e.target.value)}
                    className="w-full sm:w-44 text-[11px] font-mono pl-8 pr-2.5 py-1.5 rounded-lg bg-[#080B14] border border-[#252D46] text-slate-200 placeholder-slate-600 focus:outline-none focus:border-[#8B5CF6]"
                  />
                  {filterQuery && (
                    <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] text-slate-400 font-mono">
                      {filteredLogs.length}
                    </span>
                  )}
                </div>

                {/* Auto-scroll Button */}
                <button
                  onClick={() => setAutoScroll(!autoScroll)}
                  className={`px-2.5 py-1.5 rounded-lg text-[11px] font-mono border transition-colors flex items-center gap-1.5 cursor-pointer ${
                    autoScroll
                      ? 'bg-[#10B981]/10 border-[#10B981]/30 text-[#10B981]'
                      : 'bg-[#111629] border-[#252D46] text-slate-400 hover:text-slate-200'
                  }`}
                  title="Auto-scroll to latest log line"
                >
                  <ArrowDownCircle className="w-3.5 h-3.5" />
                  <span>Scroll</span>
                </button>

                {/* Copy logs */}
                <button
                  onClick={handleCopyLogs}
                  className="px-2.5 py-1.5 rounded-lg text-[11px] font-mono border bg-[#111629] border-[#252D46] text-slate-400 hover:text-slate-200 transition-colors flex items-center gap-1.5 cursor-pointer"
                  title="Copy terminal logs"
                >
                  {copiedLogs ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-[#10B981]" />
                      <span className="text-[#10B981]">Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5" />
                      <span>Copy</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* Terminal Body */}
            <div className="p-4 font-mono text-[12px] leading-relaxed overflow-y-auto max-h-[550px] space-y-1 select-text scrollbar-thin scrollbar-thumb-slate-800 terminal-scanlines">
              {filteredLogs.length === 0 ? (
                <div className="py-12 text-center text-slate-500 font-mono text-xs space-y-2">
                  <TerminalIcon className="w-6 h-6 mx-auto text-slate-600 opacity-60" />
                  <p>
                    {selectedDeployment?.status === 'QUEUED'
                      ? 'Deployment is enqueued. Waiting for worker to acquire via PostgreSQL SKIP LOCKED...'
                      : 'No log output recorded yet for this deployment.'}
                  </p>
                </div>
              ) : (
                filteredLogs.map((log, i) => (
                  <div key={i} className="flex gap-2.5 hover:bg-[#111629]/60 px-1.5 py-0.5 rounded transition-colors group">
                    <span className="text-slate-600 select-none shrink-0 font-mono text-[10px] w-14 group-hover:text-slate-400 transition-colors">
                      {log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : `#${log.sequenceNumber}`}
                    </span>
                    <span
                      className={`font-semibold shrink-0 text-[11px] ${
                        log.stage === 'BUILDING'
                          ? 'text-amber-400'
                          : log.stage === 'DETECTING'
                          ? 'text-cyan-400'
                          : log.stage === 'STARTING'
                          ? 'text-indigo-400'
                          : log.stage === 'HEALTH_CHECKING'
                          ? 'text-[#A78BFA]'
                          : log.stage === 'SUCCESS'
                          ? 'text-[#10B981]'
                          : log.stage === 'FAILED'
                          ? 'text-rose-400'
                          : 'text-slate-400'
                      }`}
                    >
                      [{log.stage}]
                    </span>
                    <span className="text-slate-300 break-all whitespace-pre-wrap font-mono flex-1">{log.message}</span>
                  </div>
                ))
              )}
              <div ref={terminalEndRef} />
            </div>
          </div>

          {/* Past Deployments History Shelf */}
          {deployments.length > 1 && (
            <Card className="p-4 space-y-3">
              <div className="flex items-center justify-between text-xs font-semibold text-slate-300">
                <div className="flex items-center gap-2">
                  <History className="w-4 h-4 text-[#8B5CF6]" />
                  <span>Deployment History ({deployments.length} runs)</span>
                </div>
                <span className="text-slate-500 font-mono text-[11px]">Click to inspect logs</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2.5">
                {deployments.map((d) => {
                  const isSelected = selectedDeployment?.id === d.id;
                  return (
                    <button
                      key={d.id}
                      onClick={() => setSelectedDeployment(d)}
                      className={`p-3 rounded-xl border text-left transition-all duration-200 cursor-pointer ${
                        isSelected
                          ? 'bg-[#8B5CF6]/15 border-[#8B5CF6]/50 shadow-[0_0_15px_rgba(139,92,246,0.15)] ring-1 ring-[#8B5CF6]/30'
                          : 'bg-[#080B14] border-[#252D46] hover:border-slate-600 hover:bg-[#111629]'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="font-mono text-xs font-bold text-white">
                          DEP-{d.id.substring(0, 8)}
                        </span>
                        <Badge
                          variant={
                            d.status === 'SUCCESS'
                              ? 'success'
                              : d.status === 'FAILED'
                              ? 'danger'
                              : d.status === 'CANCELLED'
                              ? 'neutral'
                              : 'blue'
                          }
                          size="xs"
                        >
                          {d.status}
                        </Badge>
                      </div>
                      <p className="text-[11px] text-slate-400 font-mono truncate">
                        {d.commitBranch || d.branch || 'main'}
                        {d.commitSha ? ` • ${d.commitSha.substring(0, 7)}` : ''}
                      </p>
                      <span className="text-[10px] text-slate-500 font-mono mt-1 block">
                        {d.createdAt ? new Date(d.createdAt).toLocaleString() : ''}
                      </span>
                    </button>
                  );
                })}
              </div>
            </Card>
          )}
        </div>
      )}
    </div>
  );
};
