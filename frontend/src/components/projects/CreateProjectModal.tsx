import React, { useState, useEffect, useCallback } from 'react';
import { 
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
import { useToast } from '../../context/ToastContext';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import axios from 'axios';

interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CreateProjectModal: React.FC<CreateProjectModalProps> = ({ isOpen, onClose }) => {
  const { refreshProjects, selectProject } = useProjects();
  const toast = useToast();

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

  // Define handleSelectRepo with useCallback before calling in effects
  const handleSelectRepo = useCallback(async (repo: GitHubRepositoryItem) => {
    setSelectedRepo(repo);
    setProjectName(repo.name);
    const defBranch = repo.defaultBranch || repo.default_branch || 'main';
    setDeploymentBranch(defBranch);

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
  }, []);

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
  }, [selectedConnection, handleSelectRepo]);

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
      const connected = await githubApi.connectRepository(selectedConnection, selectedRepo.id);

      const newProject = await projectsApi.createProject({
        repositoryId: connected.id,
        name: projectName.trim(),
        deploymentBranch,
        rootDirectory: rootDirectory.trim() || undefined,
        autoDeploy,
      });

      await refreshProjects();
      selectProject(newProject.id);
      toast.success(`Project "${newProject.name}" created successfully!`);
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

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center gap-2">
          <FolderGit2 className="w-4 h-4 text-purple-400" />
          <span>Create New Project</span>
        </div>
      }
      description="Connect a GitHub repository to build autonomous push-to-deploy pipelines."
      size="lg"
    >
      <div className="space-y-4">
        {error && (
          <div className="p-3 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {connections.length === 0 ? (
          <div className="p-6 rounded-xl bg-[#080B14] border border-[#252D46] text-center space-y-3">
            <Github className="w-8 h-8 text-slate-500 mx-auto" />
            <h4 className="text-xs font-semibold text-slate-200">No GitHub App Installed</h4>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Install the ShipIt GitHub App to grant secure repository permissions.
            </p>
            <Button
              variant="primary"
              size="sm"
              onClick={handleConnectGitHubApp}
              leftIcon={<ExternalLink className="w-3.5 h-3.5" />}
            >
              Install GitHub App
            </Button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4 text-xs">
            {/* Connection Selector */}
            <div>
              <label className="block text-slate-300 font-medium mb-1.5">
                GitHub Account / Organization
              </label>
              <select
                value={selectedConnection}
                onChange={(e) => setSelectedConnection(e.target.value)}
                className="w-full p-2.5 rounded-lg border border-[#252D46] bg-[#080B14] text-slate-200 text-xs focus:outline-none focus:border-purple-500"
              >
                {connections.map((conn) => (
                  <option key={conn.id} value={conn.id}>
                    {conn.accountLogin} ({conn.accountType})
                  </option>
                ))}
              </select>
            </div>

            {/* Repository Selector */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-slate-300 font-medium">Select Repository</label>
                <button
                  type="button"
                  onClick={handleConnectGitHubApp}
                  className="text-[11px] text-purple-400 hover:text-purple-300 flex items-center gap-1"
                >
                  <span>Add More Repos</span>
                  <ExternalLink className="w-3 h-3" />
                </button>
              </div>

              {isLoadingRepos ? (
                <div className="p-3 rounded-lg border border-[#252D46] bg-[#080B14] flex items-center justify-center gap-2 text-slate-400 text-xs">
                  <Loader2 className="w-3.5 h-3.5 animate-spin text-purple-400" />
                  <span>Loading repositories...</span>
                </div>
              ) : (
                <select
                  value={selectedRepo?.id || ''}
                  onChange={(e) => {
                    const found = repositories.find((r) => r.id === Number(e.target.value));
                    if (found) handleSelectRepo(found);
                  }}
                  className="w-full p-2.5 rounded-lg border border-[#252D46] bg-[#080B14] text-slate-200 text-xs focus:outline-none focus:border-purple-500"
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
              <Input
                label="Project Name"
                required
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                placeholder="e.g. payment-service"
                mono
              />
            </div>

            {/* Deployment Branch */}
            <div>
              <Input
                label="Deployment Branch"
                required
                value={deploymentBranch}
                onChange={(e) => setDeploymentBranch(e.target.value)}
                placeholder="main"
                leftIcon={<GitBranch className="w-3.5 h-3.5 text-slate-500" />}
                mono
                list="branches-list"
              />
              <datalist id="branches-list">
                {branches.map((b) => (
                  <option key={b.name} value={b.name} />
                ))}
              </datalist>
            </div>

            {/* Root Directory */}
            <div>
              <Input
                label="Root Directory (Optional)"
                value={rootDirectory}
                onChange={(e) => setRootDirectory(e.target.value)}
                placeholder="./"
                helperText="Subfolder containing build files, e.g. ./backend or ./"
                mono
              />
            </div>

            {/* Auto-Deploy Toggle */}
            <div className="p-3 rounded-xl bg-[#080B14] border border-[#252D46] flex items-center justify-between">
              <div>
                <div className="font-medium text-slate-200">Automatic Push-to-Deploy</div>
                <div className="text-[11px] text-slate-400">
                  Trigger automated container builds on GitHub pushes to <code className="text-purple-400 font-mono">{deploymentBranch || 'main'}</code>.
                </div>
              </div>
              <input
                type="checkbox"
                id="autoDeployModal"
                checked={autoDeploy}
                onChange={(e) => setAutoDeploy(e.target.checked)}
                className="w-4 h-4 rounded accent-purple-600 cursor-pointer"
              />
            </div>

            {/* Footer Buttons */}
            <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-[#252D46]">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={onClose}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                size="sm"
                disabled={isSubmitting || !selectedRepo}
                isLoading={isSubmitting}
              >
                Create Project
              </Button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
};
