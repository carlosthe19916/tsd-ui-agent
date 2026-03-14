import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import type { HubRequestParams, New, PlanDto } from "@app/api/models";
import { createTaskPlan, getTasks, updateTaskPlan } from "@app/api/task-api";

const TASK_QUERY_KEY = "tasks";

export const useFetchTasks = (params: HubRequestParams) => {
  return useQuery({
    queryKey: [TASK_QUERY_KEY, params],
    queryFn: () => getTasks(params),
  });
};

export const useCreateTaskPlanMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, plan }: { taskId: number; plan: New<PlanDto> }) =>
      createTaskPlan(taskId, plan),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useUpdateTaskPlanMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, plan }: { taskId: number; plan: PlanDto }) =>
      updateTaskPlan(taskId, plan),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
  });
};
