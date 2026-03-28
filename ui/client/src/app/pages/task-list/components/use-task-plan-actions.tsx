import React from "react";

import { ButtonVariant } from "@patternfly/react-core";

import type { TaskDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import {
  useCreateTaskPlanMutation,
  useRunAllPlanPhasesMutation,
} from "@app/queries/tasks";

export const useTaskPlanActions = () => {
  const [createPlanTask, setCreatePlanTask] = React.useState<TaskDto | null>(
    null,
  );
  const [runAllTask, setRunAllTask] = React.useState<TaskDto | null>(null);

  const createPlanMutation = useCreateTaskPlanMutation(() =>
    setCreatePlanTask(null),
  );
  const runAllMutation = useRunAllPlanPhasesMutation(() => setRunAllTask(null));

  const dialogs = (
    <>
      <ConfirmDialog
        isOpen={runAllTask !== null}
        title="Create plan and run"
        titleIconVariant="warning"
        message={`This will create a plan and run all phases (requirement enrichment, plan generation, execution, and change request) for "${runAllTask?.title}". This operation can take a long time.`}
        confirmBtnLabel="Run all"
        cancelBtnLabel="Cancel"
        confirmBtnVariant={ButtonVariant.danger}
        inProgress={runAllMutation.isPending}
        onConfirm={() => {
          if (runAllTask) {
            runAllMutation.mutate(runAllTask.id);
          }
        }}
        onClose={() => setRunAllTask(null)}
        onCancel={() => setRunAllTask(null)}
      />

      <ConfirmDialog
        isOpen={createPlanTask !== null}
        title="Create plan"
        titleIconVariant="info"
        message={`Create a new plan for "${createPlanTask?.title}"? This will auto-populate the requirement from the task description.`}
        confirmBtnLabel="Create"
        cancelBtnLabel="Cancel"
        confirmBtnVariant={ButtonVariant.primary}
        inProgress={createPlanMutation.isPending}
        onConfirm={() => {
          if (createPlanTask) {
            createPlanMutation.mutate({
              taskId: createPlanTask.id,
              plan: { plan: "" },
            });
          }
        }}
        onClose={() => setCreatePlanTask(null)}
        onCancel={() => setCreatePlanTask(null)}
      />
    </>
  );

  return {
    setCreatePlanTask,
    setRunAllTask,
    dialogs,
  };
};
