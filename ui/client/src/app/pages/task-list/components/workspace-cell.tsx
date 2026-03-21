import React from "react";

import {
  Button,
  Flex,
  FlexItem,
  MenuToggle,
  Select,
  SelectList,
  SelectOption,
} from "@patternfly/react-core";

import type { GitDto, TaskDto } from "@app/api/models";
import { useFetchGits } from "@app/queries/gits";
import { useFetchMappings } from "@app/queries/project-git-mappings";
import { useCreateWorkspaceAndLinkMutation } from "@app/queries/tasks";

interface WorkspaceCellProps {
  task: TaskDto;
}

function resolveGitId(
  task: TaskDto,
  gits: GitDto[] | undefined,
  mappings: { gitId: number; space: string; labels: string[] }[] | undefined,
): string {
  if (!gits || gits.length === 0) return "";

  if (task.type === "GITHUB" && task.project.apiUrl) {
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
  const { data: mappings } = useFetchMappings(task.project.id);
  const createMutation = useCreateWorkspaceAndLinkMutation();

  const defaultGitId = React.useMemo(
    () => resolveGitId(task, gits, mappings),
    [task, gits, mappings],
  );

  const [selectedGitId, setSelectedGitId] = React.useState<string>("");
  const [isOpen, setIsOpen] = React.useState(false);

  React.useEffect(() => {
    if (!selectedGitId && defaultGitId) {
      setSelectedGitId(defaultGitId);
    }
  }, [defaultGitId, selectedGitId]);

  if (task.workspace?.git) {
    return (
      <Flex direction={{ default: "column" }} gap={{ default: "gapXs" }}>
        <FlexItem>
          <small>{task.workspace.git.url}</small>
        </FlexItem>
        {task.workspace.git.branch && (
          <FlexItem>
            <small>Branch: {task.workspace.git.branch}</small>
          </FlexItem>
        )}
      </Flex>
    );
  }

  const selectedGit = gits?.find((g) => String(g.id) === selectedGitId);

  const handleCreate = () => {
    if (!selectedGitId) return;
    createMutation.mutate({
      gitId: Number(selectedGitId),
      taskId: task.id,
    });
  };

  return (
    <Flex direction={{ default: "column" }} gap={{ default: "gapSm" }}>
      <FlexItem>
        <Select
          isOpen={isOpen}
          selected={selectedGitId || undefined}
          onSelect={(_event, value) => {
            setSelectedGitId(String(value));
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
        <Button
          variant="primary"
          size="sm"
          onClick={handleCreate}
          isDisabled={!selectedGitId || createMutation.isPending}
          isLoading={createMutation.isPending}
        >
          {createMutation.isPending ? "Creating..." : "Create workspace"}
        </Button>
      </FlexItem>
    </Flex>
  );
};
