import React, { useState, useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { CommandPalette } from '../navigation/CommandPalette';
import { useProjects } from '../../context/ProjectContext';

export const MainLayout: React.FC = () => {
  const { activeProject } = useProjects();
  const location = useLocation();
  const navigate = useNavigate();

  const [isCollapsed, setIsCollapsed] = useState(false);
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);

  // Global Ctrl + K listener
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setIsCommandPaletteOpen((prev) => !prev);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const getPageTitle = (pathname: string) => {
    switch (pathname) {
      case '/':
        return 'Overview & Projects';
      case '/deployments':
        return 'Deployments & Logs';
      case '/containers':
        return 'Containers & Telemetry';
      case '/env-vars':
        return 'Secrets & Env';
      case '/settings':
        return 'Settings & Webhooks';
      default:
        return 'Dashboard';
    }
  };

  return (
    <div className="min-h-screen bg-[#080B14] text-slate-100 flex m-0 p-0 font-sans antialiased selection:bg-[#8B5CF6]/30 selection:text-purple-200 relative overflow-x-hidden">
      {/* Futuristic Background Atmospheric Lighting */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0 bg-grid-pattern opacity-40" />
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0">
        <div className="absolute -top-40 left-1/4 w-[600px] h-[600px] bg-purple-600/10 rounded-full blur-[140px] opacity-70" />
        <div className="absolute top-1/3 -right-40 w-[500px] h-[500px] bg-blue-600/8 rounded-full blur-[140px] opacity-60" />
        <div className="absolute -bottom-40 left-1/3 w-[600px] h-[600px] bg-cyan-600/5 rounded-full blur-[140px] opacity-50" />
      </div>

      {/* Global Command Palette */}
      <CommandPalette
        isOpen={isCommandPaletteOpen}
        onClose={() => setIsCommandPaletteOpen(false)}
      />

      {/* Desktop & Mobile Responsive Sidebar */}
      <Sidebar
        isCollapsed={isCollapsed}
        onToggleCollapse={() => setIsCollapsed(!isCollapsed)}
        isMobileOpen={isMobileOpen}
        onMobileClose={() => setIsMobileOpen(false)}
      />

      {/* Main Content Viewport */}
      <div className="flex-1 flex flex-col min-w-0 transition-all duration-300 relative z-10">
        <Header 
          currentPageTitle={getPageTitle(location.pathname)}
          onDeployTrigger={() => {
            if (activeProject) {
              navigate('/deployments');
            }
          }}
          onOpenCommandPalette={() => setIsCommandPaletteOpen(true)}
          onMobileMenuToggle={() => setIsMobileOpen(true)}
        />

        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto space-y-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
