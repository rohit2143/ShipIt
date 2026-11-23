export interface EnvironmentVariable {
  id: string;
  key: string;
  value: string;
  isSecret: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertEnvVarRequest {
  key: string;
  value: string;
  isSecret?: boolean;
}

export interface BatchEnvVarsRequest {
  variables: UpsertEnvVarRequest[];
}
