import React from "react";

import ReactMarkdown from "react-markdown";

import {
  Button,
  DataList,
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
  PageSection,
  Select,
  SelectList,
  SelectOption,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import EllipsisVIcon from "@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon";
import SortAmountDownIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-down-icon";
import SortAmountUpIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-up-icon";
import TerminalIcon from "@patternfly/react-icons/dist/esm/icons/terminal-icon";

import type { TaskDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { ConditionalDataListBody } from "@app/components/DataListControls";
import { FilterToolbar } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import {
  useCreateChangeRequestMutation,
  useCreateTaskPlanMutation,
  useRunAllPlanPhasesMutation,
} from "@app/queries/tasks";
import { formatDateTime } from "@app/utils/utils";
import { ButtonVariant } from "@patternfly/react-core";

import { ExecutionOutputModal } from "./components/execution-output-modal";
import { PlanProgressStepper } from "./components/plan-progress-stepper";
import { TaskFormModal } from "./components/task-form-modal";
import { WorkspaceCell } from "./components/workspace-cell";
import { RequirementModal, PlanModal } from "./components/plan-wizard-modal";
import { TaskSearchContext, TaskSearchProvider } from "./task-context";

const TaskListContent: React.FC = () => {
  const { tableControls, totalItemCount, isFetching, fetchError } =
    React.useContext(TaskSearchContext);

  const {
    currentPageItems,
    expansionDerivedState,
    sortableColumns,
    columnNames,
    sortState: { activeSort, setActiveSort },
    propHelpers: {
      toolbarProps,
      paginationToolbarItemProps,
      paginationProps,
      filterToolbarProps,
    },
  } = tableControls;

  const [isCreateModalOpen, setIsCreateModalOpen] = React.useState(false);
  const [isSortByOpen, setIsSortByOpen] = React.useState(false);
  const [openKebabId, setOpenKebabId] = React.useState<number | null>(null);
  const [requirementTask, setRequirementTask] = React.useState<TaskDto | null>(
    null,
  );
  const [planTask, setPlanTask] = React.useState<TaskDto | null>(null);
  const [createPlanTask, setCreatePlanTask] = React.useState<TaskDto | null>(
    null,
  );
  const [outputTaskId, setOutputTaskId] = React.useState<number | null>(null);
  const [runAllTask, setRunAllTask] = React.useState<TaskDto | null>(null);

  const createPlanMutation = useCreateTaskPlanMutation(() =>
    setCreatePlanTask(null),
  );
  const changeRequestMutation = useCreateChangeRequestMutation();
  const runAllMutation = useRunAllPlanPhasesMutation(() => setRunAllTask(null));

  return (
    <>
      <Toolbar {...toolbarProps}>
        <ToolbarContent>
          <FilterToolbar {...filterToolbarProps} />

          <ToolbarItem>
            <Button
              variant="primary"
              onClick={() => setIsCreateModalOpen(true)}
            >
              Create task
            </Button>
          </ToolbarItem>

          <ToolbarGroup>
            <ToolbarItem>
              <Button
                variant="control"
                onClick={() =>
                  setActiveSort({
                    columnKey: activeSort?.columnKey ?? "createdAt",
                    direction: activeSort?.direction === "asc" ? "desc" : "asc",
                  })
                }
                aria-label="Sort direction"
              >
                {activeSort?.direction === "asc" ? (
                  <SortAmountUpIcon />
                ) : (
                  <SortAmountDownIcon />
                )}
              </Button>
            </ToolbarItem>
            <ToolbarItem>
              <Select
                isOpen={isSortByOpen}
                onSelect={(_event, value) => {
                  setActiveSort({
                    columnKey: value as typeof activeSort extends {
                      columnKey: infer K;
                    }
                      ? K
                      : never,
                    direction: activeSort?.direction ?? "desc",
                  });
                  setIsSortByOpen(false);
                }}
                onOpenChange={setIsSortByOpen}
                toggle={(toggleRef) => (
                  <MenuToggle
                    ref={toggleRef}
                    onClick={() => setIsSortByOpen(!isSortByOpen)}
                    isExpanded={isSortByOpen}
                  >
                    {activeSort ? columnNames[activeSort.columnKey] : "Sort by"}
                  </MenuToggle>
                )}
              >
                <SelectList>
                  {sortableColumns?.map((columnKey) => (
                    <SelectOption key={columnKey} value={columnKey}>
                      {columnNames[columnKey]}
                    </SelectOption>
                  ))}
                </SelectList>
              </Select>
            </ToolbarItem>
          </ToolbarGroup>

          <ToolbarItem {...paginationToolbarItemProps}>
            <SimplePagination
              idPrefix="tasks-table"
              isTop
              paginationProps={paginationProps}
            />
          </ToolbarItem>
        </ToolbarContent>
      </Toolbar>

      <ConditionalDataListBody
        isLoading={isFetching}
        isError={!!fetchError}
        isNoData={totalItemCount === 0}
      >
        <DataList aria-label="Tasks list">
          {currentPageItems?.map((task: TaskDto) => (
            <DataListItem
              key={task.id}
              aria-labelledby={`task-${task.id}`}
              isExpanded={expansionDerivedState.isCellExpanded(task)}
            >
              <DataListItemRow>
                <DataListToggle
                  id={`task-toggle-${task.id}`}
                  onClick={() =>
                    expansionDerivedState.setCellExpanded({
                      item: task,
                      isExpanding: !expansionDerivedState.isCellExpanded(task),
                    })
                  }
                  isExpanded={expansionDerivedState.isCellExpanded(task)}
                />
                <DataListItemCells
                  dataListCells={[
                    <DataListCell key="info" width={2}>
                      <Flex
                        direction={{ default: "column" }}
                        gap={{ default: "gapXs" }}
                      >
                        <FlexItem>{task.title}</FlexItem>
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
                              <span id={`task-${task.id}`}>
                                {task.externalId}
                              </span>
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
                              onEditRequirement={() => setRequirementTask(task)}
                              onEditPlan={() => setPlanTask(task)}
                              onChangeRequest={() =>
                                changeRequestMutation.mutate(task.id)
                              }
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
                                {task.plan.changeRequestUrl.match(
                                  /\/(\d+)\/?$/,
                                )?.[1] ?? "PR"}
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
                                onClick={() => setOutputTaskId(task.id)}
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
                    isOpen={openKebabId === task.id}
                    onSelect={() => setOpenKebabId(null)}
                    onOpenChange={(isOpen) =>
                      setOpenKebabId(isOpen ? task.id : null)
                    }
                    toggle={(toggleRef) => (
                      <MenuToggle
                        ref={toggleRef}
                        aria-label="Kebab toggle"
                        variant="plain"
                        onClick={() =>
                          setOpenKebabId(
                            openKebabId === task.id ? null : task.id,
                          )
                        }
                        isExpanded={openKebabId === task.id}
                      >
                        <EllipsisVIcon />
                      </MenuToggle>
                    )}
                    popperProps={{ position: "right" }}
                  >
                    <DropdownList>
                      {!task.plan && (
                        <DropdownItem
                          key="create-plan"
                          onClick={() => setCreatePlanTask(task)}
                        >
                          Create plan
                        </DropdownItem>
                      )}
                      {task.workspace?.workspaceId && (
                        <DropdownItem
                          key="run-all"
                          onClick={() => setRunAllTask(task)}
                        >
                          Create plan and run
                        </DropdownItem>
                      )}
                      {task.plan && (
                        <DropdownItem
                          key="edit-requirement"
                          onClick={() => setRequirementTask(task)}
                        >
                          Edit requirement
                        </DropdownItem>
                      )}
                      {task.plan && (
                        <DropdownItem
                          key="edit-plan"
                          onClick={() => setPlanTask(task)}
                        >
                          Edit plan
                        </DropdownItem>
                      )}
                    </DropdownList>
                  </Dropdown>
                </DataListAction>
              </DataListItemRow>
              <DataListContent
                aria-label={`Details for ${task.title}`}
                isHidden={!expansionDerivedState.isCellExpanded(task)}
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
          ))}
        </DataList>
      </ConditionalDataListBody>

      <SimplePagination
        idPrefix="tasks-table"
        isTop={false}
        paginationProps={paginationProps}
      />

      <TaskFormModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
      />

      <ExecutionOutputModal
        taskId={outputTaskId}
        isOpen={outputTaskId !== null}
        onClose={() => setOutputTaskId(null)}
      />

      <RequirementModal
        task={requirementTask}
        isOpen={requirementTask !== null}
        onClose={() => setRequirementTask(null)}
      />

      <PlanModal
        task={planTask}
        isOpen={planTask !== null}
        onClose={() => setPlanTask(null)}
      />

      <ConfirmDialog
        isOpen={runAllTask !== null}
        title="Create plan and run"
        titleIconVariant="warning"
        message={`This will create a plan and run all phases (requirement enrichment, plan generation, execution, and change request) for "${runAllTask?.title}". This operation can take a long time.`}
        confirmBtnLabel="Run all"
        cancelBtnLabel="Cancel"
        confirmBtnVariant={ButtonVariant.primary}
        inProgress={runAllMutation.isPending}
        onConfirm={() => {
          if (runAllTask) {
            runAllMutation.mutate(runAllTask.id);
          }
        }}
        onClose={() => setRunAllTask(null)}
        onCancel={() => setRunAllTask(null)}
      />

      <ConfirmDialog
        isOpen={createPlanTask !== null}
        title="Create plan"
        titleIconVariant="info"
        message={`Create a new plan for "${createPlanTask?.title}"? This will auto-populate the requirement from the task description.`}
        confirmBtnLabel="Create"
        cancelBtnLabel="Cancel"
        confirmBtnVariant={ButtonVariant.primary}
        inProgress={createPlanMutation.isPending}
        onConfirm={() => {
          if (createPlanTask) {
            createPlanMutation.mutate({
              taskId: createPlanTask.id,
              plan: {
                plan: "",
              },
            });
          }
        }}
        onClose={() => setCreatePlanTask(null)}
        onCancel={() => setCreatePlanTask(null)}
      />
    </>
  );
};

export const TaskList: React.FC = () => {
  return (
    <>
      <PageSection>
        <Title headingLevel="h1" size="2xl">
          Tasks
        </Title>
      </PageSection>
      <PageSection>
        <TaskSearchProvider>
          <TaskListContent />
        </TaskSearchProvider>
      </PageSection>
    </>
  );
};
