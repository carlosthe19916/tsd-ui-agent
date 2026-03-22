import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createGit,
  createWorkspace,
  deleteGit,
  deleteWorkspace,
  getGits,
  getWorkspaceStatus,
  getWorkspaces,
  updateGit,
} from "@app/api/git-api";
import type { GitDto, New } from "@app/api/models";

const GIT_QUERY_KEY = "gits";
const WORKSPACES_QUERY_KEY = "workspaces";
const WORKSPACE_STATUS_QUERY_KEY = "workspace-status";

export const useFetchGits = () => {
  return useQuery({
    queryKey: [GIT_QUERY_KEY],
    queryFn: getGits,
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

export const useFetchWorkspaces = (gitId: number) => {
  return useQuery({
    queryKey: [WORKSPACES_QUERY_KEY, gitId],
    queryFn: () => getWorkspaces(gitId),
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
    mutationFn: (gitId: number) => createWorkspace(gitId),
    onSuccess: (_response, gitId) => {
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
    queryFn: () => getWorkspaceStatus(wsId!),
    enabled: enabled && wsId != null,
  });
};

export const useDeleteWorkspaceMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ gitId, wsId }: { gitId: number; wsId: number }) =>
      deleteWorkspace(wsId),
    onSuccess: (_response, { gitId }) => {
      queryClient.invalidateQueries({
        queryKey: [WORKSPACES_QUERY_KEY, gitId],
      });
      onSuccess?.();
    },
  });
};
