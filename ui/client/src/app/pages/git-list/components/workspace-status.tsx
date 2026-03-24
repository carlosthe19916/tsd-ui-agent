import React from "react";

import { Button, Flex, FlexItem, Label, Tooltip } from "@patternfly/react-core";
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  FolderIcon,
  InProgressIcon,
  PausedIcon,
  PlayIcon,
  StopIcon,
  TerminalIcon,
} from "@patternfly/react-icons";

import { DockerIcon, VscodeIcon } from "./icons";

import type { WorkspaceDto } from "@app/api/models";
import {
  useFetchWorkspaceCommands,
  useFetchWorkspaceStatus,
  useStartWorkspaceMutation,
  useStopWorkspaceMutation,
} from "@app/queries/gits";

export type WorkspaceType = "filesystem" | "docker" | "kubernetes";

export const workspaceType = (
  workspaceId?: string,
): WorkspaceType | undefined => {
  if (!workspaceId) return undefined;
  if (workspaceId.includes(":")) return "docker";
  if (workspaceId.startsWith("tsd-ws-")) return "kubernetes";
  return "filesystem";
};

const typeLabel: Record<
  WorkspaceType,
  { text: string; color: "grey" | "blue" | "purple" }
> = {
  filesystem: { text: "Filesystem", color: "grey" },
  docker: { text: "Docker", color: "blue" },
  kubernetes: { text: "Kubernetes", color: "purple" },
};

export const parseWorkspaceId = (workspaceId?: string) => {
  if (!workspaceId) return { containerId: undefined, path: undefined };
  const colonIdx = workspaceId.indexOf(":");
  if (colonIdx >= 0) {
    return {
      containerId: workspaceId.substring(0, colonIdx),
      path: workspaceId.substring(colonIdx + 1),
    };
  }
  return { containerId: undefined, path: workspaceId };
};

export const WorkspaceTypeLabel: React.FC<{ workspaceId?: string }> = ({
  workspaceId,
}) => {
  const type = workspaceType(workspaceId);
  if (!type) return <Label color="grey">-</Label>;
  const { text, color } = typeLabel[type];
  return (
    <Label color={color} isCompact>
      {text}
    </Label>
  );
};

export const WorkspaceStatusLabel: React.FC<{ ws: WorkspaceDto }> = ({
  ws,
}) => {
  const isProvisioned =
    !ws.isProvisioningInProgress && !ws.provisioningError && !!ws.workspaceId;

  const { data: health, isLoading } = useFetchWorkspaceStatus(
    ws.id,
    isProvisioned,
  );

  const startMutation = useStartWorkspaceMutation();
  const stopMutation = useStopWorkspaceMutation();
  const isMutating = startMutation.isPending || stopMutation.isPending;

  if (ws.isProvisioningInProgress) {
    return (
      <Label color="blue" icon={<InProgressIcon />}>
        Provisioning
      </Label>
    );
  }

  if (ws.provisioningError) {
    return (
      <Tooltip content={ws.provisioningError}>
        <Label color="red" icon={<ExclamationCircleIcon />} isCompact>
          Error
        </Label>
      </Tooltip>
    );
  }

  if (!ws.workspaceId) {
    return <Label color="grey">Pending</Label>;
  }

  if (isLoading) {
    return (
      <Label color="blue" icon={<InProgressIcon />}>
        Checking
      </Label>
    );
  }

  const statusLabel = (() => {
    switch (health?.status) {
      case "RUNNING":
        return (
          <Label color="green" icon={<CheckCircleIcon />}>
            Running
          </Label>
        );
      case "STOPPED":
        return (
          <Tooltip content={health.reason ?? "Stopped"}>
            <Label color="gold" icon={<PausedIcon />}>
              Stopped
            </Label>
          </Tooltip>
        );
      case "ERROR":
        return (
          <Tooltip content={health.reason ?? "Unknown error"}>
            <Label color="red" icon={<ExclamationCircleIcon />}>
              Error
            </Label>
          </Tooltip>
        );
      default:
        return <Label color="grey">Unknown</Label>;
    }
  })();

  if (!health?.supportsStartStop) {
    return statusLabel;
  }

  return (
    <Flex
      gap={{ default: "gapSm" }}
      alignItems={{ default: "alignItemsCenter" }}
    >
      <FlexItem>{statusLabel}</FlexItem>
      <FlexItem>
        {health.status === "RUNNING" && (
          <Button
            variant="plain"
            size="sm"
            aria-label="Stop workspace"
            onClick={() => stopMutation.mutate(ws.id as number)}
            isDisabled={isMutating}
            isLoading={stopMutation.isPending}
            icon={<StopIcon />}
          />
        )}
        {health.status === "STOPPED" && (
          <Button
            variant="plain"
            size="sm"
            aria-label="Start workspace"
            onClick={() => startMutation.mutate(ws.id as number)}
            isDisabled={isMutating}
            isLoading={startMutation.isPending}
            icon={<PlayIcon />}
          />
        )}
      </FlexItem>
    </Flex>
  );
};

const commandIcon: Record<string, React.ReactNode> = {
  NAVIGATE: <FolderIcon />,
  CONTAINER_EXEC: <DockerIcon />,
  REMOTE_EXEC: <TerminalIcon />,
  VSCODE: <VscodeIcon />,
};

export const WorkspaceCommands: React.FC<{ ws: WorkspaceDto }> = ({ ws }) => {
  const isProvisioned =
    !ws.isProvisioningInProgress && !ws.provisioningError && !!ws.workspaceId;

  const { data: commands } = useFetchWorkspaceCommands(ws.id, isProvisioned);
  const { data: health } = useFetchWorkspaceStatus(ws.id, isProvisioned);
  const [copiedIndex, setCopiedIndex] = React.useState<number | null>(null);

  if (!commands || commands.length === 0) return null;

  const wsType = workspaceType(ws.workspaceId);
  const disableNonNavigate =
    wsType === "docker" && health?.status !== "RUNNING";

  const handleCopy = (command: string, index: number) => {
    navigator.clipboard.writeText(command);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 1500);
  };

  return (
    <Flex gap={{ default: "gapSm" }}>
      {commands.map((cmd, index) => {
        const isDisabled = disableNonNavigate && cmd.type !== "NAVIGATE";
        return (
          <FlexItem key={cmd.type}>
            <Tooltip content={copiedIndex === index ? "Copied!" : cmd.command}>
              <Button
                variant="control"
                size="sm"
                icon={commandIcon[cmd.type]}
                isDisabled={isDisabled}
                onClick={() => !isDisabled && handleCopy(cmd.command, index)}
              >
                {cmd.label}
              </Button>
            </Tooltip>
          </FlexItem>
        );
      })}
    </Flex>
  );
};
