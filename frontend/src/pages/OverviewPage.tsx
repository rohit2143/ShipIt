import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Package, 
  Rocket, 
  Clock, 
  Zap, 
  ExternalLink, 
  Plus, 
  Trash2, 
  Loader2,
  FolderGit2,
  Search,
  GitBranch,
  Terminal,
  Activity,
  Layers,
  ArrowUpRight
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { CreateProjectModal } from '../components/projects/CreateProjectModal';
import { projectsApi } from '../api/projects';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Input } from '../components/ui/Input';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../context/ToastContext';

export const OverviewPage: React.FC = () => {
  const { projects, selectProject, refreshProjects, isLoading } = useProjects();
  const navigate = useNavigate();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const filteredProjects = projects.filter((p) =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    (p.repositoryFullName && p.repositoryFullName.toLowerCase().includes(search.toLowerCase()))
  );

  const handleDelete = async (id: string, name: string) => {
    if (!window.confirm(`Are you sure you want to delete project "${name}"? This action cannot be undone.`)) {
      return;
    }

    setDeletingId(id);
    try {
      await projectsApi.deleteProject(id);
      toast.success(`Project "${name}" was successfully deleted`);
      await refreshProjects();
    } catch {
      toast.error(`Failed to delete project "${name}"`);
    } finally {
      setDeletingId(null);
    }
  };

  const activeProjectsCount = projects.filter((p) => p.status === 'ACTIVE').length;
  const autoDeployCount = projects.filter((p) => p.autoDeploy).length;

  return (
    <div className="space-y-6">
      {/* Page Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-[#8B5CF6]/15 text-[#A78BFA] border border-[#8B5CF6]/30">
              <Activity className="w-3 h-3 text-[#A78BFA] animate-pulse" />
              Production Cluster
            </span>
            <span className="text-xs text-slate-500 font-mono">v1.0.0-rc2</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            System Overview
          </h1>
          <p className="text-xs text-slate-400 mt-1 max-w-xl">
            Real-time telemetry and management console for containerized microservices, build pipelines, and auto-deploy hooks.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            onClick={() => setIsModalOpen(true)}
            variant="primary"
            size="md"
            leftIcon={<Plus className="w-4 h-4" />}
          >
            New Project
          </Button>
        </div>
      </div>

      {/* KPI Telemetry Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Managed Projects */}
        <Card className="relative overflow-hidden group hover:border-[#8B5CF6]/40 transition-all duration-300">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#8B5CF6] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
              <span className="font-medium text-slate-300">Managed Projects</span>
              <div className="w-7 h-7 rounded-lg bg-[#3B82F6]/10 border border-[#3B82F6]/20 flex items-center justify-center text-[#3B82F6]">
                <Package className="w-4 h-4" />
              </div>
            </div>
            <div className="text-3xl font-bold tracking-tight text-white font-mono">
              {isLoading ? <Skeleton className="h-9 w-16" /> : projects.length}
            </div>
            <div className="mt-2 flex items-center gap-2">
              <Badge variant="success" dot={true}>
                {activeProjectsCount} Active
              </Badge>
              <span className="text-[11px] text-slate-500 font-mono">
                {projects.length - activeProjectsCount} idle
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Auto-Deployments */}
        <Card className="relative overflow-hidden group hover:border-[#3B82F6]/40 transition-all duration-300">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#3B82F6] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
              <span className="font-medium text-slate-300">Auto-Deployments</span>
              <div className="w-7 h-7 rounded-lg bg-[#8B5CF6]/10 border border-[#8B5CF6]/20 flex items-center justify-center text-[#A78BFA]">
                <Rocket className="w-4 h-4" />
              </div>
            </div>
            <div className="text-3xl font-bold tracking-tight text-white font-mono">
              {isLoading ? <Skeleton className="h-9 w-16" /> : autoDeployCount}
            </div>
            <div className="mt-2 flex items-center gap-1.5">
              <span className="text-[11px] text-slate-400 font-medium">
                GitHub App & Webhooks
              </span>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-[#3B82F6]/15 text-[#60A5FA] border border-[#3B82F6]/25 font-mono">
                ACTIVE
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Avg Build Duration */}
        <Card className="relative overflow-hidden group hover:border-[#10B981]/40 transition-all duration-300">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#10B981] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
              <span className="font-medium text-slate-300">Avg Build Duration</span>
              <div className="w-7 h-7 rounded-lg bg-[#10B981]/10 border border-[#10B981]/20 flex items-center justify-center text-[#10B981]">
                <Clock className="w-4 h-4" />
              </div>
            </div>
            <div className="text-3xl font-bold tracking-tight text-white font-mono">
              38.2<span className="text-sm font-normal text-slate-400 ml-1">s</span>
            </div>
            <div className="mt-2 flex items-center gap-2">
              <Badge variant="cyan">
                Layer Cache Enabled
              </Badge>
              <span className="text-[11px] text-slate-500 font-mono">
                Docker Engine
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Zero Broker Overhead */}
        <Card className="relative overflow-hidden group hover:border-[#F59E0B]/40 transition-all duration-300">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#F59E0B] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
          <CardContent className="p-4">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
              <span className="font-medium text-slate-300">Queue Architecture</span>
              <div className="w-7 h-7 rounded-lg bg-[#F59E0B]/10 border border-[#F59E0B]/20 flex items-center justify-center text-[#F59E0B]">
                <Zap className="w-4 h-4" />
              </div>
            </div>
            <div className="text-3xl font-bold tracking-tight text-white font-mono">
              0<span className="text-sm font-normal text-slate-400 ml-1">MB RAM</span>
            </div>
            <div className="mt-2 flex items-center gap-1.5">
              <span className="text-[11px] text-[#A78BFA] font-medium">
                PostgreSQL SKIP LOCKED
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Projects Directory Card */}
      <Card>
        <CardHeader className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-4 border-b border-[#252D46]">
          <div>
            <CardTitle className="flex items-center gap-2">
              <span>Your Services & Projects</span>
              <span className="text-xs font-mono font-normal px-2 py-0.5 rounded-full bg-[#171E34] text-slate-300 border border-[#252D46]">
                {filteredProjects.length}
              </span>
            </CardTitle>
            <CardDescription>
              Continuous deployment pipelines, listening container endpoints, and branch configurations.
            </CardDescription>
          </div>

          <div className="flex items-center gap-2.5 w-full sm:w-auto">
            <div className="relative w-full sm:w-64">
              <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Filter services or repos..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full text-xs pl-8 pr-3 py-2 rounded-lg bg-[#080B14] border border-[#252D46] text-slate-200 placeholder-slate-600 focus:outline-none focus:border-[#8B5CF6] focus:ring-1 focus:ring-[#8B5CF6]/30 transition-all font-mono"
              />
              {search && (
                <button
                  onClick={() => setSearch('')}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 text-xs"
                >
                  ✕
                </button>
              )}
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-6 space-y-3">
              <Skeleton className="h-12 w-full rounded-lg" />
              <Skeleton className="h-12 w-full rounded-lg" />
              <Skeleton className="h-12 w-full rounded-lg" />
            </div>
          ) : filteredProjects.length === 0 ? (
            <div className="py-16 text-center space-y-4 px-4">
              <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
                <FolderGit2 className="w-7 h-7" />
              </div>
              <div className="space-y-1">
                <h3 className="text-sm font-semibold text-white">
                  {search ? 'No matching projects found' : 'No projects deployed yet'}
                </h3>
                <p className="text-xs text-slate-400 max-w-sm mx-auto">
                  {search
                    ? `No projects matched the search term "${search}". Try clearing your filter.`
                    : 'Connect a GitHub repository to trigger your first containerized microservice build.'}
                </p>
              </div>
              {!search && (
                <Button
                  onClick={() => setIsModalOpen(true)}
                  variant="primary"
                  size="sm"
                  leftIcon={<Plus className="w-4 h-4" />}
                >
                  Create Your First Project
                </Button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-[#252D46] text-slate-400 bg-[#080B14]/60 font-medium">
                    <th className="py-3 px-4">Service Name</th>
                    <th className="py-3 px-4">Repository & Branch</th>
                    <th className="py-3 px-4">Container Endpoint</th>
                    <th className="py-3 px-4">Auto-Deploy</th>
                    <th className="py-3 px-4">Health Status</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#252D46]/60">
                  {filteredProjects.map((p) => {
                    const isActive = p.status === 'ACTIVE';
                    return (
                      <tr
                        key={p.id}
                        className="hover:bg-[#171E34]/40 transition-colors group"
                      >
                        {/* Service Name */}
                        <td className="py-3.5 px-4 font-semibold text-slate-100">
                          <div className="flex items-center gap-2.5">
                            <span
                              className={`w-2 h-2 rounded-full shrink-0 ${
                                isActive ? 'bg-[#10B981] shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-slate-600'
                              }`}
                            />
                            <button
                              onClick={() => {
                                selectProject(p.id);
                                navigate('/deployments');
                              }}
                              className="font-medium text-white hover:text-[#A78BFA] transition-colors text-left cursor-pointer flex items-center gap-1.5"
                            >
                              <span>{p.name}</span>
                              <ArrowUpRight className="w-3 h-3 opacity-0 group-hover:opacity-100 text-slate-400 transition-opacity" />
                            </button>
                          </div>
                        </td>

                        {/* Repository & Branch */}
                        <td className="py-3.5 px-4 font-mono text-slate-400">
                          <div className="flex items-center gap-1.5">
                            <GitBranch className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                            <span className="truncate max-w-[160px] text-slate-300">
                              {p.repositoryFullName || 'Local Repository'}
                            </span>
                            <span className="text-slate-600">:</span>
                            <span className="px-1.5 py-0.5 rounded bg-[#171E34] border border-[#252D46] text-[#A78BFA] text-[11px] font-semibold">
                              {p.deploymentBranch || 'main'}
                            </span>
                          </div>
                        </td>

                        {/* Local Endpoint */}
                        <td className="py-3.5 px-4 font-mono">
                          {p.port ? (
                            <a
                              href={`http://localhost:${p.port}`}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-[#3B82F6]/10 text-[#60A5FA] border border-[#3B82F6]/25 hover:bg-[#3B82F6]/20 transition-all font-mono text-[11px]"
                            >
                              <span>localhost:{p.port}</span>
                              <ExternalLink className="w-3 h-3" />
                            </a>
                          ) : (
                            <span className="text-slate-500 font-mono text-[11px]">Unassigned</span>
                          )}
                        </td>

                        {/* Auto-Deploy */}
                        <td className="py-3.5 px-4">
                          {p.autoDeploy ? (
                            <Badge variant="blue">ON</Badge>
                          ) : (
                            <Badge variant="neutral">OFF</Badge>
                          )}
                        </td>

                        {/* Status */}
                        <td className="py-3.5 px-4">
                          <Badge
                            variant={isActive ? 'success' : 'neutral'}
                            dot={isActive}
                          >
                            {p.status}
                          </Badge>
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <Button
                              variant="ghost"
                              size="sm"
                              leftIcon={<Terminal className="w-3.5 h-3.5 text-slate-400" />}
                              onClick={() => {
                                selectProject(p.id);
                                navigate('/deployments');
                              }}
                              title="Inspect logs"
                            >
                              Logs
                            </Button>
                            <Button
                              variant="secondary"
                              size="sm"
                              leftIcon={<Rocket className="w-3.5 h-3.5 text-[#A78BFA]" />}
                              onClick={() => {
                                selectProject(p.id);
                                navigate('/deployments');
                              }}
                              title="Trigger deployment"
                            >
                              Deploy
                            </Button>
                            <button
                              disabled={deletingId === p.id}
                              onClick={() => handleDelete(p.id, p.name)}
                              className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer disabled:opacity-50"
                              title="Delete project"
                            >
                              {deletingId === p.id ? (
                                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                              ) : (
                                <Trash2 className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Create Project Modal */}
      <CreateProjectModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
      />
    </div>
  );
};
