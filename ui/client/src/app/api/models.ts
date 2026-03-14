/** Mark an object as "New" therefore does not have an `id` field. */
export type New<T extends { id: number }> = Omit<T, "id">;

export type SourceType = "JIRA" | "GITHUB";
export type SyncStatus =
  | "NOT_SYNCHRONIZED"
  | "SYNCHRONIZATION_IN_PROGRESS"
  | "SYNCHRONIZED"
  | "SYNC_ERROR";

export interface GitDto {
  url: string;
  branch?: string;
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

export interface TaskDto {
  id: number;
  externalId: string;
  url: string;
  title: string;
  description: string;
  status: TaskStatus;
  labels: string;
  type: SourceType;
  createdAt: string;
  updatedAt: string;
  project: ProjectDto;
}

export interface SearchResultDto<T> {
  meta: { offset: number; limit: number; count: number };
  data: T[];
}

export interface ProjectDto {
  id: number;
  name: string;
  apiUrl: string;
  query?: string;
  type: SourceType;
  git: GitDto;
  credentialId: number;
  syncStatus?: SyncStatus;
  lastSyncAt?: string;
}
