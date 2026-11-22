import React from 'react';
import { Play, Activity } from 'lucide-react';
import { useProjects } from '../../context/ProjectContext';

interface HeaderProps {
  currentPageTitle: string;
  onDeployTrigger?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ 
  currentPageTitle,
  onDeployTrigger 
}) => {
  const { activeProject } = useProjects();
  const projectName = activeProject?.name || 'Select Project';

  return (
    <header className="border-b border-slate-800 bg-slate-900/60 sticky top-0 z-20 px-6 py-3 flex items-center justify-between backdrop-blur">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-xs">
        <span className="text-slate-400">Projects</span>
        <span className="text-slate-700">/</span>
        <span className="font-semibold text-slate-200">{projectName}</span>
        <span className="text-slate-700">/</span>
        <span className="text-slate-400 font-mono text-[11px]">{currentPageTitle}</span>
      </div>

      {/* Header Actions */}
      <div className="flex items-center gap-3">
        {activeProject?.port && (
          <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-950/80 border border-slate-800 text-xs font-mono text-slate-400">
            <Activity className="w-3 h-3 text-emerald-400" />
            <span>Port: {activeProject.port}</span>
          </div>
        )}

        <button 
          onClick={onDeployTrigger}
          disabled={!activeProject}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-blue-600 text-white font-medium text-xs shadow-sm hover:bg-blue-500 active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
        >
          <Play className="w-3.5 h-3.5 fill-current" />
          <span>Deploy Now</span>
        </button>
      </div>
    </header>
  );
};
