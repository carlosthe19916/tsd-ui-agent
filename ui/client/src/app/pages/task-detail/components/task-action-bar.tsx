import type React from "react";
import { Button, Tooltip } from "@patternfly/react-core";
import CodeBranchIcon from "@patternfly/react-icons/dist/esm/icons/code-branch-icon";

export interface TaskActionBarProps {
  onGitDiffClick: () => void;
  isGitDiffActive: boolean;
}

export const TaskActionBar: React.FC<TaskActionBarProps> = ({
  onGitDiffClick,
  isGitDiffActive,
}) => {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 4,
        paddingTop: 8,
        width: 36,
        borderLeft: "1px solid var(--pf-t--global--border--color--default)",
        height: "100%",
      }}
    >
      <Tooltip content="Git Changes" position="left">
        <Button
          variant="plain"
          aria-label="Git Changes"
          onClick={onGitDiffClick}
          style={{
            borderRadius: 6,
            padding: 6,
            background: isGitDiffActive
              ? "var(--pf-t--global--background--color--primary--default)"
              : undefined,
            color: isGitDiffActive
              ? "var(--pf-t--global--text--color--on-brand--default)"
              : undefined,
          }}
          icon={<CodeBranchIcon />}
        />
      </Tooltip>
    </div>
  );
};
