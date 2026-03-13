import type React from "react";

import {
  Button,
  ButtonVariant,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { CredentialDto } from "@app/api/models";

import { CredentialForm } from "./credential-form";
import { useCredentialForm } from "./useCredentialForm";

interface CredentialFormModalProps {
  credential: CredentialDto | null;
  isOpen: boolean;
  onClose: () => void;
}

export const CredentialFormModal: React.FC<CredentialFormModalProps> = ({
  credential,
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null;

  return (
    <CredentialFormModalContent credential={credential} onClose={onClose} />
  );
};

const CredentialFormModalContent: React.FC<
  Omit<CredentialFormModalProps, "isOpen">
> = ({ credential, onClose }) => {
  const isEditing = !!credential?.id;
  const { form, onSubmit, isSubmitDisabled, isCancelDisabled } =
    useCredentialForm(credential, onClose);

  return (
    <Modal
      variant="medium"
      isOpen
      onClose={onClose}
      aria-label={isEditing ? "Edit credential" : "Create credential"}
    >
      <ModalHeader
        title={isEditing ? "Edit credential" : "Create credential"}
      />
      <ModalBody>
        <CredentialForm control={form.control} />
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
