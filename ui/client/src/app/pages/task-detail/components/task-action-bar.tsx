import { Button, Card, Flex, FlexItem, Tooltip } from "@patternfly/react-core";
import TerminalIcon from "@patternfly/react-icons/dist/esm/icons/terminal-icon";
import type React from "react";

import { GitChangesButton } from "@app/components/GitChangesButton";

export interface TaskActionBarProps {
  workspaceId: number;
  onGitDiffClick: () => void;
  isGitDiffActive: boolean;
  onTerminalClick: () => void;
  isTerminalActive: boolean;
}

export const TaskActionBar: React.FC<TaskActionBarProps> = ({
  workspaceId,
  onGitDiffClick,
  isGitDiffActive,
  onTerminalClick,
  isTerminalActive,
}) => {
  return (
    <Card>
      <Flex direction={{ default: "column" }}>
        <FlexItem>
          <GitChangesButton
            workspaceId={workspaceId}
            onClick={onGitDiffClick}
            isActive={isGitDiffActive}
            tooltipPosition="left"
          />
        </FlexItem>
        <FlexItem>
          <Tooltip content="Terminal" position="left">
            <Button
              variant="plain"
              aria-label="Terminal"
              onClick={onTerminalClick}
              icon={<TerminalIcon />}
              isClicked={isTerminalActive}
            />
          </Tooltip>
        </FlexItem>
      </Flex>
    </Card>
  );
};
