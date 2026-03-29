import type React from "react";

import { Badge, Button, Tooltip } from "@patternfly/react-core";
import CodeBranchIcon from "@patternfly/react-icons/dist/esm/icons/code-branch-icon";

import { useFetchWorkspaceChangedFiles } from "@app/queries/gits";

interface GitChangesButtonProps {
  workspaceId: number;
  onClick: () => void;
  isActive?: boolean;
  tooltipPosition?: "top" | "bottom" | "left" | "right";
}

export const GitChangesButton: React.FC<GitChangesButtonProps> = ({
  workspaceId,
  onClick,
  isActive = false,
  tooltipPosition = "top",
}) => {
  const { data: changedFiles } = useFetchWorkspaceChangedFiles(
    workspaceId,
    true,
  );

  const count = changedFiles?.length ?? 0;

  return (
    <Tooltip content="Git Changes" position={tooltipPosition}>
      <Button
        variant="plain"
        aria-label="Git Changes"
        onClick={onClick}
        icon={<CodeBranchIcon />}
        isClicked={isActive}
      >
        {count > 0 && <Badge isRead>{count}</Badge>}
      </Button>
    </Tooltip>
  );
};
