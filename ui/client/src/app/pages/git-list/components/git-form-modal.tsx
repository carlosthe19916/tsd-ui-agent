import type React from "react";

import {
  Bullseye,
  Button,
  ButtonVariant,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Spinner,
} from "@patternfly/react-core";

import type { GitDto } from "@app/api/models";
import { useFetchGits } from "@app/queries/gits";

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
  const isEditing = !!git?.id;
  const { data: existingGits, isLoading } = useFetchGits();

  if (isLoading) {
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
          <Bullseye>
            <Spinner />
          </Bullseye>
        </ModalBody>
      </Modal>
    );
  }

  return (
    <GitFormModalForm
      git={git}
      existingGits={existingGits ?? []}
      onClose={onClose}
    />
  );
};

interface GitFormModalFormProps {
  git: GitDto | null;
  existingGits: GitDto[];
  onClose: () => void;
}

const GitFormModalForm: React.FC<GitFormModalFormProps> = ({
  git,
  existingGits,
  onClose,
}) => {
  const { form, onSubmit, isSubmitDisabled, isCancelDisabled, isEditing } =
    useGitForm(git, existingGits, onClose);

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
      <Form onSubmit={onSubmit}>
        <ModalBody>
          <GitForm control={form.control} editGitId={git?.id} />
        </ModalBody>
        <ModalFooter>
          <Button
            key="submit"
            variant={ButtonVariant.primary}
            isDisabled={isSubmitDisabled}
            type="submit"
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
      </Form>
    </Modal>
  );
};
