import React from "react";

import ReactMarkdown from "react-markdown";
import { Link } from "react-router-dom";

import {
  Button,
  DataListAction,
  DataListCell,
  DataListContent,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  DataListToggle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Dropdown,
  DropdownItem,
  DropdownList,
  Flex,
  FlexItem,
  Grid,
  GridItem,
  Label,
  LabelGroup,
  MenuToggle,
} from "@patternfly/react-core";
import EllipsisVIcon from "@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon";
import TerminalIcon from "@patternfly/react-icons/dist/esm/icons/terminal-icon";

import type { TaskDto } from "@app/api/models";
import { formatDateTime } from "@app/utils/utils";

import { PlanProgressStepper } from "./plan-progress-stepper";
import { WorkspaceCell } from "./workspace-cell";

interface TaskDataListItemProps {
  task: TaskDto;
  isExpanded: boolean;
  onToggleExpand: () => void;
  onEditRequirement: () => void;
  onEditPlan: () => void;
  onViewOutput: () => void;
  onCreatePlan: () => void;
  onRunAll: () => void;
  onChangeRequest: () => void;
}

export const TaskDataListItem: React.FC<TaskDataListItemProps> = ({
  task,
  isExpanded,
  onToggleExpand,
  onEditRequirement,
  onEditPlan,
  onViewOutput,
  onCreatePlan,
  onRunAll,
  onChangeRequest,
}) => {
  const [isKebabOpen, setIsKebabOpen] = React.useState(false);

  return (
    <DataListItem
      key={task.id}
      aria-labelledby={`task-${task.id}`}
      isExpanded={isExpanded}
    >
      <DataListItemRow>
        <DataListToggle
          id={`task-toggle-${task.id}`}
          onClick={onToggleExpand}
          isExpanded={isExpanded}
        />
        <DataListItemCells
          dataListCells={[
            <DataListCell key="info" width={2}>
              <Flex
                direction={{ default: "column" }}
                gap={{ default: "gapXs" }}
              >
                <FlexItem>
                  <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                </FlexItem>
                <FlexItem>
                  <small>
                    {task.url ? (
                      <a
                        id={`task-${task.id}`}
                        href={task.url}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {task.type === "GITHUB" && "#"}
                        {task.externalId}
                      </a>
                    ) : (
                      <span id={`task-${task.id}`}>{task.externalId}</span>
                    )}{" "}
                    {task.project?.name} ({task.type.toLowerCase()})
                  </small>
                </FlexItem>
                <FlexItem>Status: {task.externalStatus}</FlexItem>
              </Flex>
            </DataListCell>,
            <DataListCell key="workspace" width={2}>
              <WorkspaceCell task={task} />
            </DataListCell>,
            <DataListCell key="plan" width={3} isFilled>
              {task.plan ? (
                <Flex
                  direction={{ default: "column" }}
                  gap={{ default: "gapXs" }}
                >
                  <FlexItem>
                    <PlanProgressStepper
                      taskId={task.id}
                      plan={task.plan}
                      workspace={task.workspace}
                      onEditRequirement={onEditRequirement}
                      onEditPlan={onEditPlan}
                      onChangeRequest={onChangeRequest}
                    />
                  </FlexItem>
                  {task.plan?.changeRequestUrl && (
                    <FlexItem>
                      PR:{" "}
                      <a
                        href={task.plan.changeRequestUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        #
                        {task.plan.changeRequestUrl.match(/\/(\d+)\/?$/)?.[1] ??
                          "PR"}
                      </a>
                    </FlexItem>
                  )}
                  {(task.plan.isPlanGenerationInProgress ||
                    task.plan.isExecutionPlanInProgress) && (
                    <FlexItem>
                      <Button
                        variant="link"
                        size="sm"
                        icon={<TerminalIcon />}
                        onClick={onViewOutput}
                      >
                        View Output
                      </Button>
                    </FlexItem>
                  )}
                </Flex>
              ) : (
                "No plan"
              )}
            </DataListCell>,
          ]}
        />
        <DataListAction
          id={`task-action-${task.id}`}
          aria-label="Actions"
          aria-labelledby={`task-${task.id} task-action-${task.id}`}
        >
          <Dropdown
            isOpen={isKebabOpen}
            onSelect={() => setIsKebabOpen(false)}
            onOpenChange={setIsKebabOpen}
            toggle={(toggleRef) => (
              <MenuToggle
                ref={toggleRef}
                aria-label="Kebab toggle"
                variant="plain"
                onClick={() => setIsKebabOpen((prev) => !prev)}
                isExpanded={isKebabOpen}
              >
                <EllipsisVIcon />
              </MenuToggle>
            )}
            popperProps={{ position: "right" }}
          >
            <DropdownList>
              {!task.plan && (
                <DropdownItem key="create-plan" onClick={onCreatePlan}>
                  Create plan
                </DropdownItem>
              )}
              {task.workspace?.workspaceId && (
                <DropdownItem key="run-all" onClick={onRunAll}>
                  Create plan and run
                </DropdownItem>
              )}
              {task.plan && (
                <DropdownItem
                  key="edit-requirement"
                  onClick={onEditRequirement}
                >
                  Edit requirement
                </DropdownItem>
              )}
              {task.plan && (
                <DropdownItem key="edit-plan" onClick={onEditPlan}>
                  Edit plan
                </DropdownItem>
              )}
            </DropdownList>
          </Dropdown>
        </DataListAction>
      </DataListItemRow>
      <DataListContent
        aria-label={`Details for ${task.title}`}
        isHidden={!isExpanded}
      >
        <Grid hasGutter>
          <GridItem md={2}>
            <DescriptionList>
              <DescriptionListGroup>
                <DescriptionListTerm>Project</DescriptionListTerm>
                <DescriptionListDescription>
                  {task.project?.name ?? "-"}
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>Labels</DescriptionListTerm>
                <DescriptionListDescription>
                  <LabelGroup>
                    {task.labels?.map((label) => (
                      <Label key={label}>{label}</Label>
                    ))}
                  </LabelGroup>
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>Created</DescriptionListTerm>
                <DescriptionListDescription>
                  {formatDateTime(task.createdAt)}
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>Updated</DescriptionListTerm>
                <DescriptionListDescription>
                  {formatDateTime(task.updatedAt)}
                </DescriptionListDescription>
              </DescriptionListGroup>
            </DescriptionList>
          </GridItem>
          <GridItem md={10}>
            <DescriptionList>
              <DescriptionListGroup>
                <DescriptionListTerm>Description</DescriptionListTerm>
                <DescriptionListDescription>
                  <ReactMarkdown>{task.description}</ReactMarkdown>
                </DescriptionListDescription>
              </DescriptionListGroup>
            </DescriptionList>
          </GridItem>
        </Grid>
      </DataListContent>
    </DataListItem>
  );
};
