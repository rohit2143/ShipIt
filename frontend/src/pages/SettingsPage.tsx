import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  ShieldCheck, 
  Copy, 
  Check, 
  Trash2, 
  AlertTriangle, 
  GitBranch, 
  FolderGit2, 
  Server, 
  Cpu, 
  Zap, 
  Lock, 
  Eye, 
  EyeOff,
  Boxes,
  Loader2
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { projectsApi } from '../api/projects';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Input } from '../components/ui/Input';
import { useToast } from '../context/ToastContext';

export const SettingsPage: React.FC = () => {
  const { activeProject, projects, selectProject, refreshProjects } = useProjects();
  const navigate = useNavigate();
  const toast = useToast();

  const [copiedWebhook, setCopiedWebhook] = useState(false);
  const [copiedSecret, setCopiedSecret] = useState(false);
  const [showSecret, setShowSecret] = useState(false);
  const [autoDeployEnabled, setAutoDeployEnabled] = useState(activeProject?.autoDeploy ?? true);
  const [isDeleting, setIsDeleting] = useState(false);

  const webhookUrl = `${window.location.protocol}//${window.location.hostname}:8080/api/v1/github/webhook`;
  const webhookSecret = 'whsec_09183491823091820391283';

  const handleCopyWebhook = () => {
    navigator.clipboard.writeText(webhookUrl);
    setCopiedWebhook(true);
    toast.success('Webhook ingress URL copied to clipboard');
    setTimeout(() => setCopiedWebhook(false), 2000);
  };

  const handleCopySecret = () => {
    navigator.clipboard.writeText(webhookSecret);
    setCopiedSecret(true);
    toast.success('HMAC-SHA256 secret copied to clipboard');
    setTimeout(() => setCopiedSecret(false), 2000);
  };

  const handleDeleteProject = async () => {
    if (!activeProject) return;
    const confirmName = prompt(
      `To permanently delete "${activeProject.name}", type the project name below:`
    );

    if (confirmName !== activeProject.name) {
      if (confirmName !== null) {
        toast.error('Project name did not match. Deletion aborted.');
      }
      return;
    }

    setIsDeleting(true);
    try {
      await projectsApi.deleteProject(activeProject.id);
      toast.success(`Project "${activeProject.name}" was permanently deleted.`);
      await refreshProjects();
      navigate('/');
    } catch {
      toast.error('Failed to delete project. Please check if containers are locked.');
    } finally {
      setIsDeleting(false);
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
            Select or create a project from the top navigation bar to configure its webhooks, build triggers, and deployment settings.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header & Project Selector */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-[#8B5CF6]/15 text-[#A78BFA] border border-[#8B5CF6]/30">
              <ShieldCheck className="w-3 h-3 text-[#A78BFA]" />
              Project Settings
            </span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Configuration & Webhooks
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Configure GitHub event ingestion, build hooks, runtime resource allocations, and lifecycle policies.
          </p>
        </div>

        {projects.length > 1 && (
          <div className="flex items-center gap-2 bg-[#111629] border border-[#252D46] rounded-xl px-3 py-1.5 text-xs shadow-sm">
            <FolderGit2 className="w-4 h-4 text-[#8B5CF6]" />
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

      {/* GitHub Webhook & Automation Section */}
      <Card>
        <CardHeader className="border-b border-[#252D46] pb-4">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#10B981]/15 text-[#10B981] border border-[#10B981]/30 flex items-center justify-center">
              <ShieldCheck className="w-4 h-4" />
            </div>
            <div>
              <CardTitle>GitHub Webhook & Auto-Deploy Ingress</CardTitle>
              <CardDescription>
                Ingest GitHub push events with cryptographic signature verification (HMAC-SHA256).
              </CardDescription>
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-5 space-y-4 text-xs">
          {/* Webhook Ingress URL */}
          <div>
            <label className="block text-slate-300 font-medium mb-1.5">
              Webhook Ingress URL
            </label>
            <div className="flex items-center gap-2">
              <input 
                type="text" 
                readOnly 
                value={webhookUrl} 
                className="w-full font-mono text-xs px-3 py-2 rounded-lg border border-[#252D46] bg-[#080B14] text-slate-200 select-all focus:outline-none focus:border-[#8B5CF6]" 
              />
              <Button
                onClick={handleCopyWebhook}
                variant="secondary"
                size="sm"
                leftIcon={copiedWebhook ? <Check className="w-3.5 h-3.5 text-[#10B981]" /> : <Copy className="w-3.5 h-3.5 text-slate-400" />}
              >
                {copiedWebhook ? 'Copied' : 'Copy'}
              </Button>
            </div>
            <p className="text-[11px] text-slate-500 mt-1">
              Configure this payload URL under your GitHub repository&apos;s Settings &gt; Webhooks with content type <code className="font-mono text-slate-400">application/json</code>.
            </p>
          </div>

          {/* HMAC Secret */}
          <div>
            <label className="block text-slate-300 font-medium mb-1.5">
              HMAC-SHA256 Webhook Secret
            </label>
            <div className="flex items-center gap-2">
              <div className="relative w-full">
                <input 
                  type={showSecret ? 'text' : 'password'} 
                  readOnly 
                  value={webhookSecret} 
                  className="w-full font-mono text-xs px-3 py-2 rounded-lg border border-[#252D46] bg-[#080B14] text-slate-200 select-all pr-10 focus:outline-none focus:border-[#8B5CF6]" 
                />
                <button
                  type="button"
                  onClick={() => setShowSecret(!showSecret)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors cursor-pointer"
                  title={showSecret ? 'Mask secret' : 'Reveal secret'}
                >
                  {showSecret ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>

              <Button
                onClick={handleCopySecret}
                variant="secondary"
                size="sm"
                leftIcon={copiedSecret ? <Check className="w-3.5 h-3.5 text-[#10B981]" /> : <Copy className="w-3.5 h-3.5 text-slate-400" />}
              >
                {copiedSecret ? 'Copied' : 'Copy'}
              </Button>
            </div>
            <p className="text-[11px] text-slate-500 mt-1">
              Used to compute and verify the <code className="font-mono text-slate-400">X-Hub-Signature-256</code> header sent on each GitHub push payload.
            </p>
          </div>

          {/* Push-to-Deploy Toggle */}
          <div className="pt-2">
            <label className="flex items-start gap-3 p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] cursor-pointer hover:border-slate-600 transition-colors">
              <input 
                type="checkbox" 
                checked={autoDeployEnabled}
                onChange={(e) => {
                  setAutoDeployEnabled(e.target.checked);
                  toast.success(`Auto-deploy ${e.target.checked ? 'enabled' : 'disabled'} for branch ${activeProject.deploymentBranch || 'main'}`);
                }}
                className="w-4 h-4 mt-0.5 rounded accent-[#8B5CF6] cursor-pointer" 
              />
              <div className="space-y-0.5">
                <div className="font-semibold text-white flex items-center gap-2">
                  <span>Automatic Push-to-Deploy</span>
                  <Badge variant={autoDeployEnabled ? 'success' : 'neutral'} size="xs">
                    {autoDeployEnabled ? 'ENABLED' : 'PAUSED'}
                  </Badge>
                </div>
                <p className="text-[11px] text-slate-400">
                  Trigger an automated zero-broker build job whenever a push occurs to branch{' '}
                  <code className="font-mono bg-[#111629] px-1.5 py-0.5 rounded border border-[#252D46] text-[#A78BFA]">
                    {activeProject.deploymentBranch || 'main'}
                  </code>
                </p>
              </div>
            </label>
          </div>
        </CardContent>
      </Card>

      {/* Runtime & Container Defaults Section */}
      <Card>
        <CardHeader className="border-b border-[#252D46] pb-4">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#3B82F6]/15 text-[#3B82F6] border border-[#3B82F6]/30 flex items-center justify-center">
              <Boxes className="w-4 h-4" />
            </div>
            <div>
              <CardTitle>Runtime Engine & Port Mapping</CardTitle>
              <CardDescription>
                Virtualization engine specifications and port routing configurations.
              </CardDescription>
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-5 text-xs">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] space-y-1">
              <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5">
                <Server className="w-3.5 h-3.5 text-[#3B82F6]" />
                <span>Host Port Mapping</span>
              </span>
              <div className="text-white font-mono font-bold text-sm">
                localhost:{activeProject.port || 10042}
              </div>
              <p className="text-[10px] text-slate-500 font-mono">
                Forwarded to container 8080/TCP
              </p>
            </div>

            <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] space-y-1">
              <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5">
                <Cpu className="w-3.5 h-3.5 text-[#8B5CF6]" />
                <span>Memory Allocation Limit</span>
              </span>
              <div className="text-white font-mono font-bold text-sm">
                512 MB Max RAM
              </div>
              <p className="text-[10px] text-slate-500 font-mono">
                Hard cgroup limit with 256 MB JVM Heap
              </p>
            </div>

            <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] space-y-1">
              <span className="text-slate-400 text-[11px] font-medium flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5 text-[#10B981]" />
                <span>Health Probe Endpoint</span>
              </span>
              <div className="text-white font-mono font-bold text-sm">
                /actuator/health
              </div>
              <p className="text-[10px] text-slate-500 font-mono">
                HTTP GET with 5s timeout probe
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Danger Zone */}
      <div className="rounded-2xl border border-rose-500/30 bg-rose-500/5 p-5 space-y-4">
        <div className="flex items-center gap-2.5 pb-3 border-b border-rose-500/20 text-rose-400">
          <AlertTriangle className="w-5 h-5 shrink-0" />
          <div>
            <h3 className="font-bold text-sm text-white">Danger Zone</h3>
            <p className="text-xs text-rose-300/80">Irreversible project removal and resource deallocation.</p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 text-xs">
          <div className="space-y-1 max-w-lg">
            <h4 className="font-semibold text-white">Delete this project</h4>
            <p className="text-slate-400 leading-relaxed text-[11px]">
              Once deleted, all deployment logs, Docker containers, images, and encrypted environment variables associated with <strong className="text-white">{activeProject.name}</strong> will be permanently purged.
            </p>
          </div>

          <Button
            onClick={handleDeleteProject}
            isLoading={isDeleting}
            variant="danger"
            size="sm"
            leftIcon={<Trash2 className="w-3.5 h-3.5" />}
          >
            Delete Project
          </Button>
        </div>
      </div>
    </div>
  );
};
