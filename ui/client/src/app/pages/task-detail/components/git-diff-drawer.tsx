import React from "react";
import {
  ActionGroup,
  Alert,
  Bullseye,
  Button,
  Content,
  Divider,
  EmptyState,
  EmptyStateBody,
  Flex,
  FlexItem,
  Form,
  FormGroup,
  Label,
  Spinner,
  Split,
  SplitItem,
  Stack,
  StackItem,
  TextInput,
  Title,
} from "@patternfly/react-core";
import ArrowLeftIcon from "@patternfly/react-icons/dist/esm/icons/arrow-left-icon";
import { Diff, Hunk, parseDiff } from "react-diff-view";
import "react-diff-view/style/index.css";
import styles from "./git-diff-drawer.module.css";

import {
  useCommitAndPushMutation,
  useCommitMutation,
  useFetchWorkspaceChangedFiles,
  useFetchWorkspaceDiff,
} from "@app/queries/gits";
import type { GitChangedFile } from "@app/api/git-api";

interface GitDiffDrawerProps {
  workspaceId: number;
}

const statusColor = (
  status: string,
): "blue" | "green" | "red" | "grey" | "orange" => {
  switch (status) {
    case "M":
      return "blue";
    case "A":
      return "green";
    case "D":
      return "red";
    case "R":
      return "orange";
    default:
      return "grey";
  }
};

const FileListPanel: React.FC<{
  files: GitChangedFile[];
  onSelect: (path: string) => void;
}> = ({ files, onSelect }) => {
  if (files.length === 0) {
    return (
      <EmptyState>
        <EmptyStateBody>No changes detected</EmptyStateBody>
      </EmptyState>
    );
  }

  return (
    <Stack>
      {files.map((file) => {
        const parts = file.path.split("/");
        const fileName = parts.pop();
        const dir = parts.length > 0 ? `${parts.join("/")}/` : "";

        return (
          <StackItem key={file.path}>
            <Button variant="plain" isBlock onClick={() => onSelect(file.path)}>
              <Flex
                alignItems={{ default: "alignItemsCenter" }}
                gap={{ default: "gapSm" }}
              >
                <FlexItem>
                  <Label color={statusColor(file.status)} isCompact>
                    {file.status}
                  </Label>
                </FlexItem>
                <FlexItem>
                  <Content component="small">
                    <Content component="span">{dir}</Content>
                    <Content component="strong">{fileName}</Content>
                  </Content>
                </FlexItem>
              </Flex>
            </Button>
          </StackItem>
        );
      })}
    </Stack>
  );
};

const DiffViewPanel: React.FC<{
  workspaceId: number;
  filePath: string;
  onBack: () => void;
}> = ({ workspaceId, filePath, onBack }) => {
  const { data: diffText, isLoading } = useFetchWorkspaceDiff(
    workspaceId,
    filePath,
    true,
  );

  const files = React.useMemo(() => {
    if (!diffText) return [];
    return parseDiff(diffText);
  }, [diffText]);

  return (
    <Stack hasGutter>
      <StackItem>
        <Split hasGutter>
          <SplitItem>
            <Button
              variant="plain"
              aria-label="Back to file list"
              onClick={onBack}
              icon={<ArrowLeftIcon />}
              size="sm"
            />
          </SplitItem>
          <SplitItem isFilled>
            <Content component="small">{filePath}</Content>
          </SplitItem>
        </Split>
      </StackItem>
      <StackItem>
        {isLoading ? (
          <Bullseye>
            <Spinner size="lg" />
          </Bullseye>
        ) : files.length === 0 ? (
          <EmptyState>
            <EmptyStateBody>No diff available</EmptyStateBody>
          </EmptyState>
        ) : (
          <div className={styles.diffContainer}>
            {files.map((file) => (
              <Diff
                key={`${file.oldRevision}-${file.newRevision}`}
                viewType="unified"
                diffType={file.type}
                hunks={file.hunks}
              >
                {(hunks) =>
                  hunks.map((hunk) => <Hunk key={hunk.content} hunk={hunk} />)
                }
              </Diff>
            ))}
          </div>
        )}
      </StackItem>
    </Stack>
  );
};

export const GitDiffDrawer: React.FC<GitDiffDrawerProps> = ({
  workspaceId,
}) => {
  const [selectedFile, setSelectedFile] = React.useState<string | null>(null);
  const [commitMessage, setCommitMessage] = React.useState("");

  const { data: changedFiles, isLoading } = useFetchWorkspaceChangedFiles(
    workspaceId,
    !selectedFile,
  );

  const commitMutation = useCommitMutation(() => setCommitMessage(""));
  const commitAndPushMutation = useCommitAndPushMutation(() =>
    setCommitMessage(""),
  );

  const hasChanges = (changedFiles?.length ?? 0) > 0;
  const isCommitting =
    commitMutation.isPending || commitAndPushMutation.isPending;
  const commitError = commitMutation.error || commitAndPushMutation.error;

  if (selectedFile) {
    return (
      <DiffViewPanel
        workspaceId={workspaceId}
        filePath={selectedFile}
        onBack={() => setSelectedFile(null)}
      />
    );
  }

  if (isLoading) {
    return (
      <Bullseye>
        <Spinner size="lg" />
      </Bullseye>
    );
  }

  return (
    <Stack hasGutter>
      <StackItem>
        <Flex
          alignItems={{ default: "alignItemsCenter" }}
          gap={{ default: "gapSm" }}
        >
          <FlexItem>
            <Title headingLevel="h3" size="md">
              Changed Files
            </Title>
          </FlexItem>
          {changedFiles && changedFiles.length > 0 && (
            <FlexItem>
              <Label isCompact>{changedFiles.length}</Label>
            </FlexItem>
          )}
        </Flex>
      </StackItem>
      <StackItem>
        <FileListPanel files={changedFiles ?? []} onSelect={setSelectedFile} />
      </StackItem>

      {hasChanges && (
        <>
          <Divider />
          <StackItem>
            <Form
              onSubmit={(e) => {
                e.preventDefault();
                commitMutation.mutate({
                  wsId: workspaceId,
                  message: commitMessage,
                });
              }}
            >
              <FormGroup label="Commit message" fieldId="commit-message">
                <TextInput
                  id="commit-message"
                  value={commitMessage}
                  onChange={(_e, val) => setCommitMessage(val)}
                  placeholder="Describe your changes..."
                  isDisabled={isCommitting}
                />
              </FormGroup>
              {commitError && (
                <Alert
                  variant="danger"
                  isInline
                  isPlain
                  title={
                    commitError instanceof Error
                      ? commitError.message
                      : "Commit failed"
                  }
                />
              )}
              <ActionGroup>
                <Button
                  variant="primary"
                  size="sm"
                  isDisabled={!commitMessage.trim() || isCommitting}
                  isLoading={commitMutation.isPending}
                  onClick={() =>
                    commitMutation.mutate({
                      wsId: workspaceId,
                      message: commitMessage,
                    })
                  }
                >
                  Commit
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  isDisabled={!commitMessage.trim() || isCommitting}
                  isLoading={commitAndPushMutation.isPending}
                  onClick={() =>
                    commitAndPushMutation.mutate({
                      wsId: workspaceId,
                      message: commitMessage,
                    })
                  }
                >
                  Commit & Push
                </Button>
              </ActionGroup>
            </Form>
          </StackItem>
        </>
      )}
    </Stack>
  );
};
