export interface GitHubConnection {
  id: string;
  installationId: number;
  accountLogin: string;
  accountType: 'User' | 'Organization';
  avatarUrl: string;
  verified: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface GitHubRepositoryItem {
  id: number; // GitHub numeric repo ID
  name: string;
  fullName?: string;
  full_name?: string;
  ownerLogin?: string;
  ownerAvatarUrl?: string;
  htmlUrl?: string;
  html_url?: string;
  cloneUrl?: string;
  clone_url?: string;
  defaultBranch?: string;
  default_branch?: string;
  isPrivate?: boolean;
  private?: boolean;
  description?: string;
}

export interface GitHubRepositoriesListResponse {
  totalCount: number;
  repositories: GitHubRepositoryItem[];
}

export interface GitHubConnectedRepository {
  id: string; // Internal UUID
  connectionId: string;
  githubRepoId: number;
  name: string;
  fullName: string;
  ownerLogin: string;
  ownerAvatarUrl: string;
  htmlUrl: string;
  cloneUrl: string;
  defaultBranch: string;
  isPrivate: boolean;
  webhookId?: number;
  createdAt: string;
  updatedAt: string;
}

export interface GitHubBranch {
  name: string;
  commitSha: string;
  isProtected: boolean;
}

export interface ConnectRepositoryRequest {
  connectionId: string;
  githubRepoId: number;
}
