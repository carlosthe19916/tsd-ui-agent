import type React from "react";

import {
  Button,
  ButtonVariant,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { ProjectDto } from "@app/api/models";

import { ProjectForm } from "./project-form";
import { useProjectForm } from "./useProjectForm";

interface ProjectFormModalProps {
  project: ProjectDto | null;
  isOpen: boolean;
  onClose: () => void;
}

export const ProjectFormModal: React.FC<ProjectFormModalProps> = ({
  project,
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null;

  return <ProjectFormModalContent project={project} onClose={onClose} />;
};

const ProjectFormModalContent: React.FC<
  Omit<ProjectFormModalProps, "isOpen">
> = ({ project, onClose }) => {
  const isEditing = !!project?.id;
  const { form, onSubmit, isSubmitDisabled, isCancelDisabled } = useProjectForm(
    project,
    onClose,
  );

  return (
    <Modal
      variant="medium"
      isOpen
      onClose={onClose}
      aria-label={isEditing ? "Edit project" : "Create project"}
    >
      <ModalHeader title={isEditing ? "Edit project" : "Create project"} />
      <ModalBody>
        <ProjectForm control={form.control} />
      </ModalBody>
      <ModalFooter>
        <Button
          key="submit"
          variant={ButtonVariant.primary}
          isDisabled={isSubmitDisabled}
          onClick={onSubmit}
        >
          {isEditing ? "Save" : "Create"}
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
    </Modal>
  );
};
