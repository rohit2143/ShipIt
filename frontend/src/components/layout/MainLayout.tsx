import React from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { useProjects } from '../../context/ProjectContext';

export const MainLayout: React.FC = () => {
  const { activeProject } = useProjects();
  const location = useLocation();

  const getPageTitle = (pathname: string) => {
    switch (pathname) {
      case '/':
        return 'Overview & Projects';
      case '/deployments':
        return 'Deployments & Live Logs';
      case '/containers':
        return 'Containers & Health';
      case '/env-vars':
        return 'Secrets & Env';
      case '/settings':
        return 'Settings & Webhooks';
      default:
        return 'Dashboard';
    }
  };

  return (
    <div className="min-h-screen bg-[#0b0f19] text-slate-100 flex m-0 p-0 font-sans antialiased">
      <Sidebar />

      <div className="flex-1 flex flex-col min-w-0">
        <Header 
          currentPageTitle={getPageTitle(location.pathname)}
          onDeployTrigger={() => {
            if (activeProject) {
              alert(`Triggering manual deployment for ${activeProject.name}...`);
            }
          }}
        />

        <main className="flex-1 p-6 max-w-6xl w-full mx-auto space-y-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
