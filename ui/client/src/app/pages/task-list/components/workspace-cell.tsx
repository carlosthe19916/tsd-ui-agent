import React from "react";

import {
  Button,
  ButtonVariant,
  Dropdown,
  DropdownItem,
  DropdownList,
  Flex,
  FlexItem,
  MenuToggle,
  Select,
  SelectList,
  SelectOption,
  Tooltip,
} from "@patternfly/react-core";
import TerminalIcon from "@patternfly/react-icons/dist/esm/icons/terminal-icon";
import TrashIcon from "@patternfly/react-icons/dist/esm/icons/trash-icon";

import type { ExecutionMode, GitDto, TaskDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { ProvisioningOutputModal } from "./provisioning-output-modal";
import {
  WorkspaceCommands,
  WorkspaceStatusLabel,
  WorkspaceTypeLabel,
  parseWorkspaceId,
} from "@app/pages/git-list/components/workspace-status";
import { useFetchGits } from "@app/queries/gits";
import { useFetchMappings } from "@app/queries/project-git-mappings";
import {
  useCreateWorkspaceAndLinkMutation,
  useDeleteWorkspaceForTaskMutation,
} from "@app/queries/tasks";

interface WorkspaceCellProps {
  task: TaskDto;
}

function resolveGitId(
  task: TaskDto,
  gits: GitDto[] | undefined,
  mappings: { gitId: number; space: string; labels: string[] }[] | undefined,
): string {
  if (!gits || gits.length === 0) return "";

  if (task.type === "GITHUB" && task.project?.apiUrl) {
    const prefix = "https://api.github.com/repos/";
    if (task.project.apiUrl.startsWith(prefix)) {
      const ownerRepo = task.project.apiUrl.substring(prefix.length);
      const match = gits.find((g) => g.url.includes(ownerRepo));
      if (match) return String(match.id);
    }
  }

  if (task.type === "JIRA" && mappings && task.externalId) {
    const idx = task.externalId.indexOf("-");
    const space = idx > 0 ? task.externalId.substring(0, idx) : task.externalId;
    const taskLabels = new Set(task.labels ?? []);
    const match = mappings.find((m) => {
      if (m.space !== space) return false;
      if (!m.labels || m.labels.length === 0) return true;
      return m.labels.every((l) => taskLabels.has(l));
    });
    if (match) return String(match.gitId);
  }

  if (gits.length === 1) return String(gits[0].id);

  return "";
}

export const WorkspaceCell: React.FC<WorkspaceCellProps> = ({ task }) => {
  const { data: gits } = useFetchGits();
  const { data: mappings } = useFetchMappings(task.project?.id);
  const createMutation = useCreateWorkspaceAndLinkMutation();

  const [confirmDelete, setConfirmDelete] = React.useState(false);
  const [showProvisionLog, setShowProvisionLog] = React.useState(false);
  const deleteMutation = useDeleteWorkspaceForTaskMutation(() =>
    setConfirmDelete(false),
  );

  const defaultGitId = React.useMemo(
    () => resolveGitId(task, gits, mappings),
    [task, gits, mappings],
  );

  const [selectedGitIdOverride, setSelectedGitIdOverride] =
    React.useState<string>("");
  const [isOpen, setIsOpen] = React.useState(false);
  const [isCreateOpen, setIsCreateOpen] = React.useState(false);

  const selectedGitId = selectedGitIdOverride || defaultGitId;

  if (task.workspace) {
    const ws = task.workspace;
    const { containerId, path: _path } = parseWorkspaceId(ws.workspaceId);

    return (
      <>
        <Flex direction={{ default: "column" }} gap={{ default: "gapXs" }}>
          <FlexItem>
            <Flex
              alignItems={{ default: "alignItemsCenter" }}
              gap={{ default: "gapSm" }}
            >
              <FlexItem>
                <WorkspaceStatusLabel ws={ws} />
              </FlexItem>
              {ws.isProvisioningInProgress && (
                <FlexItem>
                  <Button
                    variant="link"
                    size="sm"
                    icon={<TerminalIcon />}
                    onClick={() => setShowProvisionLog(true)}
                  >
                    View Log
                  </Button>
                </FlexItem>
              )}
              <FlexItem>
                <Tooltip content="Delete workspace">
                  <Button
                    variant="plain"
                    size="sm"
                    isDanger
                    aria-label="Delete workspace"
                    icon={<TrashIcon />}
                    onClick={() => setConfirmDelete(true)}
                  />
                </Tooltip>
              </FlexItem>
            </Flex>
          </FlexItem>
          <FlexItem>
            <WorkspaceCommands ws={ws} />
          </FlexItem>
          <FlexItem>
            <small>{ws.git?.url}</small>
          </FlexItem>
          {ws.git?.branch && (
            <FlexItem>
              <small>Branch: {ws.git.branch}</small>
            </FlexItem>
          )}
          {containerId && (
            <FlexItem>
              <small>
                Container:{" "}
                <Tooltip content={containerId}>
                  <code>{containerId.substring(0, 12)}</code>
                </Tooltip>
              </small>
            </FlexItem>
          )}
          <FlexItem>
            <WorkspaceTypeLabel workspaceId={ws.workspaceId} />
          </FlexItem>
        </Flex>

        {confirmDelete && (
          <ConfirmDialog
            isOpen
            title="Delete workspace"
            titleIconVariant="warning"
            message={`Are you sure you want to delete workspace "${ws.workspaceId ?? ws.id}"?`}
            confirmBtnLabel="Delete"
            cancelBtnLabel="Cancel"
            confirmBtnVariant={ButtonVariant.danger}
            onClose={() => setConfirmDelete(false)}
            onConfirm={() => deleteMutation.mutate(ws.id as number)}
            onCancel={() => setConfirmDelete(false)}
            inProgress={deleteMutation.isPending}
          />
        )}

        <ProvisioningOutputModal
          wsId={ws.id ?? null}
          isOpen={showProvisionLog}
          onClose={() => setShowProvisionLog(false)}
        />
      </>
    );
  }

  const selectedGit = gits?.find((g) => String(g.id) === selectedGitId);

  const handleCreate = (executionMode: ExecutionMode) => {
    if (!selectedGitId) return;
    createMutation.mutate({
      gitId: Number(selectedGitId),
      taskId: task.id,
      executionMode,
    });
  };

  return (
    <Flex direction={{ default: "column" }} gap={{ default: "gapSm" }}>
      <FlexItem>
        <Select
          isOpen={isOpen}
          selected={selectedGitId || undefined}
          onSelect={(_event, value) => {
            setSelectedGitIdOverride(String(value));
            setIsOpen(false);
          }}
          onOpenChange={setIsOpen}
          toggle={(toggleRef) => (
            <MenuToggle
              ref={toggleRef}
              onClick={() => setIsOpen(!isOpen)}
              isExpanded={isOpen}
              isFullWidth
              style={{ maxWidth: 300 }}
            >
              {selectedGit ? selectedGit.url : "Select a repository"}
            </MenuToggle>
          )}
        >
          <SelectList>
            {gits?.map((git) => (
              <SelectOption
                key={git.id}
                value={String(git.id)}
                description={
                  [
                    git.branch ? `Branch: ${git.branch}` : null,
                    git.forkUrl ? `Fork: ${git.forkUrl}` : null,
                  ]
                    .filter(Boolean)
                    .join(" | ") || undefined
                }
              >
                {git.url}
              </SelectOption>
            ))}
          </SelectList>
        </Select>
      </FlexItem>
      <FlexItem>
        <Dropdown
          isOpen={isCreateOpen}
          onSelect={() => setIsCreateOpen(false)}
          onOpenChange={setIsCreateOpen}
          toggle={(toggleRef) => (
            <MenuToggle
              ref={toggleRef}
              onClick={() => setIsCreateOpen(!isCreateOpen)}
              isExpanded={isCreateOpen}
              variant="primary"
              isDisabled={!selectedGitId || createMutation.isPending}
            >
              {createMutation.isPending ? "Creating..." : "Create workspace"}
            </MenuToggle>
          )}
        >
          <DropdownList>
            <DropdownItem
              key="filesystem"
              onClick={() => handleCreate("FILESYSTEM")}
            >
              Local filesystem
            </DropdownItem>
            <DropdownItem
              key="container"
              onClick={() => handleCreate("DOCKER")}
            >
              Container
            </DropdownItem>
          </DropdownList>
        </Dropdown>
      </FlexItem>
    </Flex>
  );
};
