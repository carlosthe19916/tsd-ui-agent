import React from "react";
import {
  Button,
  EmptyState,
  EmptyStateBody,
  Label,
  Spinner,
  Split,
  SplitItem,
  Title,
} from "@patternfly/react-core";
import ArrowLeftIcon from "@patternfly/react-icons/dist/esm/icons/arrow-left-icon";
import { Diff, Hunk, parseDiff } from "react-diff-view";
import "react-diff-view/style/index.css";
import styles from "./git-diff-drawer.module.css";

import {
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
    <div>
      {files.map((file) => {
        const parts = file.path.split("/");
        const fileName = parts.pop();
        const dir = parts.length > 0 ? `${parts.join("/")}/` : "";

        return (
          <div
            key={file.path}
            role="button"
            tabIndex={0}
            onClick={() => onSelect(file.path)}
            onKeyDown={(e) => {
              if (e.key === "Enter") onSelect(file.path);
            }}
            style={{
              padding: "6px 12px",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 8,
              borderBottom:
                "1px solid var(--pf-t--global--border--color--default)",
            }}
          >
            <Label color={statusColor(file.status)} isCompact>
              {file.status}
            </Label>
            <span
              style={{
                fontSize: 13,
                fontFamily: "var(--pf-t--global--font--family--mono)",
              }}
            >
              <span style={{ opacity: 0.6 }}>{dir}</span>
              <span style={{ fontWeight: 600 }}>{fileName}</span>
            </span>
          </div>
        );
      })}
    </div>
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
    <div>
      <Split hasGutter style={{ padding: "8px 12px", alignItems: "center" }}>
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
          <span
            style={{
              fontSize: 13,
              fontFamily: "var(--pf-t--global--font--family--mono)",
            }}
          >
            {filePath}
          </span>
        </SplitItem>
      </Split>
      {isLoading ? (
        <div style={{ padding: 24, textAlign: "center" }}>
          <Spinner size="lg" />
        </div>
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
    </div>
  );
};

export const GitDiffDrawer: React.FC<GitDiffDrawerProps> = ({
  workspaceId,
}) => {
  const [selectedFile, setSelectedFile] = React.useState<string | null>(null);

  const { data: changedFiles, isLoading } = useFetchWorkspaceChangedFiles(
    workspaceId,
    !selectedFile,
  );

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
      <div style={{ padding: 24, textAlign: "center" }}>
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div>
      <div style={{ padding: "8px 12px" }}>
        <Title headingLevel="h3" size="md">
          Changed Files
          {changedFiles && changedFiles.length > 0 && (
            <Label isCompact style={{ marginLeft: 8 }}>
              {changedFiles.length}
            </Label>
          )}
        </Title>
      </div>
      <FileListPanel files={changedFiles ?? []} onSelect={setSelectedFile} />
    </div>
  );
};
