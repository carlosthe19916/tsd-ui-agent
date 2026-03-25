/** Mark an object as "New" therefore does not have an `id` field. */
export type New<T extends { id: number }> = Omit<T, "id">;

export type GitVendorType = "GITHUB" | "GITLAB";
export type SourceType = "JIRA" | "GITHUB";
export type SyncStatus =
  | "NOT_SYNCHRONIZED"
  | "SYNCHRONIZATION_IN_PROGRESS"
  | "SYNCHRONIZED"
  | "SYNC_ERROR";

export interface GitDto {
  id: number;
  url: string;
  branch?: string;
  forkUrl?: string;
  vendorType?: GitVendorType;
  credential?: CredentialDto;
}

export interface CredentialDto {
  id: number;
  name: string;
  token?: string;
}

export interface HubFilter {
  field: string;
  operator?: "=" | "!=" | "~" | ">" | ">=" | "<" | "<=";
  value:
    | string
    | number
    | {
        list: (string | number)[];
        operator?: "AND" | "OR";
      };
}

export interface HubRequestParams {
  filters?: HubFilter[];
  sort?: {
    field: string;
    direction: "asc" | "desc";
  };
  page?: {
    pageNumber: number;
    itemsPerPage: number;
  };
}

export interface Label {
  key: string;
  value?: string;
}

export type TaskStatus = "OPEN" | "IN_PROGRESS" | "CLOSED";

export interface WorkspaceDto {
  id?: number;
  git?: GitDto;
  isProvisioningInProgress?: boolean;
  provisioningError?: string;
  workspaceId?: string;
  task?: { id: number };
}

export interface PlanDto {
  id: number;
  plan: string;
  requirement?: string;
  isRequirementInProgress?: boolean;
  requirementError?: string;
  isExecutionPlanInProgress?: boolean;
  executionPlanError?: string;
  executionPlanCompletedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  isPlanGenerationInProgress?: boolean;
  planGenerationError?: string;
  isChangeRequestInProgress?: boolean;
  changeRequestError?: string;
  changeRequestUrl?: string;
}

export interface TaskDto {
  id: number;
  externalId: string;
  url: string;
  title: string;
  description: string;
  status: TaskStatus;
  externalStatus: string;

  labels?: string[];
  type: SourceType;
  createdAt: string;
  updatedAt: string;
  project: ProjectDto;
  plan?: PlanDto;
  workspace?: WorkspaceDto;
}

export interface SearchResultDto<T> {
  meta: { offset: number; limit: number; count: number };
  data: T[];
}

export interface ProjectGitMappingDto {
  id: number;
  projectId: number;
  gitId: number;
  space: string;
  labels: string[];
}

export interface ProjectDto {
  id: number;
  name: string;
  apiUrl: string;
  query?: string;
  type: SourceType;
  credential: CredentialDto;
  syncStatus?: SyncStatus;
  lastSyncAt?: string;
}
