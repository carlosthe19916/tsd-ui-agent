import type React from "react";

import {
  Button,
  ButtonVariant,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { GitDto } from "@app/api/models";

import { GitForm } from "./git-form";
import { useGitForm } from "./useGitForm";

interface GitFormModalProps {
  git: GitDto | null;
  isOpen: boolean;
  onClose: () => void;
}

export const GitFormModal: React.FC<GitFormModalProps> = ({
  git,
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null;

  return <GitFormModalContent git={git} onClose={onClose} />;
};

const GitFormModalContent: React.FC<Omit<GitFormModalProps, "isOpen">> = ({
  git,
  onClose,
}) => {
  const { form, onSubmit, isSubmitDisabled, isCancelDisabled, isEditing } =
    useGitForm(git, onClose);

  return (
    <Modal
      variant="medium"
      isOpen
      onClose={onClose}
      aria-label={isEditing ? "Edit git repository" : "Create git repository"}
    >
      <ModalHeader
        title={isEditing ? "Edit git repository" : "Create git repository"}
      />
      <ModalBody>
        <GitForm control={form.control} />
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
