import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { 
  Rocket, 
  LayoutDashboard, 
  Terminal, 
  Server, 
  KeyRound, 
  Settings, 
  ChevronDown, 
  LogOut,
  Cpu,
  Check,
  PanelLeftClose,
  PanelLeftOpen,
  X,
  Zap
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useProjects } from '../../context/ProjectContext';
import { jobsApi } from '../../api/jobs';
import type { JobQueueStatsResponse } from '../../types/job';

interface SidebarProps {
  isCollapsed?: boolean;
  onToggleCollapse?: () => void;
  isMobileOpen?: boolean;
  onMobileClose?: () => void;
}

interface NavItem {
  to: string;
  label: string;
  icon: React.ReactNode;
  end?: boolean;
  badge?: string;
}

interface NavCategory {
  label: string;
  items: NavItem[];
}

export const Sidebar: React.FC<SidebarProps> = ({
  isCollapsed = false,
  onToggleCollapse,
  isMobileOpen = false,
  onMobileClose,
}) => {
  const { user, logout } = useAuth();
  const { projects, activeProject, selectProject } = useProjects();
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [queueStats, setQueueStats] = useState<JobQueueStatsResponse | null>(null);

  useEffect(() => {
    const fetchQueue = () => {
      jobsApi.getQueueStats()
        .then((res) => setQueueStats(res))
        .catch(() => {});
    };
    fetchQueue();
    const interval = setInterval(fetchQueue, 15000);
    return () => clearInterval(interval);
  }, []);

  const initials = user?.name
    ? user.name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .substring(0, 2)
    : 'U';

  const activeProjectName = activeProject?.name || 'Select Project';

  const navCategories: NavCategory[] = [
    {
      label: 'Overview',
      items: [
        {
          to: '/',
          end: true,
          label: 'Dashboard',
          icon: <LayoutDashboard className="w-4 h-4 shrink-0" />,
          badge: projects.length.toString(),
        },
      ],
    },
    {
      label: 'Applications',
      items: [
        {
          to: '/deployments',
          label: 'Deployments',
          icon: <Terminal className="w-4 h-4 shrink-0" />,
          badge: 'SSE',
        },
      ],
    },
    {
      label: 'Infrastructure',
      items: [
        {
          to: '/containers',
          label: 'Containers',
          icon: <Server className="w-4 h-4 shrink-0" />,
        },
      ],
    },
    {
      label: 'Configuration',
      items: [
        {
          to: '/env-vars',
          label: 'Environment',
          icon: <KeyRound className="w-4 h-4 shrink-0" />,
          badge: 'AES',
        },
        {
          to: '/settings',
          label: 'Settings',
          icon: <Settings className="w-4 h-4 shrink-0" />,
        },
      ],
    },
  ];

  return (
    <>
      {/* Mobile Drawer Backdrop */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 bg-[#080B14]/80 backdrop-blur-sm z-40 lg:hidden"
          onClick={onMobileClose}
          aria-hidden="true"
        />
      )}

      {/* Main Sidebar Element */}
      <aside
        className={`fixed lg:sticky top-0 h-screen z-50 lg:z-30 shrink-0 bg-[#0D1120] border-r border-[#252D46] flex flex-col justify-between transition-all duration-300 ease-in-out select-none ${
          isCollapsed ? 'w-20' : 'w-64'
        } ${
          isMobileOpen 
            ? 'translate-x-0' 
            : '-translate-x-full lg:translate-x-0'
        }`}
      >
        {/* Top Section */}
        <div className="p-4 space-y-4 overflow-y-auto">
          {/* Brand Header */}
          {isCollapsed ? (
            <div className="flex flex-col items-center gap-2.5">
              <div 
                className="w-9 h-9 rounded-xl bg-gradient-to-tr from-purple-700 via-purple-600 to-indigo-500 text-white flex items-center justify-center shadow-[0_0_20px_-3px_rgba(139,92,246,0.5)] border border-purple-400/30 shrink-0"
                title="ShipIt"
              >
                <Rocket className="w-4 h-4 text-purple-100" />
              </div>
              {onToggleCollapse && (
                <button
                  type="button"
                  onClick={onToggleCollapse}
                  className="hidden lg:flex p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-[#171E34] transition-colors cursor-pointer"
                  title="Expand sidebar"
                >
                  <PanelLeftOpen className="w-4 h-4" />
                </button>
              )}
            </div>
          ) : (
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-purple-700 via-purple-600 to-indigo-500 text-white flex items-center justify-center shadow-[0_0_20px_-3px_rgba(139,92,246,0.5)] border border-purple-400/30 shrink-0">
                  <Rocket className="w-4 h-4 text-purple-100" />
                </div>
                <div className="flex flex-col truncate">
                  <div className="flex items-center gap-1.5">
                    <span className="font-bold tracking-tight text-sm text-slate-100 font-mono">
                      ShipIt
                    </span>
                    <span className="text-[9px] uppercase font-bold px-1.5 py-0.2 rounded-full bg-purple-500/10 text-purple-300 border border-purple-500/25">
                      PaaS
                    </span>
                  </div>
                  <span className="text-[10px] text-slate-400 truncate">
                    Autonomous Cloud
                  </span>
                </div>
              </div>

              {/* Desktop Collapse Toggle */}
              <div className="hidden lg:flex items-center">
                {onToggleCollapse && (
                  <button
                    type="button"
                    onClick={onToggleCollapse}
                    className="p-1 rounded-md text-slate-400 hover:text-slate-200 hover:bg-[#171E34] transition-colors cursor-pointer"
                    title="Collapse sidebar"
                  >
                    <PanelLeftClose className="w-4 h-4" />
                  </button>
                )}
              </div>

              {/* Mobile Close Button */}
              <button
                type="button"
                onClick={onMobileClose}
                className="lg:hidden p-1 rounded-md text-slate-400 hover:text-slate-200 hover:bg-[#171E34] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* Active Project Selector */}
          {!isCollapsed && (
            <div className="relative pt-1">
              <label className="block text-[10px] font-semibold uppercase tracking-wider text-slate-400 font-mono mb-1.5">
                Active Project
              </label>
              <button
                type="button"
                onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                className="w-full flex items-center justify-between text-xs font-medium p-2.5 rounded-xl border border-[#252D46] bg-[#111629] hover:border-purple-500/40 hover:shadow-[0_0_15px_-3px_rgba(139,92,246,0.15)] transition-all cursor-pointer"
              >
                <div className="flex items-center gap-2 truncate">
                  <span
                    className={`w-2 h-2 rounded-full shrink-0 ${
                      activeProject ? 'bg-emerald-400 shadow-[0_0_8px_rgba(16,185,129,0.8)]' : 'bg-slate-600'
                    }`}
                  />
                  <span className="truncate font-semibold text-slate-200">
                    {activeProjectName}
                  </span>
                </div>
                <ChevronDown
                  className={`w-3.5 h-3.5 text-slate-400 shrink-0 transition-transform ${
                    isDropdownOpen ? 'rotate-180' : ''
                  }`}
                />
              </button>

              {/* Dropdown Menu */}
              {isDropdownOpen && (
                <div className="absolute top-full left-0 right-0 mt-1 py-1.5 bg-[#111629] border border-[#252D46] rounded-xl shadow-2xl z-50 max-h-56 overflow-y-auto divide-y divide-[#252D46]/40">
                  {projects.length === 0 ? (
                    <div className="px-3 py-2 text-xs text-slate-500">No projects created yet</div>
                  ) : (
                    projects.map((p) => (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => {
                          selectProject(p.id);
                          setIsDropdownOpen(false);
                        }}
                        className="w-full flex items-center justify-between px-3 py-2 text-xs text-left hover:bg-[#171E34] transition-colors cursor-pointer"
                      >
                        <div className="truncate">
                          <div className="font-medium text-slate-200 truncate">{p.name}</div>
                          {p.repositoryFullName && (
                            <div className="text-[10px] text-slate-400 truncate">{p.repositoryFullName}</div>
                          )}
                        </div>
                        {activeProject?.id === p.id && (
                          <Check className="w-3.5 h-3.5 text-purple-400 shrink-0 ml-2" />
                        )}
                      </button>
                    ))
                  )}
                </div>
              )}
            </div>
          )}

          {/* Navigation Links Grouped by Category */}
          <nav className="space-y-4 pt-2">
            {navCategories.map((group) => (
              <div key={group.label} className="space-y-1">
                {!isCollapsed && (
                  <div className="px-3 text-[10px] font-semibold uppercase tracking-wider text-slate-400 font-mono">
                    {group.label}
                  </div>
                )}
                <div className="space-y-1">
                  {group.items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      onClick={() => onMobileClose && onMobileClose()}
                      className={({ isActive }) =>
                        `flex items-center ${
                          isCollapsed ? 'justify-center p-2.5' : 'justify-between px-3 py-2'
                        } rounded-xl text-xs font-medium transition-all duration-150 ${
                          isActive
                            ? 'bg-gradient-to-r from-purple-900/40 via-purple-900/20 to-transparent text-purple-200 border-l-2 border-purple-500 shadow-[inset_0_1px_0_0_rgba(139,92,246,0.2)]'
                            : 'text-slate-400 hover:text-slate-200 hover:bg-[#171E34]/60 border-l-2 border-transparent'
                        }`
                      }
                      title={isCollapsed ? item.label : undefined}
                    >
                      <div className="flex items-center gap-3">
                        <span className="text-purple-400/90">{item.icon}</span>
                        {!isCollapsed && <span>{item.label}</span>}
                      </div>

                      {!isCollapsed && item.badge && (
                        <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-[#171E34] text-slate-400 border border-[#252D46]">
                          {item.badge}
                        </span>
                      )}
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>
        </div>

        {/* Pinned Bottom Telemetry & User Profile */}
        <div className="p-3 border-t border-[#252D46] bg-[#080B14]/60 space-y-3">
          {/* Infrastructure Health Telemetry */}
          {!isCollapsed && (
            <div className="p-2.5 rounded-xl border border-[#252D46] bg-[#111629]/70 text-[11px] space-y-1.5">
              <div className="flex items-center justify-between text-slate-400">
                <span className="flex items-center gap-1.5 font-medium">
                  <span className={`w-1.5 h-1.5 rounded-full ${
                    (queueStats?.runningCount ?? 0) > 0
                      ? 'bg-amber-400 shadow-[0_0_6px_rgba(245,158,11,0.8)] animate-pulse'
                      : 'bg-emerald-400 shadow-[0_0_6px_rgba(16,185,129,0.8)]'
                  }`} />
                  <span className="font-mono text-[10px]">Worker Pool</span>
                </span>
                <span className="text-[9px] font-mono text-emerald-400 font-semibold">
                  {(queueStats?.runningCount ?? 0) > 0 ? `${queueStats?.runningCount} BUSY` : 'READY'}
                </span>
              </div>
              <div className="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                <span className="flex items-center gap-1">
                  <Zap className="w-3 h-3 text-[#A78BFA]" />
                  Job Queue
                </span>
                <span className="text-slate-300">
                  {queueStats ? `${queueStats.pendingCount + queueStats.runningCount} in queue` : 'Active'}
                </span>
              </div>
              <div className="w-full bg-[#080B14] h-1 rounded-full overflow-hidden border border-[#252D46]/60">
                <div
                  className="bg-gradient-to-r from-purple-500 to-indigo-500 h-full rounded-full transition-all duration-300"
                  style={{
                    width: `${
                      queueStats && queueStats.totalCount > 0
                        ? Math.min(100, Math.max(15, (queueStats.runningCount / queueStats.totalCount) * 100))
                        : 25
                    }%`
                  }}
                />
              </div>
            </div>
          )}

          {/* User Card */}
          <div className="flex items-center justify-between px-1">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-purple-600 to-indigo-600 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-sm border border-purple-400/20">
                {initials}
              </div>
              {!isCollapsed && (
                <div className="flex flex-col truncate">
                  <span className="text-xs font-semibold leading-tight text-slate-200 truncate">
                    {user?.name || 'Developer'}
                  </span>
                  <span className="text-[10px] text-slate-400 leading-tight font-mono">
                    {user?.role || 'ROLE_USER'}
                  </span>
                </div>
              )}
            </div>

            <button
              type="button"
              title="Sign Out"
              onClick={() => logout()}
              className="text-slate-400 hover:text-rose-400 p-1.5 rounded-lg hover:bg-[#171E34] transition-colors cursor-pointer shrink-0"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>
    </>
  );
};
