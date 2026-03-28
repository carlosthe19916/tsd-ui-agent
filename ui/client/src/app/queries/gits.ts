import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createGit,
  createWorkspace,
  deleteGit,
  deleteWorkspace,
  getGits,
  getWorkspaceChangedFiles,
  getWorkspaceCommands,
  getWorkspaceDiff,
  getWorkspaceStatus,
  getWorkspaces,
  startWorkspace,
  stopWorkspace,
  updateGit,
} from "@app/api/git-api";
import type { ExecutionMode, GitDto, New } from "@app/api/models";

const GIT_QUERY_KEY = "gits";
const WORKSPACES_QUERY_KEY = "workspaces";
const WORKSPACE_STATUS_QUERY_KEY = "workspace-status";
const WORKSPACE_COMMANDS_QUERY_KEY = "workspace-commands";
const WORKSPACE_CHANGED_FILES_KEY = "workspace-changed-files";
const WORKSPACE_DIFF_KEY = "workspace-diff";

export const useFetchGits = () => {
  return useQuery({
    queryKey: [GIT_QUERY_KEY],
    queryFn: getGits,
    refetchInterval: (query) => {
      const hasInProgress = query.state.data?.some(
        (git) => git.isProvisioningInProgress,
      );
      return hasInProgress ? 3000 : false;
    },
  });
};

export const useCreateGitMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (git: New<GitDto>) => createGit(git),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [GIT_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useUpdateGitMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (git: GitDto) => updateGit(git.id as number, git),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [GIT_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useDeleteGitMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteGit(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [GIT_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useFetchWorkspaces = (gitId: number, hasTask?: boolean) => {
  return useQuery({
    queryKey: [WORKSPACES_QUERY_KEY, gitId, { hasTask }],
    queryFn: () => getWorkspaces(gitId, hasTask),
    refetchInterval: (query) => {
      const hasInProgress = query.state.data?.some(
        (workspace) => workspace.isProvisioningInProgress,
      );
      return hasInProgress ? 3000 : false;
    },
  });
};

export const useCreateWorkspaceMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      gitId,
      executionMode,
    }: {
      gitId: number;
      executionMode: ExecutionMode;
    }) => createWorkspace(gitId, undefined, executionMode),
    onSuccess: (_response, { gitId }) => {
      queryClient.invalidateQueries({
        queryKey: [WORKSPACES_QUERY_KEY, gitId],
      });
      onSuccess?.();
    },
  });
};

export const useFetchWorkspaceStatus = (
  wsId: number | undefined,
  enabled: boolean,
) => {
  return useQuery({
    queryKey: [WORKSPACE_STATUS_QUERY_KEY, wsId],
    queryFn: () => getWorkspaceStatus(wsId as number),
    enabled: enabled && wsId != null,
  });
};

export const useFetchWorkspaceCommands = (
  wsId: number | undefined,
  enabled: boolean,
) => {
  return useQuery({
    queryKey: [WORKSPACE_COMMANDS_QUERY_KEY, wsId],
    queryFn: () => getWorkspaceCommands(wsId as number),
    enabled: enabled && wsId != null,
  });
};

export const useStartWorkspaceMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (wsId: number) => startWorkspace(wsId),
    onSuccess: (_response, wsId) => {
      queryClient.invalidateQueries({
        queryKey: [WORKSPACE_STATUS_QUERY_KEY, wsId],
      });
    },
  });
};

export const useStopWorkspaceMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (wsId: number) => stopWorkspace(wsId),
    onSuccess: (_response, wsId) => {
      queryClient.invalidateQueries({
        queryKey: [WORKSPACE_STATUS_QUERY_KEY, wsId],
      });
    },
  });
};

export const useFetchWorkspaceChangedFiles = (
  wsId: number | undefined,
  enabled: boolean,
) => {
  return useQuery({
    queryKey: [WORKSPACE_CHANGED_FILES_KEY, wsId],
    queryFn: () => getWorkspaceChangedFiles(wsId as number),
    enabled: enabled && wsId != null,
    refetchInterval: 5000,
  });
};

export const useFetchWorkspaceDiff = (
  wsId: number | undefined,
  filePath: string | undefined,
  enabled: boolean,
) => {
  return useQuery({
    queryKey: [WORKSPACE_DIFF_KEY, wsId, filePath],
    queryFn: () => getWorkspaceDiff(wsId as number, filePath),
    enabled: enabled && wsId != null && filePath != null,
    refetchInterval: 5000,
  });
};

export const useDeleteWorkspaceMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ wsId }: { gitId: number; wsId: number }) =>
      deleteWorkspace(wsId),
    onSuccess: (_response, { gitId }) => {
      queryClient.invalidateQueries({
        queryKey: [WORKSPACES_QUERY_KEY, gitId],
      });
      onSuccess?.();
    },
  });
};
