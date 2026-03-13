import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createProject,
  deleteProject,
  getProjects,
  updateProject,
} from "@app/api/project-api";
import type { ProjectDto } from "@app/api/models";

const PROJECT_QUERY_KEY = "projects";

export const useFetchProjects = () => {
  return useQuery({
    queryKey: [PROJECT_QUERY_KEY],
    queryFn: getProjects,
  });
};

export const useCreateProjectMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (project: ProjectDto) => createProject(project),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PROJECT_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useUpdateProjectMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (project: ProjectDto) =>
      updateProject(project.id as number, project),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PROJECT_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useDeleteProjectMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteProject(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PROJECT_QUERY_KEY] });
      onSuccess?.();
    },
  });
};
