import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { createGit, deleteGit, getGits, updateGit } from "@app/api/git-api";
import type { GitDto, New } from "@app/api/models";

const GIT_QUERY_KEY = "gits";

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
