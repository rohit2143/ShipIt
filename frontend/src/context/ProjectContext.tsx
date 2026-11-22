import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ProjectSummary } from '../types/project';
import { projectsApi } from '../api/projects';
import { useAuth } from './AuthContext';

interface ProjectContextType {
  projects: ProjectSummary[];
  activeProject: ProjectSummary | null;
  isLoading: boolean;
  selectProject: (projectId: string) => void;
  refreshProjects: () => Promise<void>;
}

const ProjectContext = createContext<ProjectContextType | undefined>(undefined);

export const ProjectProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [activeProject, setActiveProject] = useState<ProjectSummary | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const { isAuthenticated } = useAuth();

  const refreshProjects = async () => {
    if (!isAuthenticated) return;
    setIsLoading(true);
    try {
      const list = await projectsApi.getProjects();
      const safeList = Array.isArray(list) ? list : [];
      setProjects(safeList);
      
      setActiveProject((prev) => {
        if (prev && safeList.some((p) => p.id === prev.id)) {
          return safeList.find((p) => p.id === prev.id) || null;
        }
        return safeList.length > 0 ? safeList[0] : null;
      });
    } catch {
      setProjects([]);
      setActiveProject(null);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (isAuthenticated) {
      refreshProjects();
    } else {
      setProjects([]);
      setActiveProject(null);
    }
  }, [isAuthenticated]);

  const selectProject = (projectId: string) => {
    const found = projects.find((p) => p.id === projectId);
    if (found) {
      setActiveProject(found);
    }
  };

  return (
    <ProjectContext.Provider
      value={{
        projects,
        activeProject,
        isLoading,
        selectProject,
        refreshProjects,
      }}
    >
      {children}
    </ProjectContext.Provider>
  );
};

export const useProjects = (): ProjectContextType => {
  const context = useContext(ProjectContext);
  if (!context) {
    throw new Error('useProjects must be used within a ProjectProvider');
  }
  return context;
};
