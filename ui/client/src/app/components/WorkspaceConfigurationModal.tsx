import React from "react";

import {
  CodeBlock,
  CodeBlockCode,
  Modal,
  ModalBody,
  ModalHeader,
  Spinner,
} from "@patternfly/react-core";

import { getWorkspaceConfiguration } from "@app/api/git-api";

interface WorkspaceConfigurationModalProps {
  wsId: number | null;
  isOpen: boolean;
  onClose: () => void;
}

export const WorkspaceConfigurationModal: React.FC<
  WorkspaceConfigurationModalProps
> = ({ wsId, isOpen, onClose }) => {
  const [content, setContent] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);

  React.useEffect(() => {
    if (isOpen && wsId !== null) {
      setLoading(true);
      setContent(null);
      setError(null);
      getWorkspaceConfiguration(wsId)
        .then((data) => {
          const formatted =
            typeof data === "string" ? data : JSON.stringify(data, null, 2);
          setContent(formatted);
        })
        .catch((err) => {
          setError(err?.response?.data?.message ?? err.message ?? "Failed to load configuration");
        })
        .finally(() => setLoading(false));
    }
  }, [isOpen, wsId]);

  return (
    <Modal variant="large" isOpen={isOpen} onClose={onClose}>
      <ModalHeader title="Workspace Configuration" />
      <ModalBody>
        {loading && <Spinner size="lg" />}
        {error && <p>{error}</p>}
        {content && (
          <CodeBlock>
            <CodeBlockCode>{content}</CodeBlockCode>
          </CodeBlock>
        )}
      </ModalBody>
    </Modal>
  );
};
