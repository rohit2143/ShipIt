import React, { useState, useEffect } from 'react';
import { 
  X, 
  GitBranch, 
  Github, 
  ExternalLink, 
  Loader2, 
  AlertCircle,
  FolderGit2
} from 'lucide-react';
import { githubApi } from '../../api/github';
import { projectsApi } from '../../api/projects';
import type { GitHubConnection, GitHubRepositoryItem, GitHubConnectedRepository, GitHubBranch } from '../../types/github';
import { useProjects } from '../../context/ProjectContext';
import axios from 'axios';

interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CreateProjectModal: React.FC<CreateProjectModalProps> = ({ isOpen, onClose }) => {
  const { refreshProjects, selectProject } = useProjects();

  const [connections, setConnections] = useState<GitHubConnection[]>([]);
  const [selectedConnection, setSelectedConnection] = useState<string>('');
  
  const [repositories, setRepositories] = useState<GitHubRepositoryItem[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<GitHubRepositoryItem | null>(null);

  const [branches, setBranches] = useState<GitHubBranch[]>([]);
  const [deploymentBranch, setDeploymentBranch] = useState('main');

  const [projectName, setProjectName] = useState('');
  const [rootDirectory, setRootDirectory] = useState('');
  const [autoDeploy, setAutoDeploy] = useState(true);

  const [isLoadingRepos, setIsLoadingRepos] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    const loadConnections = async () => {
      try {
        const conns = await githubApi.getConnections();
        setConnections(conns);
        if (conns.length > 0) {
          setSelectedConnection(conns[0].id);
        }
      } catch {
        setError('Failed to load GitHub connections.');
      }
    };

    loadConnections();
  }, [isOpen]);

  useEffect(() => {
    if (!selectedConnection) return;

    const loadRepos = async () => {
      setIsLoadingRepos(true);
      setError(null);
      try {
        const resp = await githubApi.getInstallationRepositories(selectedConnection);
        setRepositories(resp.repositories);
        if (resp.repositories.length > 0) {
          handleSelectRepo(resp.repositories[0]);
        }
      } catch {
        setError('Could not fetch repositories for this GitHub App installation.');
      } finally {
        setIsLoadingRepos(false);
      }
    };

    loadRepos();
  }, [selectedConnection]);

  const handleSelectRepo = async (repo: GitHubRepositoryItem) => {
    setSelectedRepo(repo);
    setProjectName(repo.name);
    const defBranch = repo.defaultBranch || repo.default_branch || 'main';
    setDeploymentBranch(defBranch);

    // Fetch branches if repo is connected, or fallback to defaultBranch
    try {
      const connectedRepos = await githubApi.getConnectedRepositories();
      const existing = connectedRepos.find((cr: GitHubConnectedRepository) => cr.githubRepoId === repo.id);
      if (existing) {
        const branchList = await githubApi.getRepositoryBranches(existing.id);
        setBranches(branchList);
      } else {
        setBranches([{ name: defBranch, commitSha: '', isProtected: false }]);
      }
    } catch {
      setBranches([{ name: defBranch, commitSha: '', isProtected: false }]);
    }
  };

  const handleConnectGitHubApp = async () => {
    try {
      const { installUrl } = await githubApi.getInstallUrl();
      window.open(installUrl, '_blank');
    } catch {
      setError('Failed to generate GitHub App installation URL.');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRepo || !selectedConnection) {
      setError('Please select a repository.');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      // 1. Ensure repo is connected in backend
      const connected = await githubApi.connectRepository(selectedConnection, selectedRepo.id);

      // 2. Create the project
      const newProject = await projectsApi.createProject({
        repositoryId: connected.id,
        name: projectName.trim(),
        deploymentBranch,
        rootDirectory: rootDirectory.trim() || undefined,
        autoDeploy,
      });

      await refreshProjects();
      selectProject(newProject.id);
      onClose();
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setError(err.response?.data?.message || err.response?.data?.error || 'Failed to create project.');
      } else {
        setError('An unexpected error occurred.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-5 text-slate-200">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-blue-600/20 border border-blue-500/30 text-blue-400 flex items-center justify-center">
              <FolderGit2 className="w-4 h-4" />
            </div>
            <h3 className="font-bold text-sm text-slate-100">Create New Project</h3>
          </div>
          <button 
            onClick={onClose}
            className="text-slate-400 hover:text-slate-200 p-1 rounded-md hover:bg-slate-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {error && (
          <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {connections.length === 0 ? (
          <div className="text-center py-6 space-y-4">
            <div className="w-12 h-12 rounded-xl bg-slate-800 flex items-center justify-center mx-auto text-slate-300">
              <Github className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-sm font-semibold text-slate-200">Connect GitHub</h4>
              <p className="text-xs text-slate-400 max-w-xs mx-auto mt-1">
                Install the ShipIt GitHub App to grant access to your repositories.
              </p>
            </div>
            <button
              onClick={handleConnectGitHubApp}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition-all"
            >
              <span>Install GitHub App</span>
              <ExternalLink className="w-3.5 h-3.5" />
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4 text-xs">
            {/* Repository Select */}
            <div>
              <label className="block text-slate-300 font-medium mb-1">
                Select Repository
              </label>
              {isLoadingRepos ? (
                <div className="p-3 rounded-lg border border-slate-800 bg-slate-950 flex items-center gap-2 text-slate-400">
                  <Loader2 className="w-4 h-4 animate-spin text-blue-400" />
                  <span>Loading repositories...</span>
                </div>
              ) : (
                <select
                  value={selectedRepo?.id || ''}
                  onChange={(e) => {
                    const found = repositories.find((r) => r.id === Number(e.target.value));
                    if (found) handleSelectRepo(found);
                  }}
                  className="w-full p-2.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-200 focus:outline-none focus:border-blue-500"
                >
                  {repositories.map((repo) => (
                    <option key={repo.id} value={repo.id}>
                      {repo.fullName || repo.full_name || repo.name} {(repo.private ?? repo.isPrivate) ? '(Private)' : ''}
                    </option>
                  ))}
                </select>
              )}
            </div>

            {/* Project Name */}
            <div>
              <label className="block text-slate-300 font-medium mb-1">
                Project Name
              </label>
              <input
                type="text"
                required
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                placeholder="e.g. payment-service"
                className="w-full p-2.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-200 focus:outline-none focus:border-blue-500 font-mono text-xs"
              />
            </div>

            {/* Deployment Branch */}
            <div>
              <label className="block text-slate-300 font-medium mb-1">
                Deployment Branch
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500">
                  <GitBranch className="w-4 h-4" />
                </div>
                <input
                  type="text"
                  required
                  value={deploymentBranch}
                  onChange={(e) => setDeploymentBranch(e.target.value)}
                  placeholder="main"
                  list="branches-list"
                  className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-200 focus:outline-none focus:border-blue-500 font-mono text-xs"
                />
                <datalist id="branches-list">
                  {branches.map((b) => (
                    <option key={b.name} value={b.name} />
                  ))}
                </datalist>
              </div>
            </div>

            {/* Root Directory */}
            <div>
              <label className="block text-slate-300 font-medium mb-1">
                Root Directory <span className="text-slate-500 font-normal">(Optional)</span>
              </label>
              <input
                type="text"
                value={rootDirectory}
                onChange={(e) => setRootDirectory(e.target.value)}
                placeholder="./"
                className="w-full p-2.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-200 focus:outline-none focus:border-blue-500 font-mono text-xs"
              />
            </div>

            {/* Auto-Deploy Toggle */}
            <div className="pt-2 flex items-center gap-2.5">
              <input
                type="checkbox"
                id="autoDeployModal"
                checked={autoDeploy}
                onChange={(e) => setAutoDeploy(e.target.checked)}
                className="accent-blue-600 rounded cursor-pointer"
              />
              <label htmlFor="autoDeployModal" className="text-slate-300 cursor-pointer select-none">
                Auto-deploy on GitHub push events to <code className="text-blue-400 font-mono">{deploymentBranch || 'main'}</code>
              </label>
            </div>

            {/* Footer Buttons */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-lg border border-slate-800 hover:bg-slate-800 text-slate-300 font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSubmitting || !selectedRepo}
                className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold shadow transition-all disabled:opacity-50"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Creating...</span>
                  </>
                ) : (
                  <span>Create Project</span>
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
