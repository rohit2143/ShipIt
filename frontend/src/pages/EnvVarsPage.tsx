import React, { useState, useEffect } from 'react';
import { 
  KeyRound, 
  Trash2, 
  Plus, 
  Search, 
  ShieldCheck, 
  Copy, 
  Check, 
  Edit2, 
  Loader2, 
  AlertCircle, 
  UploadCloud, 
  Lock, 
  Unlock, 
  RefreshCw,
  FolderGit2
} from 'lucide-react';
import { useProjects } from '../context/ProjectContext';
import { envVarsApi } from '../api/envVars';
import type { EnvironmentVariable, UpsertEnvVarRequest } from '../types/envVar';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../context/ToastContext';

export const EnvVarsPage: React.FC = () => {
  const { activeProject, projects, selectProject, isLoading: isProjectsLoading } = useProjects();
  const toast = useToast();

  const [envVars, setEnvVars] = useState<EnvironmentVariable[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Add / Edit Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState<UpsertEnvVarRequest>({
    key: '',
    value: '',
    isSecret: false,
  });
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Bulk Import Modal State
  const [isBulkOpen, setIsBulkOpen] = useState(false);
  const [bulkText, setBulkText] = useState('');
  const [bulkError, setBulkError] = useState<string | null>(null);
  const [isBulkSubmitting, setIsBulkSubmitting] = useState(false);

  // Action status
  const [deletingKey, setDeletingKey] = useState<string | null>(null);

  // Load env vars whenever activeProject changes
  const fetchEnvVars = async () => {
    if (!activeProject) return;
    setIsLoading(true);
    try {
      const vars = await envVarsApi.getEnvVars(activeProject.id);
      setEnvVars(Array.isArray(vars) ? vars : []);
    } catch {
      toast.error('Failed to load environment variables.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (activeProject) {
      fetchEnvVars();
    } else {
      setEnvVars([]);
    }
  }, [activeProject?.id]);

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    toast.success(`Copied value for ${key}`);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const openAddModal = () => {
    setIsEditing(false);
    setFormData({ key: '', value: '', isSecret: false });
    setFormError(null);
    setIsModalOpen(true);
  };

  const openEditModal = (item: EnvironmentVariable) => {
    setIsEditing(true);
    setFormData({
      key: item.key,
      value: item.isSecret ? '' : item.value,
      isSecret: item.isSecret,
    });
    setFormError(null);
    setIsModalOpen(true);
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeProject) return;

    // Validate key
    const trimmedKey = formData.key.trim().toUpperCase();
    const keyRegex = /^[A-Z_][A-Z0-9_]*$/;
    if (!keyRegex.test(trimmedKey)) {
      setFormError('Key must start with a letter/underscore and contain only uppercase letters, digits, and underscores.');
      return;
    }

    if (formData.value === undefined || formData.value === null) {
      setFormError('Value is required.');
      return;
    }

    setIsSubmitting(true);
    setFormError(null);

    try {
      await envVarsApi.upsertEnvVar(activeProject.id, {
        key: trimmedKey,
        value: formData.value,
        isSecret: formData.isSecret,
      });

      toast.success(`Variable "${trimmedKey}" saved successfully.`);
      setIsModalOpen(false);
      await fetchEnvVars();
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setFormError(message || 'Failed to save environment variable.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (key: string) => {
    if (!activeProject) return;
    if (!window.confirm(`Are you sure you want to delete environment variable "${key}"?`)) {
      return;
    }

    setDeletingKey(key);
    try {
      await envVarsApi.deleteEnvVar(activeProject.id, key);
      toast.success(`Variable "${key}" deleted successfully.`);
      await fetchEnvVars();
    } catch {
      toast.error(`Failed to delete variable "${key}".`);
    } finally {
      setDeletingKey(null);
    }
  };

  const handleBulkSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeProject) return;

    const lines = bulkText.split('\n');
    const variables: UpsertEnvVarRequest[] = [];
    const errors: string[] = [];

    const keyRegex = /^[A-Z_][A-Z0-9_]*$/;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line || line.startsWith('#')) continue; // Skip comments and empty lines

      const eqIdx = line.indexOf('=');
      if (eqIdx === -1) {
        errors.push(`Line ${i + 1}: Missing "=" sign ("${line.substring(0, 20)}...")`);
        continue;
      }

      const key = line.substring(0, eqIdx).trim().toUpperCase();
      let val = line.substring(eqIdx + 1).trim();

      // Strip surrounding quotes if present
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.substring(1, val.length - 1);
      }

      if (!keyRegex.test(key)) {
        errors.push(`Line ${i + 1}: Invalid key format "${key}". Must match ^[A-Z_][A-Z0-9_]*$`);
        continue;
      }

      // Auto-detect secret based on key keywords
      const looksLikeSecret = /SECRET|PASSWORD|PASS|KEY|TOKEN|AUTH|CREDENTIAL|PRIVATE/i.test(key);

      variables.push({
        key,
        value: val,
        isSecret: looksLikeSecret,
      });
    }

    if (errors.length > 0) {
      setBulkError(errors.slice(0, 3).join('\n') + (errors.length > 3 ? `\n...and ${errors.length - 3} more` : ''));
      return;
    }

    if (variables.length === 0) {
      setBulkError('No valid KEY=VALUE pairs found.');
      return;
    }

    setIsBulkSubmitting(true);
    setBulkError(null);

    try {
      await envVarsApi.batchUpsertEnvVars(activeProject.id, { variables });
      toast.success(`Successfully imported ${variables.length} environment variables.`);
      setIsBulkOpen(false);
      setBulkText('');
      await fetchEnvVars();
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setBulkError(message || 'Failed to import environment variables.');
    } finally {
      setIsBulkSubmitting(false);
    }
  };

  const filteredVars = envVars.filter((v) =>
    v.key.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Header & Project Selector */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-[#8B5CF6]/15 text-[#A78BFA] border border-[#8B5CF6]/30">
              <KeyRound className="w-3 h-3 text-[#A78BFA]" />
              Configuration Vault
            </span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Environment Variables
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Manage configuration keys and cryptographic secrets injected into container environments during build and runtime.
          </p>
        </div>

        {projects.length > 1 && (
          <div className="flex items-center gap-2 bg-[#111629] border border-[#252D46] rounded-xl px-3 py-1.5 text-xs shadow-sm">
            <FolderGit2 className="w-4 h-4 text-[#8B5CF6]" />
            <select
              value={activeProject?.id || ''}
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

      {/* Security Architecture Callout */}
      <div className="rounded-2xl bg-gradient-to-r from-[#171E34] via-[#111629] to-[#0D1120] border border-[#8B5CF6]/30 p-4 text-xs shadow-lg flex items-start gap-3.5 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-48 h-48 bg-[#8B5CF6]/5 rounded-full blur-2xl pointer-events-none" />
        <div className="p-2.5 rounded-xl bg-[#8B5CF6]/15 text-[#A78BFA] border border-[#8B5CF6]/30 shrink-0 shadow-inner">
          <ShieldCheck className="w-5 h-5 text-[#A78BFA]" />
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="font-bold text-white text-sm">AES-256-GCM Cryptographic Protection</span>
            <Badge variant="purple" size="xs">
              AAD Bound
            </Badge>
          </div>
          <p className="text-slate-400 leading-relaxed text-[11px]">
            Variables marked as <strong className="text-slate-200">Secret</strong> are encrypted at rest with AES-256-GCM using authenticated associated data bound to this project ID. Secret values are never exposed over the wire and remain masked (<code className="font-mono text-[#A78BFA]">••••••••••••••••</code>).
          </p>
        </div>
      </div>

      {/* Main Content Area */}
      {!activeProject && !isProjectsLoading ? (
        <Card className="p-12 text-center space-y-4">
          <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
            <FolderGit2 className="w-7 h-7" />
          </div>
          <div>
            <h3 className="text-base font-semibold text-white">No Project Selected</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
              Select or create a project to inspect and configure its environment variables, API tokens, and runtime secrets.
            </p>
          </div>
        </Card>
      ) : (
        <Card className="overflow-hidden">
          {/* Action Toolbar */}
          <CardHeader className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 pb-4 border-b border-[#252D46]">
            {/* Search */}
            <div className="relative flex-1 max-w-sm">
              <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search variables by key..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg bg-[#080B14] border border-[#252D46] text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-[#8B5CF6] font-mono transition-colors"
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

            {/* Buttons */}
            <div className="flex items-center gap-2">
              <Button
                onClick={fetchEnvVars}
                isLoading={isLoading}
                variant="ghost"
                size="sm"
                title="Refresh variables"
                leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
              >
                Refresh
              </Button>

              <Button
                onClick={() => {
                  setBulkText('');
                  setBulkError(null);
                  setIsBulkOpen(true);
                }}
                variant="secondary"
                size="sm"
                leftIcon={<UploadCloud className="w-3.5 h-3.5 text-[#A78BFA]" />}
              >
                Import .env
              </Button>

              <Button
                onClick={openAddModal}
                variant="primary"
                size="sm"
                leftIcon={<Plus className="w-3.5 h-3.5" />}
              >
                Add Variable
              </Button>
            </div>
          </CardHeader>

          {/* Table / List */}
          <CardContent className="p-0">
            {isLoading && envVars.length === 0 ? (
              <div className="p-6 space-y-3">
                <Skeleton className="h-12 w-full rounded-lg" />
                <Skeleton className="h-12 w-full rounded-lg" />
                <Skeleton className="h-12 w-full rounded-lg" />
              </div>
            ) : filteredVars.length === 0 ? (
              <div className="py-16 text-center space-y-4 px-4">
                <div className="w-14 h-14 rounded-2xl bg-[#171E34] border border-[#252D46] flex items-center justify-center mx-auto text-[#A78BFA] shadow-lg shadow-[#8B5CF6]/5">
                  <KeyRound className="w-7 h-7" />
                </div>
                <div className="space-y-1">
                  <h3 className="text-sm font-semibold text-white">
                    {search ? 'No matching variables found' : 'No Environment Variables Configured'}
                  </h3>
                  <p className="text-xs text-slate-400 max-w-sm mx-auto">
                    {search 
                      ? `No variables match "${search}". Try clearing your search.` 
                      : 'Add environment variables such as DATABASE_URL, PORT, or API credentials for your project container.'}
                  </p>
                </div>
                {!search && (
                  <div className="flex items-center justify-center gap-2 pt-2">
                    <Button
                      onClick={openAddModal}
                      variant="primary"
                      size="sm"
                      leftIcon={<Plus className="w-4 h-4" />}
                    >
                      Add Variable
                    </Button>
                    <Button
                      onClick={() => setIsBulkOpen(true)}
                      variant="secondary"
                      size="sm"
                      leftIcon={<UploadCloud className="w-4 h-4" />}
                    >
                      Import .env
                    </Button>
                  </div>
                )}
              </div>
            ) : (
              <div className="divide-y divide-[#252D46]/60">
                {filteredVars.map((item) => (
                  <div 
                    key={item.id}
                    className="p-4 hover:bg-[#171E34]/30 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs group"
                  >
                    {/* Left: Key & Value */}
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-white tracking-wide text-sm">
                          {item.key}
                        </span>
                        {item.isSecret ? (
                          <Badge variant="purple" size="xs">
                            <Lock className="w-2.5 h-2.5 mr-0.5 inline" />
                            Secret
                          </Badge>
                        ) : (
                          <Badge variant="neutral" size="xs">
                            <Unlock className="w-2.5 h-2.5 mr-0.5 inline" />
                            Plaintext
                          </Badge>
                        )}
                      </div>

                      <div className="flex items-center gap-2 font-mono text-slate-400 text-[11px] truncate">
                        {item.isSecret ? (
                          <span className="tracking-widest text-slate-500 font-mono">••••••••••••••••</span>
                        ) : (
                          <span className="text-slate-300 select-all font-mono">{item.value}</span>
                        )}
                      </div>
                    </div>

                    {/* Right: Actions */}
                    <div className="flex items-center gap-1.5 shrink-0">
                      {!item.isSecret && (
                        <Button
                          variant="ghost"
                          size="xs"
                          onClick={() => handleCopy(item.value, item.key)}
                          title="Copy value"
                          leftIcon={
                            copiedKey === item.key ? (
                              <Check className="w-3.5 h-3.5 text-[#10B981]" />
                            ) : (
                              <Copy className="w-3.5 h-3.5 text-slate-400" />
                            )
                          }
                        >
                          {copiedKey === item.key ? 'Copied' : 'Copy'}
                        </Button>
                      )}

                      <Button
                        variant="ghost"
                        size="xs"
                        onClick={() => openEditModal(item)}
                        title="Edit variable"
                        leftIcon={<Edit2 className="w-3.5 h-3.5 text-slate-400" />}
                      >
                        Edit
                      </Button>

                      <button
                        onClick={() => handleDelete(item.key)}
                        disabled={deletingKey === item.key}
                        title="Delete variable"
                        className="p-1.5 rounded-lg hover:bg-rose-500/10 text-slate-400 hover:text-rose-400 transition-colors disabled:opacity-50 cursor-pointer"
                      >
                        {deletingKey === item.key ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin text-rose-400" />
                        ) : (
                          <Trash2 className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Add / Edit Variable Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={isEditing ? 'Edit Variable' : 'Add Environment Variable'}
        size="md"
      >
        <div className="space-y-4">
          {formError && (
            <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{formError}</span>
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-4 text-xs">
            <div>
              <label className="block text-slate-300 font-medium mb-1.5">
                Variable Key <span className="text-rose-400">*</span>
              </label>
              <Input
                type="text"
                placeholder="e.g. DATABASE_URL, PORT, JWT_SECRET"
                value={formData.key}
                disabled={isEditing}
                mono={true}
                onChange={(e) => setFormData({ ...formData, key: e.target.value.toUpperCase() })}
                required
              />
              <p className="text-[11px] text-slate-500 mt-1 font-mono">
                Uppercase letters, numbers, and underscores only.
              </p>
            </div>

            <div>
              <label className="block text-slate-300 font-medium mb-1.5">
                Variable Value <span className="text-rose-400">*</span>
              </label>
              <textarea
                placeholder={
                  isEditing && formData.isSecret 
                    ? 'Enter a new secret value to overwrite (or leave as is)' 
                    : 'e.g. postgresql://user:pass@host:5432/db'
                }
                value={formData.value}
                onChange={(e) => setFormData({ ...formData, value: e.target.value })}
                rows={3}
                className="w-full p-2.5 rounded-lg bg-[#080B14] border border-[#252D46] text-white font-mono text-xs focus:outline-none focus:border-[#8B5CF6] focus:ring-1 focus:ring-[#8B5CF6]/30 resize-none"
                required={!isEditing || !formData.isSecret}
              />
            </div>

            <div className="p-3.5 rounded-xl bg-[#080B14] border border-[#252D46] flex items-center justify-between">
              <div>
                <div className="font-semibold text-white">Encrypt as Secret</div>
                <div className="text-[11px] text-slate-400">
                  AES-256-GCM encryption at rest; masked in the dashboard.
                </div>
              </div>
              <input
                type="checkbox"
                checked={formData.isSecret}
                onChange={(e) => setFormData({ ...formData, isSecret: e.target.checked })}
                className="w-4 h-4 rounded accent-[#8B5CF6] cursor-pointer"
              />
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-[#252D46]">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setIsModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                isLoading={isSubmitting}
                variant="primary"
                size="sm"
              >
                {isEditing ? 'Save Changes' : 'Add Variable'}
              </Button>
            </div>
          </form>
        </div>
      </Modal>

      {/* Bulk Import .env Modal */}
      <Modal
        isOpen={isBulkOpen}
        onClose={() => setIsBulkOpen(false)}
        title="Import Environment Variables (.env)"
        size="lg"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-400 leading-relaxed">
            Paste your <code className="text-white font-mono bg-[#080B14] px-1.5 py-0.5 rounded border border-[#252D46]">.env</code> file content below. Lines with keys containing <code className="font-mono text-[#A78BFA]">SECRET</code>, <code className="font-mono text-[#A78BFA]">PASSWORD</code>, <code className="font-mono text-[#A78BFA]">TOKEN</code>, or <code className="font-mono text-[#A78BFA]">KEY</code> will automatically be encrypted as secrets.
          </p>

          {bulkError && (
            <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs whitespace-pre-line flex items-start gap-2 font-mono">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{bulkError}</span>
            </div>
          )}

          <form onSubmit={handleBulkSubmit} className="space-y-4 text-xs">
            <div>
              <textarea
                placeholder={`PORT=8080\nSPRING_PROFILES_ACTIVE=production\nDATABASE_URL=jdbc:postgresql://localhost:5432/app\nDB_PASSWORD=superSecret123!\nJWT_SECRET=404E635266556A586E327235753878...`}
                value={bulkText}
                onChange={(e) => setBulkText(e.target.value)}
                rows={8}
                className="w-full p-3 rounded-xl bg-[#080B14] border border-[#252D46] text-white font-mono text-xs focus:outline-none focus:border-[#8B5CF6] focus:ring-1 focus:ring-[#8B5CF6]/30"
                required
              />
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-[#252D46]">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setIsBulkOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                isLoading={isBulkSubmitting}
                disabled={!bulkText.trim()}
                variant="primary"
                size="sm"
                leftIcon={<UploadCloud className="w-4 h-4" />}
              >
                Import & Encrypt Variables
              </Button>
            </div>
          </form>
        </div>
      </Modal>
    </div>
  );
};
