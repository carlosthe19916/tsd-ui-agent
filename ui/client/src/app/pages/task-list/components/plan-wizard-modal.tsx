import React from "react";

import {
  Button,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { TaskDto } from "@app/api/models";
import {
  useCreateTaskPlanMutation,
  usePatchTaskPlanMutation,
} from "@app/queries/tasks";

import { RequirementStep, type RequirementState } from "./requirement-step";
import { PlanStep, type PlanState } from "./execution-plan-step";

interface RequirementModalProps {
  task: TaskDto | null;
  isOpen: boolean;
  onClose: () => void;
}

export const RequirementModal: React.FC<RequirementModalProps> = ({
  task,
  isOpen,
  onClose,
}) => {
  if (!isOpen || !task) return null;
  return <RequirementModalContent task={task} onClose={onClose} />;
};

const RequirementModalContent: React.FC<{
  task: TaskDto;
  onClose: () => void;
}> = ({ task, onClose }) => {
  const [state, setState] = React.useState<RequirementState>({
    requirement: task.plan?.requirement ?? task.description ?? "",
    isValid: true,
  });

  const createMutation = useCreateTaskPlanMutation(onClose);
  const patchMutation = usePatchTaskPlanMutation(onClose);

  const handleSave = () => {
    if (task.plan) {
      patchMutation.mutate({
        taskId: task.id,
        plan: { requirement: state.requirement },
      });
    } else {
      createMutation.mutate({
        taskId: task.id,
        plan: {
          plan: "",
          requirement: state.requirement,
          status: "IN_PROGRESS",
          type: "MANUAL",
        },
      });
    }
  };

  const isSaving = createMutation.isPending || patchMutation.isPending;

  return (
    <Modal
      isOpen
      onEscapePress={onClose}
      width="90%"
      style={{ height: "93vh" }}
      aria-label="Edit requirement"
    >
      <ModalHeader title="Requirement" onClose={onClose} />
      <ModalBody style={{ height: "100%", overflow: "hidden" }}>
        <RequirementStep
          taskId={task.id}
          initialState={state}
          onStateChanged={setState}
        />
      </ModalBody>
      <ModalFooter>
        <Button
          variant="primary"
          onClick={handleSave}
          isDisabled={!state.isValid || isSaving}
          isLoading={isSaving}
        >
          Save
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
};

interface PlanModalProps {
  task: TaskDto | null;
  isOpen: boolean;
  onClose: () => void;
}

export const PlanModal: React.FC<PlanModalProps> = ({
  task,
  isOpen,
  onClose,
}) => {
  if (!isOpen || !task) return null;
  return <PlanModalContent task={task} onClose={onClose} />;
};

const PlanModalContent: React.FC<{
  task: TaskDto;
  onClose: () => void;
}> = ({ task, onClose }) => {
  const [state, setState] = React.useState<PlanState>({
    plan: task.plan?.plan ?? "",
    isValid: true,
  });

  const createMutation = useCreateTaskPlanMutation(onClose);
  const patchMutation = usePatchTaskPlanMutation(onClose);

  const handleSave = () => {
    if (task.plan) {
      patchMutation.mutate({
        taskId: task.id,
        plan: { plan: state.plan },
      });
    } else {
      createMutation.mutate({
        taskId: task.id,
        plan: {
          plan: state.plan,
          status: "IN_PROGRESS",
          type: "MANUAL",
        },
      });
    }
  };

  const isSaving = createMutation.isPending || patchMutation.isPending;

  return (
    <Modal
      isOpen
      onEscapePress={onClose}
      width="90%"
      style={{ height: "93vh" }}
      aria-label="Edit plan"
    >
      <ModalHeader title="Plan" onClose={onClose} />
      <ModalBody style={{ height: "100%", overflow: "hidden" }}>
        <PlanStep
          taskId={task.id}
          hasGit={!!task.workspace?.git}
          initialState={state}
          onStateChanged={setState}
        />
      </ModalBody>
      <ModalFooter>
        <Button
          variant="primary"
          onClick={handleSave}
          isDisabled={!state.isValid || isSaving}
          isLoading={isSaving}
        >
          Save
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
};
