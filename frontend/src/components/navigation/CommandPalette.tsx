import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Search, 
  LayoutDashboard, 
  Terminal, 
  Server, 
  KeyRound, 
  Settings, 
  Plus, 
  FolderGit2, 
  LogOut, 
  ExternalLink,
  Command
} from 'lucide-react';
import { useProjects } from '../../context/ProjectContext';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';

interface CommandItem {
  id: string;
  category: 'Navigation' | 'Projects' | 'Actions';
  title: string;
  subtitle?: string;
  icon: React.ReactNode;
  onSelect: () => void;
}

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({ isOpen, onClose }) => {
  const navigate = useNavigate();
  const { projects, selectProject, activeProject } = useProjects();
  const { logout } = useAuth();
  const toast = useToast();

  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  // Focus input on open
  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  // Build command items
  const items: CommandItem[] = [
    // Navigation
    {
      id: 'nav-overview',
      category: 'Navigation',
      title: 'Overview & Projects',
      subtitle: 'View all projects and global metrics',
      icon: <LayoutDashboard className="w-4 h-4 text-purple-400" />,
      onSelect: () => {
        navigate('/');
        onClose();
      },
    },
    {
      id: 'nav-deployments',
      category: 'Navigation',
      title: 'Deployments & Live Streaming',
      subtitle: 'Inspect deployment history and real-time build logs',
      icon: <Terminal className="w-4 h-4 text-blue-400" />,
      onSelect: () => {
        navigate('/deployments');
        onClose();
      },
    },
    {
      id: 'nav-containers',
      category: 'Navigation',
      title: 'Containers & Telemetry',
      subtitle: 'Inspect container ports, lifecycle, and health probes',
      icon: <Server className="w-4 h-4 text-cyan-400" />,
      onSelect: () => {
        navigate('/containers');
        onClose();
      },
    },
    {
      id: 'nav-env-vars',
      category: 'Navigation',
      title: 'Environment Variables & Secrets',
      subtitle: 'Manage AES-256-GCM encrypted configuration',
      icon: <KeyRound className="w-4 h-4 text-indigo-400" />,
      onSelect: () => {
        navigate('/env-vars');
        onClose();
      },
    },
    {
      id: 'nav-settings',
      category: 'Navigation',
      title: 'Project Settings & Webhooks',
      subtitle: 'Configure automated push webhooks and general settings',
      icon: <Settings className="w-4 h-4 text-slate-400" />,
      onSelect: () => {
        navigate('/settings');
        onClose();
      },
    },

    // Projects (Dynamic from project context)
    ...projects.map((p) => ({
      id: `project-${p.id}`,
      category: 'Projects' as const,
      title: p.name,
      subtitle: p.repositoryFullName || 'Local project',
      icon: <FolderGit2 className={`w-4 h-4 ${activeProject?.id === p.id ? 'text-emerald-400' : 'text-slate-400'}`} />,
      onSelect: () => {
        selectProject(p.id);
        toast.info(`Switched active project to "${p.name}"`);
        onClose();
      },
    })),

    // Quick Actions
    {
      id: 'action-add-env',
      category: 'Actions',
      title: 'Add Environment Variable',
      subtitle: 'Inject a new secret or configuration variable',
      icon: <Plus className="w-4 h-4 text-purple-400" />,
      onSelect: () => {
        navigate('/env-vars');
        onClose();
      },
    },
    {
      id: 'action-deploy-logs',
      category: 'Actions',
      title: 'Stream Live Deployment Logs',
      subtitle: 'Connect to real-time server-sent event log stream',
      icon: <ExternalLink className="w-4 h-4 text-blue-400" />,
      onSelect: () => {
        navigate('/deployments');
        onClose();
      },
    },
    {
      id: 'action-logout',
      category: 'Actions',
      title: 'Sign Out',
      subtitle: 'Clear tokens and return to login',
      icon: <LogOut className="w-4 h-4 text-rose-400" />,
      onSelect: () => {
        logout();
        onClose();
      },
    },
  ];

  // Filter items
  const filteredItems = items.filter(
    (item) =>
      item.title.toLowerCase().includes(query.toLowerCase()) ||
      (item.subtitle && item.subtitle.toLowerCase().includes(query.toLowerCase())) ||
      item.category.toLowerCase().includes(query.toLowerCase())
  );

  // Group filtered items
  const groupedItems = filteredItems.reduce<Record<string, CommandItem[]>>((acc, item) => {
    if (!acc[item.category]) acc[item.category] = [];
    acc[item.category].push(item);
    return acc;
  }, {});

  // Keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % (filteredItems.length || 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + filteredItems.length) % (filteredItems.length || 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (filteredItems[selectedIndex]) {
        filteredItems[selectedIndex].onSelect();
      }
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  if (!isOpen) return null;

  let flatIndex = 0;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-start justify-center pt-20 sm:pt-28 p-4"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-[#080B14]/85 backdrop-blur-md transition-opacity"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Palette Container */}
      <div
        className="relative w-full max-w-xl rounded-2xl bg-[#111629] border border-[#252D46] shadow-[0_25px_60px_rgba(8,11,20,0.9),0_0_35px_rgba(139,92,246,0.15)] overflow-hidden z-10 flex flex-col"
        onKeyDown={handleKeyDown}
      >
        {/* Input Bar */}
        <div className="flex items-center px-4 py-3.5 border-b border-[#252D46] bg-[#0D1120]/60">
          <Search className="w-4 h-4 text-purple-400 mr-3 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            placeholder="Type a command or search projects... (Press ESC to close)"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            className="w-full bg-transparent text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none"
          />
          <kbd className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-mono font-medium bg-[#171E34] text-slate-400 border border-[#252D46]">
            ESC
          </kbd>
        </div>

        {/* Results List */}
        <div className="max-h-80 overflow-y-auto p-2 divide-y divide-[#252D46]/40">
          {filteredItems.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">
              No matching commands or projects found.
            </div>
          ) : (
            Object.entries(groupedItems).map(([category, catItems]) => (
              <div key={category} className="py-1.5 first:pt-0 last:pb-0">
                <div className="px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wider text-purple-400/80 font-mono">
                  {category}
                </div>
                <div className="space-y-0.5">
                  {catItems.map((item) => {
                    const currentIndex = flatIndex++;
                    const isSelected = selectedIndex === currentIndex;

                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={item.onSelect}
                        onMouseEnter={() => setSelectedIndex(currentIndex)}
                        className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs transition-colors cursor-pointer text-left ${
                          isSelected
                            ? 'bg-gradient-to-r from-purple-900/40 to-indigo-900/20 text-slate-100 border border-purple-500/30'
                            : 'text-slate-300 hover:bg-[#171E34]/60 border border-transparent'
                        }`}
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className={`p-1.5 rounded-md ${isSelected ? 'bg-purple-500/20' : 'bg-[#171E34]'}`}>
                            {item.icon}
                          </div>
                          <div className="truncate">
                            <div className="font-medium text-slate-200 truncate">{item.title}</div>
                            {item.subtitle && (
                              <div className="text-[11px] text-slate-500 truncate">{item.subtitle}</div>
                            )}
                          </div>
                        </div>

                        {isSelected && (
                          <div className="text-[10px] font-mono text-purple-400 flex items-center gap-1 shrink-0 pl-2">
                            <span>Select</span>
                            <kbd className="px-1.5 py-0.5 rounded bg-[#171E34] border border-purple-500/30">↵</kbd>
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 border-t border-[#252D46] bg-[#0D1120]/40 flex items-center justify-between text-[11px] text-slate-500 font-mono">
          <div className="flex items-center gap-3">
            <span>↑↓ Navigate</span>
            <span>↵ Open</span>
            <span>ESC Close</span>
          </div>
          <div className="flex items-center gap-1 text-purple-400/80">
            <Command className="w-3 h-3" />
            <span>ShipIt Command</span>
          </div>
        </div>
      </div>
    </div>
  );
};
