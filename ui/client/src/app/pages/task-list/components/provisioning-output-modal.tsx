import type React from "react";

import { Modal, ModalBody, ModalHeader } from "@patternfly/react-core";

import { ProvisioningOutputPanel } from "@app/pages/git-list/components/workspace-status";

interface ProvisioningOutputModalProps {
  wsId: number | null;
  isOpen: boolean;
  onClose: () => void;
}

export const ProvisioningOutputModal: React.FC<
  ProvisioningOutputModalProps
> = ({ wsId, isOpen, onClose }) => {
  return (
    <Modal variant="large" isOpen={isOpen} onClose={onClose}>
      <ModalHeader title="Provisioning Output" />
      <ModalBody>
        {isOpen && wsId !== null && <ProvisioningOutputPanel wsId={wsId} />}
      </ModalBody>
    </Modal>
  );
};
