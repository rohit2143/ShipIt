import React from 'react';
import { Play, Activity, Search, Menu, Command } from 'lucide-react';
import { useProjects } from '../../context/ProjectContext';
import { Button } from '../ui/Button';

interface HeaderProps {
  currentPageTitle: string;
  onDeployTrigger?: () => void;
  onOpenCommandPalette?: () => void;
  onMobileMenuToggle?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ 
  currentPageTitle,
  onDeployTrigger,
  onOpenCommandPalette,
  onMobileMenuToggle,
}) => {
  const { activeProject } = useProjects();
  const projectName = activeProject?.name || 'All Projects';

  return (
    <header className="border-b border-[#252D46] bg-[#0D1120]/80 sticky top-0 z-20 px-4 sm:px-6 py-3 flex items-center justify-between backdrop-blur-xl transition-colors">
      {/* Left: Mobile Toggle & Breadcrumbs */}
      <div className="flex items-center gap-3 min-w-0">
        {onMobileMenuToggle && (
          <button
            type="button"
            onClick={onMobileMenuToggle}
            className="lg:hidden p-1.5 rounded-lg border border-[#252D46] text-slate-400 hover:text-slate-100 hover:bg-[#171E34] transition-colors cursor-pointer"
            aria-label="Open navigation menu"
          >
            <Menu className="w-4 h-4" />
          </button>
        )}

        <div className="flex items-center gap-2 text-xs truncate">
          <span className="text-slate-400 font-mono text-[11px] hidden sm:inline">ShipIt</span>
          <span className="text-slate-600 hidden sm:inline">/</span>
          <span className="font-semibold text-slate-200 truncate">{projectName}</span>
          <span className="text-slate-600">/</span>
          <span className="text-purple-400 font-mono text-[11px] font-medium truncate">
            {currentPageTitle}
          </span>
        </div>
      </div>

      {/* Center: Command Palette Trigger Button */}
      {onOpenCommandPalette && (
        <button
          type="button"
          onClick={onOpenCommandPalette}
          className="hidden md:flex items-center justify-between gap-3 px-3 py-1.5 rounded-xl border border-[#252D46] bg-[#111629]/90 hover:border-purple-500/40 text-xs text-slate-400 hover:text-slate-200 transition-all cursor-pointer shadow-sm min-w-[220px]"
        >
          <div className="flex items-center gap-2">
            <Search className="w-3.5 h-3.5 text-purple-400" />
            <span className="text-[11px]">Quick actions & search...</span>
          </div>
          <div className="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-[#171E34] border border-[#252D46] text-[10px] font-mono text-slate-400">
            <Command className="w-2.5 h-2.5 mr-0.5" />
            <span>K</span>
          </div>
        </button>
      )}

      {/* Right: Telemetry & Primary Action */}
      <div className="flex items-center gap-2.5 shrink-0">
        {/* Mobile Search Icon */}
        {onOpenCommandPalette && (
          <button
            type="button"
            onClick={onOpenCommandPalette}
            className="md:hidden p-2 rounded-lg border border-[#252D46] text-slate-400 hover:text-purple-300 hover:bg-[#171E34] transition-colors cursor-pointer"
            title="Open command palette"
          >
            <Search className="w-4 h-4" />
          </button>
        )}

        {/* Port Indicator */}
        {activeProject?.port && (
          <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-[#111629] border border-[#252D46] text-xs font-mono text-slate-300 shadow-sm">
            <Activity className="w-3 h-3 text-emerald-400 animate-pulse" />
            <span className="text-slate-400 text-[11px]">Port:</span>
            <span className="text-purple-300 font-semibold">{activeProject.port}</span>
          </div>
        )}

        {/* Deploy Now Button */}
        <Button
          variant="primary"
          size="sm"
          onClick={onDeployTrigger}
          disabled={!activeProject}
          leftIcon={<Play className="w-3.5 h-3.5 fill-current" />}
        >
          <span>Deploy</span>
        </Button>
      </div>
    </header>
  );
};
