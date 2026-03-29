import type React from "react";

import {
  Button,
  ButtonVariant,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import { TaskForm } from "./task-form";
import { useTaskForm } from "./useTaskForm";

interface TaskFormModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const TaskFormModal: React.FC<TaskFormModalProps> = ({
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null;

  return <TaskFormModalContent onClose={onClose} />;
};

const TaskFormModalContent: React.FC<{ onClose: () => void }> = ({
  onClose,
}) => {
  const { form, onSubmit, isSubmitDisabled, isCancelDisabled } =
    useTaskForm(onClose);

  return (
    <Modal variant="medium" isOpen onClose={onClose} aria-label="Create task">
      <ModalHeader title="Create task" />
      <Form onSubmit={onSubmit}>
        <ModalBody>
          <TaskForm control={form.control} />
        </ModalBody>
        <ModalFooter>
          <Button
            key="submit"
            variant={ButtonVariant.primary}
            isDisabled={isSubmitDisabled}
            type="submit"
          >
            Create
          </Button>
          <Button
            key="cancel"
            variant={ButtonVariant.link}
            isDisabled={isCancelDisabled}
            onClick={onClose}
          >
            Cancel
          </Button>
        </ModalFooter>
      </Form>
    </Modal>
  );
};
