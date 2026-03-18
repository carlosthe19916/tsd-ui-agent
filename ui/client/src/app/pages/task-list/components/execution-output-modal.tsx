import type React from "react";

import { Modal, ModalBody, ModalHeader } from "@patternfly/react-core";

import { ExecutionOutputPanel } from "./execution-output-panel";

interface ExecutionOutputModalProps {
  taskId: number | null;
  isOpen: boolean;
  onClose: () => void;
}

export const ExecutionOutputModal: React.FC<ExecutionOutputModalProps> = ({
  taskId,
  isOpen,
  onClose,
}) => {
  // React.useEffect(() => {console.log("refresh")});
  return (
    <Modal variant="large" isOpen={isOpen} onClose={onClose}>
      <ModalHeader title="Execution Output" />
      <ModalBody>
        {isOpen && taskId !== null && (
          <ExecutionOutputPanel taskId={taskId} isActive={true} />
        )}
      </ModalBody>
    </Modal>
  );
};
