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
  FolderGit2
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { CreateProjectModal } from '../components/projects/CreateProjectModal';
import { projectsApi } from '../api/projects';

export const OverviewPage: React.FC = () => {
  const { projects, selectProject, refreshProjects, isLoading } = useProjects();
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const filteredProjects = projects.filter((p) =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    (p.repositoryFullName && p.repositoryFullName.toLowerCase().includes(search.toLowerCase()))
  );

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Are you sure you want to delete project "${name}"? This cannot be undone.`)) {
      return;
    }

    setDeletingId(id);
    try {
      await projectsApi.deleteProject(id);
      await refreshProjects();
    } catch {
      alert('Failed to delete project.');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
            <span>Managed Projects</span>
            <Package className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-2xl font-bold tracking-tight text-slate-100">{projects.length}</div>
          <div className="text-[11px] text-emerald-400 flex items-center gap-1 mt-1 font-medium">
            <span>● {projects.filter((p) => p.status === 'ACTIVE').length} active</span>
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
            <span>Auto-Deployments</span>
            <Rocket className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="text-2xl font-bold tracking-tight text-slate-100">
            {projects.filter((p) => p.autoDeploy).length}
          </div>
          <div className="text-[11px] text-slate-400 mt-1 font-medium">
            Webhook monitored
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
            <span>Avg Build Duration</span>
            <Clock className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold tracking-tight text-slate-100">38.2s</div>
          <div className="text-[11px] text-emerald-400 mt-1 font-medium">
            Docker cached
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
            <span>Zero Broker Overhead</span>
            <Zap className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-bold tracking-tight text-slate-100">0 MB</div>
          <div className="text-[11px] text-indigo-400 mt-1 font-medium">
            PostgreSQL SKIP LOCKED
          </div>
        </div>
      </div>

      {/* Projects Table Card */}
      <div className="rounded-xl bg-slate-900 border border-slate-800 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-slate-100">Your Projects</h2>
            <p className="text-xs text-slate-400">
              Repositories monitored for push events and automated container deployments.
            </p>
          </div>
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <input 
              type="text" 
              placeholder="Filter projects..." 
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="text-xs px-3 py-1.5 rounded-md border border-slate-800 bg-slate-950 w-full sm:w-60 focus:outline-none focus:border-blue-500 text-slate-200 placeholder-slate-600" 
            />
            <button 
              onClick={() => setIsModalOpen(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-md bg-blue-600 hover:bg-blue-500 text-white whitespace-nowrap transition-colors shadow-sm"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>New Project</span>
            </button>
          </div>
        </div>

        {isLoading ? (
          <div className="py-12 flex flex-col items-center justify-center space-y-2 text-slate-400 text-xs">
            <Loader2 className="w-6 h-6 animate-spin text-blue-400" />
            <span>Loading projects...</span>
          </div>
        ) : filteredProjects.length === 0 ? (
          <div className="py-14 text-center space-y-3">
            <div className="w-12 h-12 rounded-xl bg-slate-800/80 border border-slate-700/60 flex items-center justify-center mx-auto text-slate-400">
              <FolderGit2 className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-slate-200">No projects found</h3>
              <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
                {search ? 'No projects match your filter query.' : 'Connect a GitHub repository and deploy your first microservice.'}
              </p>
            </div>
            {!search && (
              <button
                onClick={() => setIsModalOpen(true)}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition-all"
              >
                <Plus className="w-4 h-4" />
                <span>Create Your First Project</span>
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-slate-400 bg-slate-950/50">
                  <th className="py-3 px-4 font-medium">Project Name</th>
                  <th className="py-3 px-4 font-medium">Repository & Branch</th>
                  <th className="py-3 px-4 font-medium">Local Endpoint</th>
                  <th className="py-3 px-4 font-medium">Auto-Deploy</th>
                  <th className="py-3 px-4 font-medium">Status</th>
                  <th className="py-3 px-4 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {filteredProjects.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3.5 px-4 font-semibold text-slate-200 flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                      <button 
                        onClick={() => {
                          selectProject(p.id);
                          navigate('/deployments');
                        }}
                        className="hover:underline hover:text-blue-400 text-left cursor-pointer"
                      >
                        {p.name}
                      </button>
                    </td>
                    <td className="py-3.5 px-4 font-mono text-slate-400">
                      {p.repositoryFullName || 'Local Repo'} :{' '}
                      <span className="text-slate-200 font-semibold">{p.deploymentBranch || 'main'}</span>
                    </td>
                    <td className="py-3.5 px-4">
                      {p.port ? (
                        <a 
                          href={`http://localhost:${p.port}`} 
                          target="_blank" 
                          rel="noreferrer" 
                          className="text-indigo-400 hover:underline flex items-center gap-1 font-mono"
                        >
                          localhost:{p.port}
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      ) : (
                        <span className="text-slate-500 font-mono">Not deployed</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4">
                      {p.autoDeploy ? (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20">
                          ON
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-800 text-slate-400 border border-slate-700">
                          OFF
                        </span>
                      )}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        {p.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right space-x-2">
                      <button 
                        onClick={() => {
                          selectProject(p.id);
                          navigate('/deployments');
                        }} 
                        className="px-2.5 py-1 rounded border border-slate-700 hover:bg-slate-800 text-[11px] font-medium text-slate-300 transition-colors cursor-pointer"
                      >
                        Logs
                      </button>
                      <button 
                        onClick={() => {
                          selectProject(p.id);
                          navigate('/deployments');
                        }}
                        className="px-2.5 py-1 rounded bg-blue-600 hover:bg-blue-500 text-white text-[11px] font-medium transition-colors cursor-pointer"
                      >
                        Deploy
                      </button>
                      <button 
                        disabled={deletingId === p.id}
                        onClick={() => handleDelete(p.id, p.name)}
                        className="p-1 rounded text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
                        title="Delete project"
                      >
                        {deletingId === p.id ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <Trash2 className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <CreateProjectModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
      />
    </div>
  );
};
