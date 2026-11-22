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
  History
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { deploymentsApi } from '../api/deployments';
import type { Deployment, DeploymentLogEntry, DeploymentStatus } from '../types/deployment';

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
    } catch (err: unknown) {
      alert('Failed to trigger deployment. Please ensure a worker is available.');
    } finally {
      setIsTriggering(false);
    }
  };

  // Handle Cancel Deployment
  const handleCancelDeployment = async () => {
    if (!selectedDeployment || isCancelling) return;
    if (!confirm('Are you sure you want to cancel this deployment?')) return;

    setIsCancelling(true);
    try {
      const updated = await deploymentsApi.cancelDeployment(selectedDeployment.id);
      setSelectedDeployment(updated);
      setDeployments((prev) => prev.map((d) => d.id === updated.id ? updated : d));
    } catch {
      alert('Failed to cancel deployment.');
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
    } catch {
      alert('Failed to retry deployment.');
    }
  };

  // Filtered log lines
  const filteredLogs = filterQuery
    ? logs.filter((l) => l.message.toLowerCase().includes(filterQuery.toLowerCase()) || l.stage.toLowerCase().includes(filterQuery.toLowerCase()))
    : logs;

  // Calculate current stage index
  const currentStageIndex = selectedDeployment ? STAGE_ORDER[selectedDeployment.status] ?? 0 : 0;

  if (!activeProject) {
    return (
      <div className="rounded-xl bg-slate-900 border border-slate-800 p-12 text-center space-y-4">
        <div className="w-12 h-12 rounded-xl bg-slate-800 flex items-center justify-center mx-auto text-slate-400">
          <FolderGit2 className="w-6 h-6" />
        </div>
        <div>
          <h3 className="text-base font-semibold text-slate-200">No Project Selected</h3>
          <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
            Select or create a project to inspect its deployment lifecycle, active container, and real-time streaming logs.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Top Action & Project Selector Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-slate-900 border border-slate-800 p-4 rounded-xl shadow-sm">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-blue-600/20 border border-blue-500/30 text-blue-400 flex items-center justify-center font-bold">
            <Rocket className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <select
                value={activeProject.id}
                onChange={(e) => selectProject(e.target.value)}
                className="font-bold text-sm text-slate-100 bg-transparent border-none cursor-pointer focus:outline-none hover:text-blue-400 transition-colors"
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                    {p.name}
                  </option>
                ))}
              </select>
              <span className="text-xs font-mono text-slate-400">
                ({activeProject.repositoryFullName || 'Local Repo'})
              </span>
            </div>
            <p className="text-xs text-slate-400 font-mono">
              Branch: <span className="text-slate-200 font-medium">{activeProject.deploymentBranch || 'main'}</span>
              {activeProject.port && (
                <span className="ml-3 text-indigo-400">Port: {activeProject.port}</span>
              )}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <button
            onClick={handleTriggerDeployment}
            disabled={isTriggering}
            className="flex-1 sm:flex-initial inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-md transition-all cursor-pointer disabled:opacity-50"
          >
            {isTriggering ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Enqueuing Build...</span>
              </>
            ) : (
              <>
                <Rocket className="w-4 h-4" />
                <span>Deploy Now</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Main Deployment Console */}
      {isLoadingDeployments ? (
        <div className="py-16 text-center text-slate-400 text-xs space-y-2">
          <Loader2 className="w-6 h-6 animate-spin text-blue-400 mx-auto" />
          <span>Loading project deployments...</span>
        </div>
      ) : deployments.length === 0 ? (
        <div className="rounded-xl bg-slate-900 border border-slate-800 p-12 text-center space-y-4">
          <div className="w-12 h-12 rounded-xl bg-slate-800 flex items-center justify-center mx-auto text-slate-400">
            <History className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-base font-semibold text-slate-200">No Deployments Yet</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
              Click &quot;Deploy Now&quot; to build and containerize branch <span className="text-blue-400 font-mono">{activeProject.deploymentBranch || 'main'}</span> with Docker.
            </p>
          </div>
          <button
            onClick={handleTriggerDeployment}
            disabled={isTriggering}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-md transition-all cursor-pointer"
          >
            <Rocket className="w-4 h-4" />
            <span>Deploy Now</span>
          </button>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Deployment Lifecycle Card */}
          {selectedDeployment && (
            <div className="rounded-xl bg-slate-900 border border-slate-800 p-5 shadow-sm space-y-5">
              <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-3 pb-4 border-b border-slate-800">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-slate-950 border border-slate-800 text-slate-300">
                      DEP-{selectedDeployment.id.substring(0, 8)}
                    </span>
                    <h2 className="text-base font-bold text-slate-100">Deployment Lifecycle</h2>
                    <span
                      className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${
                        selectedDeployment.status === 'SUCCESS'
                          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                          : selectedDeployment.status === 'FAILED'
                          ? 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                          : selectedDeployment.status === 'CANCELLED'
                          ? 'bg-slate-800 text-slate-400 border-slate-700'
                          : 'bg-blue-500/10 text-blue-400 border-blue-500/20 animate-pulse'
                      }`}
                    >
                      {selectedDeployment.status}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 font-mono">
                    Trigger: <span className="text-slate-200 font-semibold">{selectedDeployment.trigger}</span> • Branch:{' '}
                    <span className="text-slate-200 font-semibold">{selectedDeployment.commitBranch || selectedDeployment.branch || 'main'}</span>
                    {selectedDeployment.commitSha && (
                      <> • Commit: <span className="text-slate-200">{selectedDeployment.commitSha.substring(0, 7)}</span></>
                    )}
                    {selectedDeployment.durationSeconds && (
                      <> • Duration: <span className="text-emerald-400 font-bold">{selectedDeployment.durationSeconds}s</span></>
                    )}
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  {!['SUCCESS', 'FAILED', 'CANCELLED'].includes(selectedDeployment.status) ? (
                    <button
                      onClick={handleCancelDeployment}
                      disabled={isCancelling}
                      className="px-3 py-1.5 text-xs font-medium rounded-md border border-rose-500/30 text-rose-400 hover:bg-rose-500/10 transition-colors flex items-center gap-1.5 cursor-pointer"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      <span>{isCancelling ? 'Cancelling...' : 'Cancel Deployment'}</span>
                    </button>
                  ) : (
                    <button
                      onClick={handleRetryDeployment}
                      className="px-3 py-1.5 text-xs font-medium rounded-md bg-blue-600 hover:bg-blue-500 text-white shadow-sm transition-all flex items-center gap-1.5 cursor-pointer"
                    >
                      <RefreshCw className="w-3.5 h-3.5" />
                      <span>Re-deploy</span>
                    </button>
                  )}

                  <a
                    href={deploymentsApi.getDownloadLogsUrl(selectedDeployment.id)}
                    target="_blank"
                    rel="noreferrer"
                    className="px-3 py-1.5 text-xs font-medium rounded-md bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors flex items-center gap-1.5"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Download Raw Log</span>
                  </a>
                </div>
              </div>

              {/* 6-Stage Stepper */}
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2 pt-1 text-xs">
                {STAGES.map((s, idx) => {
                  const isCompleted = currentStageIndex > idx || selectedDeployment.status === 'SUCCESS';
                  const isCurrent = currentStageIndex === idx && !['SUCCESS', 'FAILED', 'CANCELLED'].includes(selectedDeployment.status);
                  const isFailed = selectedDeployment.status === 'FAILED' && currentStageIndex === idx;

                  let borderClass = 'border-slate-800 bg-slate-950/40 text-slate-500';
                  let icon = <Clock className="w-3.5 h-3.5 text-slate-600" />;

                  if (isCompleted) {
                    borderClass = 'border-emerald-500/30 bg-emerald-500/5 text-emerald-400';
                    icon = <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />;
                  } else if (isCurrent) {
                    borderClass = 'border-blue-500/40 bg-blue-500/10 text-blue-300 shadow-sm shadow-blue-500/10';
                    icon = <Loader2 className="w-3.5 h-3.5 animate-spin text-blue-400" />;
                  } else if (isFailed) {
                    borderClass = 'border-rose-500/40 bg-rose-500/10 text-rose-400';
                    icon = <AlertCircle className="w-3.5 h-3.5 text-rose-400" />;
                  }

                  return (
                    <div
                      key={s.key}
                      className={`flex flex-col p-2.5 rounded-lg border transition-all ${borderClass}`}
                    >
                      <span className="text-[10px] font-semibold flex items-center gap-1.5">
                        {icon}
                        <span>{s.label}</span>
                      </span>
                      <span className="text-[11px] font-medium mt-1 truncate">{s.title}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Terminal Log Stream */}
          <div className="rounded-xl border border-slate-800 bg-[#0d1117] text-slate-200 overflow-hidden shadow-xl flex flex-col">
            <div className="px-4 py-2.5 bg-[#161b22] border-b border-slate-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-xs">
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80"></span>
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80"></span>
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500/80"></span>
                </div>
                <span className="font-mono text-slate-400 text-[11px] ml-2">
                  shipit-worker@node0: ~/deployments/{selectedDeployment?.id.substring(0, 8)}.log
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded font-mono border ${
                    sseState === 'CONNECTED'
                      ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30 animate-pulse'
                      : sseState === 'CONNECTING'
                      ? 'bg-amber-500/20 text-amber-400 border-amber-500/30'
                      : sseState === 'COMPLETED'
                      ? 'bg-blue-500/20 text-blue-400 border-blue-500/30'
                      : 'bg-slate-800 text-slate-400 border-slate-700'
                  }`}
                >
                  SSE: {sseState}
                </span>
              </div>

              <div className="flex items-center gap-2 w-full sm:w-auto">
                <div className="relative flex-1 sm:flex-initial">
                  <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-2" />
                  <input
                    type="text"
                    placeholder="Search logs..."
                    value={filterQuery}
                    onChange={(e) => setFilterQuery(e.target.value)}
                    className="w-full sm:w-44 text-[11px] font-mono pl-8 pr-2.5 py-1 rounded bg-slate-900 border border-slate-700 text-slate-200 placeholder-slate-600 focus:outline-none focus:border-blue-500"
                  />
                </div>

                <button
                  onClick={() => setAutoScroll(!autoScroll)}
                  className={`px-2.5 py-1 rounded text-[11px] font-mono border transition-colors flex items-center gap-1 cursor-pointer ${
                    autoScroll
                      ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                      : 'bg-slate-800 border-slate-700 text-slate-400'
                  }`}
                  title="Auto-scroll to latest log line"
                >
                  <ArrowDownCircle className="w-3.5 h-3.5" />
                  <span>Auto-scroll</span>
                </button>
              </div>
            </div>

            <div className="p-4 font-mono text-[11px] leading-relaxed overflow-y-auto max-h-[550px] space-y-1 select-text">
              {filteredLogs.length === 0 ? (
                <div className="py-8 text-center text-slate-500 font-mono text-xs">
                  {selectedDeployment?.status === 'QUEUED'
                    ? 'Deployment is enqueued. Waiting for worker to acquire via PostgreSQL SKIP LOCKED...'
                    : 'No log output recorded yet for this deployment.'}
                </div>
              ) : (
                filteredLogs.map((log, i) => (
                  <div key={i} className="flex gap-2.5 hover:bg-slate-800/20 px-1 rounded transition-colors">
                    <span className="text-slate-600 select-none shrink-0 font-mono text-[10px]">
                      {log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : `#${log.sequenceNumber}`}
                    </span>
                    <span
                      className={`font-semibold shrink-0 ${
                        log.stage === 'BUILDING'
                          ? 'text-amber-400'
                          : log.stage === 'DETECTING'
                          ? 'text-cyan-400'
                          : log.stage === 'STARTING'
                          ? 'text-indigo-400'
                          : log.stage === 'HEALTH_CHECKING'
                          ? 'text-purple-400'
                          : log.stage === 'SUCCESS'
                          ? 'text-emerald-400'
                          : log.stage === 'FAILED'
                          ? 'text-rose-400'
                          : 'text-slate-400'
                      }`}
                    >
                      [{log.stage}]
                    </span>
                    <span className="text-slate-300 break-all whitespace-pre-wrap">{log.message}</span>
                  </div>
                ))
              )}
              <div ref={terminalEndRef} />
            </div>
          </div>

          {/* Past Deployments History Shelf */}
          {deployments.length > 1 && (
            <div className="rounded-xl bg-slate-900 border border-slate-800 p-4 shadow-sm space-y-3">
              <div className="flex items-center gap-2 text-xs font-semibold text-slate-300">
                <History className="w-4 h-4 text-slate-400" />
                <span>Deployment History ({deployments.length} runs)</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2.5">
                {deployments.map((d) => (
                  <button
                    key={d.id}
                    onClick={() => setSelectedDeployment(d)}
                    className={`p-3 rounded-lg border text-left transition-all cursor-pointer ${
                      selectedDeployment?.id === d.id
                        ? 'bg-blue-600/10 border-blue-500/40 shadow-sm'
                        : 'bg-slate-950 border-slate-800/80 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-mono text-xs font-bold text-slate-200">
                        DEP-{d.id.substring(0, 8)}
                      </span>
                      <span
                        className={`text-[9px] px-1.5 py-0.2 rounded font-semibold border ${
                          d.status === 'SUCCESS'
                            ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                            : d.status === 'FAILED'
                            ? 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                            : 'bg-blue-500/10 text-blue-400 border-blue-500/20'
                        }`}
                      >
                        {d.status}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 font-mono mt-1 truncate">
                      {d.commitBranch || d.branch || 'main'}
                      {d.commitSha ? ` • ${d.commitSha.substring(0, 7)}` : ''}
                    </p>
                    <span className="text-[10px] text-slate-500 font-mono mt-0.5 block">
                      {d.createdAt ? new Date(d.createdAt).toLocaleString() : ''}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
