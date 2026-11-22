import React, { useState } from 'react';
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
  Check
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useProjects } from '../../context/ProjectContext';

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuth();
  const { projects, activeProject, selectProject } = useProjects();
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const initials = user?.name
    ? user.name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .substring(0, 2)
    : 'U';

  const activeProjectName = activeProject?.name || 'No project selected';

  return (
    <aside className="w-64 border-r border-slate-800 bg-slate-900/90 backdrop-blur flex flex-col justify-between shrink-0 min-h-screen sticky top-0 h-screen z-30 select-none">
      
      {/* Top Brand & Project Selector */}
      <div className="p-4 space-y-4">
        {/* Brand */}
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-blue-600 text-white flex items-center justify-center font-bold text-base shadow-sm">
            <Rocket className="w-4 h-4" />
          </div>
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5">
              <span className="font-bold tracking-tight text-sm text-slate-100">ShipIt</span>
              <span className="text-[9px] uppercase font-semibold px-1 py-0.2 rounded bg-slate-800 text-slate-300 border border-slate-700">PaaS</span>
            </div>
            <span className="text-[10px] text-slate-400">Single-Node Engine</span>
          </div>
        </div>

        {/* Project Selector Dropdown */}
        <div className="relative">
          <label className="block text-[10px] font-semibold uppercase tracking-wider text-slate-400 mb-1">
            Active Project
          </label>
          <button 
            type="button"
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            className="w-full flex items-center justify-between text-xs font-medium px-2.5 py-2 rounded-lg border border-slate-800 bg-slate-950/60 hover:border-slate-700 transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-2 truncate">
              <span className={`w-2 h-2 rounded-full shrink-0 ${activeProject ? 'bg-emerald-500' : 'bg-slate-600'}`}></span>
              <span className="truncate font-semibold text-slate-200">{activeProjectName}</span>
            </div>
            <ChevronDown className={`w-3.5 h-3.5 text-slate-400 shrink-0 transition-transform ${isDropdownOpen ? 'rotate-180' : ''}`} />
          </button>

          {isDropdownOpen && (
            <div className="absolute top-full left-0 right-0 mt-1 py-1 bg-slate-950 border border-slate-800 rounded-lg shadow-xl z-50 max-h-48 overflow-y-auto">
              {projects.length === 0 ? (
                <div className="px-3 py-2 text-xs text-slate-500">No projects yet</div>
              ) : (
                projects.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => {
                      selectProject(p.id);
                      setIsDropdownOpen(false);
                    }}
                    className="w-full flex items-center justify-between px-3 py-1.5 text-xs text-left hover:bg-slate-900 transition-colors cursor-pointer"
                  >
                    <span className="truncate text-slate-300">{p.name}</span>
                    {activeProject?.id === p.id && (
                      <Check className="w-3.5 h-3.5 text-blue-400 shrink-0" />
                    )}
                  </button>
                ))
              )}
            </div>
          )}
        </div>

        {/* Navigation Menu */}
        <nav className="space-y-1 pt-1">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 px-2.5 mb-1.5">
            Workspace
          </div>

          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              `w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs font-medium transition-all ${
                isActive
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`
            }
          >
            <div className="flex items-center gap-2.5">
              <LayoutDashboard className="w-4 h-4" />
              <span>Overview & Projects</span>
            </div>
            <span className="text-[10px] font-mono opacity-80">{projects.length}</span>
          </NavLink>

          <NavLink
            to="/deployments"
            className={({ isActive }) =>
              `w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs font-medium transition-all ${
                isActive
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`
            }
          >
            <div className="flex items-center gap-2.5">
              <Terminal className="w-4 h-4" />
              <span>Deployments & Logs</span>
            </div>
          </NavLink>

          <NavLink
            to="/containers"
            className={({ isActive }) =>
              `w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-xs font-medium transition-all ${
                isActive
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`
            }
          >
            <Server className="w-4 h-4" />
            <span>Containers & Health</span>
          </NavLink>

          <NavLink
            to="/env-vars"
            className={({ isActive }) =>
              `w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs font-medium transition-all ${
                isActive
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`
            }
          >
            <div className="flex items-center gap-2.5">
              <KeyRound className="w-4 h-4" />
              <span>Secrets & Env</span>
            </div>
            <span className="text-[9px] font-mono px-1 rounded bg-slate-950 border border-slate-800 text-slate-400">
              AES
            </span>
          </NavLink>

          <NavLink
            to="/settings"
            className={({ isActive }) =>
              `w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-xs font-medium transition-all ${
                isActive
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`
            }
          >
            <Settings className="w-4 h-4" />
            <span>Settings & Webhooks</span>
          </NavLink>
        </nav>
      </div>

      {/* Pinned Bottom Node Telemetry & User Card */}
      <div className="p-3 border-t border-slate-800 bg-slate-950/40 space-y-3">
        {/* Engine Status */}
        <div className="p-2.5 rounded-lg border border-slate-800 bg-slate-900/60 text-[11px] space-y-1.5">
          <div className="flex items-center justify-between text-slate-400">
            <span className="flex items-center gap-1.5 font-medium">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Node Worker 0
            </span>
            <span className="text-[9px] font-mono text-emerald-400 font-semibold">ONLINE</span>
          </div>
          <div className="flex items-center justify-between text-[10px] text-slate-400">
            <span>RAM: 2.4 / 8.0 GB</span>
            <span className="flex items-center gap-1">
              <Cpu className="w-3 h-3 text-slate-400" />
              Docker 29.1
            </span>
          </div>
          <div className="w-full bg-slate-950 h-1 rounded-full overflow-hidden border border-slate-800">
            <div className="bg-indigo-500 h-full rounded-full" style={{ width: '30%' }}></div>
          </div>
        </div>

        {/* User Card */}
        <div className="flex items-center justify-between px-1">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold text-xs">
              {initials}
            </div>
            <div className="flex flex-col truncate max-w-[120px]">
              <span className="text-xs font-semibold leading-tight text-slate-200 truncate">
                {user?.name || 'Developer'}
              </span>
              <span className="text-[10px] text-slate-400 leading-tight">
                {user?.role || 'ROLE_USER'}
              </span>
            </div>
          </div>
          <button 
            title="Sign Out" 
            onClick={() => logout()}
            className="text-slate-400 hover:text-rose-400 p-1.5 rounded-md hover:bg-slate-800/60 transition-colors cursor-pointer"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </aside>
  );
};
