import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import type { HubRequestParams, New, PlanDto, TaskDto } from "@app/api/models";
import { createWorkspace, deleteWorkspace } from "@app/api/git-api";
import {
  createChangeRequest,
  createTask,
  createTaskPlan,
  enrichRequirement,
  executePlan,
  generatePlan,
  getTaskPlan,
  getTasks,
  openTerminal,
  openVSCode,
  patchTaskPlan,
  runAllPlanPhases,
  updateTaskPlan,
} from "@app/api/task-api";

const TASK_QUERY_KEY = "tasks";

export const useFetchTasks = (params: HubRequestParams) => {
  return useQuery({
    queryKey: [TASK_QUERY_KEY, params],
    queryFn: () => getTasks(params),
    refetchInterval: (query) => {
      const hasInProgress = query.state.data?.data?.some(
        (task) =>
          task.workspace?.isProvisioningInProgress ||
          task.plan?.isRequirementInProgress ||
          task.plan?.isPlanGenerationInProgress ||
          task.plan?.isExecutionPlanInProgress ||
          task.plan?.isChangeRequestInProgress,
      );
      return hasInProgress ? 3000 : false;
    },
  });
};

export const useCreateTaskMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (task: New<TaskDto>) => createTask(task),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
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

export const useOpenVSCodeMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => openVSCode(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
    },
  });
};

export const useOpenTerminalMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => openTerminal(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
    },
  });
};

export const useGeneratePlanMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => generatePlan(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
    },
  });
};

export const useEnrichRequirementMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => enrichRequirement(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
    },
  });
};

export const useExecutePlanMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => executePlan(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
    },
  });
};

export const useRunAllPlanPhasesMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => runAllPlanPhases(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useCreateChangeRequestMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: number) => createChangeRequest(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
    },
  });
};

export const usePatchTaskPlanMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      taskId,
      plan,
    }: {
      taskId: number;
      plan: Partial<PlanDto>;
    }) => patchTaskPlan(taskId, plan),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useFetchTaskPlan = (taskId: number, enabled = true) => {
  return useQuery({
    queryKey: [TASK_QUERY_KEY, taskId, "plan"],
    queryFn: () => getTaskPlan(taskId),
    enabled,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (
        data?.isRequirementInProgress ||
        data?.isPlanGenerationInProgress ||
        data?.isExecutionPlanInProgress ||
        data?.isChangeRequestInProgress
      ) {
        return 2000;
      }
      return false;
    },
  });
};

export const useDeleteWorkspaceForTaskMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (wsId: number) => deleteWorkspace(wsId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
  });
};

export const useCreateWorkspaceAndLinkMutation = (onSuccess?: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ gitId, taskId }: { gitId: number; taskId: number }) =>
      createWorkspace(gitId, taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TASK_QUERY_KEY] });
      onSuccess?.();
    },
  });
};
