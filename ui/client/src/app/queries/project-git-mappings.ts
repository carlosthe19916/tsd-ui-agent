import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createMapping,
  deleteMapping,
  getMappings,
} from "@app/api/project-git-mapping-api";
import type { New, ProjectGitMappingDto } from "@app/api/models";

const MAPPINGS_QUERY_KEY = "project-git-mappings";

export const useFetchMappings = (projectId: number) => {
  return useQuery({
    queryKey: [MAPPINGS_QUERY_KEY, projectId],
    queryFn: () => getMappings(projectId),
  });
};

export const useCreateMappingMutation = (
  projectId: number,
  onSuccess?: () => void,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mapping: New<ProjectGitMappingDto>) =>
      createMapping(projectId, mapping),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [MAPPINGS_QUERY_KEY, projectId],
      });
      onSuccess?.();
    },
  });
};

export const useDeleteMappingMutation = (
  projectId: number,
  onSuccess?: () => void,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mappingId: number) => deleteMapping(projectId, mappingId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [MAPPINGS_QUERY_KEY, projectId],
      });
      onSuccess?.();
    },
  });
};
