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
  id?: number;
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

export interface ProjectDto {
  id?: number;
  name: string;
  url: string;
  query?: string;
  type: SourceType;
  git: GitDto;
  credentialId: number;
  syncStatus?: SyncStatus;
  lastSyncAt?: string;
}
