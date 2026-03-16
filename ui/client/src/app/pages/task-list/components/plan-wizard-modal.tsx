import React from "react";

import {
  Modal,
  Wizard,
  WizardHeader,
  WizardStep,
} from "@patternfly/react-core";

import type { GitDto, TaskDto } from "@app/api/models";
import {
  useCreateTaskPlanMutation,
  useUpdateTaskPlanMutation,
} from "@app/queries/tasks";

import { RequirementStep, type RequirementState } from "./requirement-step";
import {
  GitConfigurationStep,
  type GitConfigurationState,
} from "./git-configuration-step";
import { PlanStep, type PlanState } from "./execution-plan-step";

interface PlanWizardModalProps {
  task: TaskDto | null;
  isOpen: boolean;
  onClose: () => void;
  initialStep?: number;
}

export const PlanWizardModal: React.FC<PlanWizardModalProps> = ({
  task,
  isOpen,
  onClose,
  initialStep,
}) => {
  if (!isOpen || !task) return null;

  return (
    <PlanWizardModalContent
      task={task}
      onClose={onClose}
      initialStep={initialStep}
    />
  );
};

const PlanWizardModalContent: React.FC<{
  task: TaskDto;
  onClose: () => void;
  initialStep?: number;
}> = ({ task, onClose, initialStep = 1 }) => {
  const [requirementState, setRequirementState] =
    React.useState<RequirementState>({
      requirement: task.plan?.requirement ?? task.description ?? "",
      isValid: true,
    });

  const [gitConfigState, setGitConfigState] =
    React.useState<GitConfigurationState>({
      gitId: task.plan?.git?.id ? String(task.plan.git.id) : "",
      isValid: true,
    });

  const [planState, setPlanState] = React.useState<PlanState>({
    plan: task.plan?.plan ?? "",
    isValid: true,
  });

  const isValid =
    requirementState.isValid && gitConfigState.isValid && planState.isValid;

  const createMutation = useCreateTaskPlanMutation(onClose);
  const updateMutation = useUpdateTaskPlanMutation(onClose);

  const handleSave = () => {
    const gitPayload: GitDto | undefined = gitConfigState.gitId
      ? ({ id: Number(gitConfigState.gitId) } as GitDto)
      : undefined;

    if (task.plan) {
      updateMutation.mutate({
        taskId: task.id,
        plan: {
          ...task.plan,
          requirement: requirementState.requirement,
          plan: planState.plan,
          git: gitPayload,
        },
      });
    } else {
      createMutation.mutate({
        taskId: task.id,
        plan: {
          plan: planState.plan,
          git: gitPayload,
          status: "IN_PROGRESS",
          type: "MANUAL",
        },
      });
    }
  };

  return (
    <Modal
      isOpen
      onEscapePress={onClose}
      // variant={ModalVariant.large}
      width="90%"
      style={{ height: "93vh" }}
      aria-label="Plan wizard"
    >
      <Wizard
        onClose={onClose}
        header={
          <WizardHeader
            onClose={onClose}
            title={task.plan ? "Edit plan" : "Create plan"}
          />
        }
        startIndex={initialStep}
      >
        <WizardStep
          name="Requirement"
          id="requirement-step"
          footer={{ isNextDisabled: !requirementState.isValid }}
        >
          <RequirementStep
            taskId={task.id}
            initialState={requirementState}
            onStateChanged={setRequirementState}
          />
        </WizardStep>
        <WizardStep
          name="Git Configuration"
          id="git-config-step"
          footer={{ isNextDisabled: !gitConfigState.isValid }}
        >
          <GitConfigurationStep
            initialState={gitConfigState}
            onStateChanged={setGitConfigState}
            worktreePath={task.plan?.worktreePath}
            originalGitId={
              task.plan?.git?.id ? String(task.plan.git.id) : undefined
            }
          />
        </WizardStep>
        <WizardStep
          name="Plan"
          id="execution-plan-step"
          footer={{
            nextButtonText: "Save",
            isNextDisabled: !isValid,
            onNext: handleSave,
          }}
        >
          <PlanStep
            taskId={task.id}
            initialState={planState}
            onStateChanged={setPlanState}
          />
        </WizardStep>
      </Wizard>
    </Modal>
  );
};
