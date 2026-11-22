export type ProjectStatus = 'ACTIVE' | 'ARCHIVED' | 'DISABLED';

export interface ProjectSummary {
  id: string;
  name: string;
  description?: string;
  repositoryId?: string;
  repositoryFullName?: string;
  deploymentBranch?: string;
  port?: number;
  autoDeploy: boolean;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectDetail {
  id: string;
  name: string;
  description?: string;
  repositoryId: string;
  repositoryName: string;
  repositoryFullName: string;
  repositoryHtmlUrl: string;
  repositoryCloneUrl: string;
  repositoryDefaultBranch: string;
  repositoryIsPrivate: boolean;
  deploymentBranch: string;
  rootDirectory?: string;
  buildCommand?: string;
  startCommand?: string;
  port?: number;
  autoDeploy: boolean;
  status: ProjectStatus;
  environmentVariablesCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  repositoryId: string;
  name: string;
  description?: string;
  deploymentBranch?: string;
  rootDirectory?: string;
  buildCommand?: string;
  startCommand?: string;
  port?: number;
  autoDeploy?: boolean;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
  deploymentBranch?: string;
  rootDirectory?: string;
  buildCommand?: string;
  startCommand?: string;
  port?: number;
  autoDeploy?: boolean;
}

export interface ProjectEnvironmentVariable {
  id: string;
  key: string;
  maskedValue: string;
  version: number;
  encrypted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SetEnvironmentVariableRequest {
  key: string;
  value: string;
}
