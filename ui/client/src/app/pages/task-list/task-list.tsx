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
  Icon,
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
  Tooltip,
} from "@patternfly/react-core";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";
import CloseIcon from "@patternfly/react-icons/dist/esm/icons/close-icon";
import CodeIcon from "@patternfly/react-icons/dist/esm/icons/code-icon";
import EllipsisVIcon from "@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon";
import InProgressIcon from "@patternfly/react-icons/dist/esm/icons/in-progress-icon";
import PendingIcon from "@patternfly/react-icons/dist/esm/icons/pending-icon";
import SortAmountDownIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-down-icon";
import SortAmountUpIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-up-icon";
import TerminalIcon from "@patternfly/react-icons/dist/esm/icons/terminal-icon";

import type { TaskDto, TaskStatus } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { ConditionalDataListBody } from "@app/components/DataListControls";
import { FilterToolbar } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import {
  useCreateChangeRequestMutation,
  useCreateTaskPlanMutation,
  useOpenTerminalMutation,
  useOpenVSCodeMutation,
  usePatchTaskPlanMutation,
} from "@app/queries/tasks";
import { formatDateTime } from "@app/utils/utils";
import { ButtonVariant } from "@patternfly/react-core";

import { ExecutionOutputModal } from "./components/execution-output-modal";
import { PlanProgressStepper } from "./components/plan-progress-stepper";
import { PlanWizardModal } from "./components/plan-wizard-modal";
import { TaskSearchContext, TaskSearchProvider } from "./task-context";

const statusIcon = (status: TaskStatus) => {
  switch (status) {
    case "OPEN":
      return <PendingIcon />;
    case "IN_PROGRESS":
      return <InProgressIcon />;
    case "CLOSED":
      return (
        <CheckCircleIcon color="var(--pf-t--global--color--status--success--default)" />
      );
  }
};

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

  const [isSortByOpen, setIsSortByOpen] = React.useState(false);
  const [openKebabId, setOpenKebabId] = React.useState<number | null>(null);
  const [wizardTask, setWizardTask] = React.useState<TaskDto | null>(null);
  const [wizardInitialStep, setWizardInitialStep] = React.useState(1);
  const [createPlanTask, setCreatePlanTask] = React.useState<TaskDto | null>(
    null,
  );
  const [clearClaudeTask, setClearClaudeTask] = React.useState<TaskDto | null>(
    null,
  );
  const [outputTaskId, setOutputTaskId] = React.useState<number | null>(null);

  const createPlanMutation = useCreateTaskPlanMutation(() =>
    setCreatePlanTask(null),
  );
  const openVSCodeMutation = useOpenVSCodeMutation();
  const openTerminalMutation = useOpenTerminalMutation();
  const changeRequestMutation = useCreateChangeRequestMutation();
  const patchPlanMutation = usePatchTaskPlanMutation(() =>
    setClearClaudeTask(null),
  );

  return (
    <>
      <Toolbar {...toolbarProps}>
        <ToolbarContent>
          <FilterToolbar {...filterToolbarProps} />

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
                    <DataListCell isIcon key="icon" width={1}>
                      <Icon>{statusIcon(task.status)}</Icon>
                    </DataListCell>,
                    <DataListCell key="info" width={2}>
                      <Flex
                        direction={{ default: "column" }}
                        gap={{ default: "gapXs" }}
                      >
                        <FlexItem>{task.title}</FlexItem>
                        <FlexItem>
                          <small>
                            <a
                              id={`task-${task.id}`}
                              href={task.url}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              {task.type === "GITHUB" && "#"}
                              {task.externalId}
                            </a>{" "}
                            {task.project.name} ({task.type.toLowerCase()})
                          </small>
                        </FlexItem>
                        <FlexItem>
                          Status: {task.externalStatus}
                        </FlexItem>
                      </Flex>
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
                              onEditStep={(step) => {
                                setWizardTask(task);
                                setWizardInitialStep(step);
                              }}
                              onChangeRequest={() =>
                                changeRequestMutation.mutate(task.id)
                              }
                            />
                          </FlexItem>
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
                    <DataListCell key="outcomes" alignRight>
                      {task?.plan?.git && (
                        <FlexItem>
                          <Flex gap={{ default: "gapMd" }}>
                            <FlexItem>
                              <Tooltip content="Open VSCode">
                                <Button
                                  variant="control"
                                  size="sm"
                                  onClick={() =>
                                    openVSCodeMutation.mutate(task.id)
                                  }
                                  isLoading={openVSCodeMutation.isPending}
                                  isDisabled={openVSCodeMutation.isPending}
                                  icon={<CodeIcon />}
                                  aria-label="Open VSCode"
                                >
                                  VSCode
                                </Button>
                              </Tooltip>{" "}
                              <Tooltip content="Open Terminal">
                                <Button
                                  variant="control"
                                  size="sm"
                                  onClick={() =>
                                    openTerminalMutation.mutate(task.id)
                                  }
                                  isLoading={openTerminalMutation.isPending}
                                  isDisabled={openTerminalMutation.isPending}
                                  icon={<TerminalIcon />}
                                  aria-label="Open Terminal"
                                >
                                  Terminal
                                </Button>
                              </Tooltip>
                            </FlexItem>
                            {task.plan.claudeSessionId && (
                              <FlexItem>
                                <Tooltip content="Clear Claude session">
                                  <Button
                                    variant="control"
                                    size="sm"
                                    onClick={() => setClearClaudeTask(task)}
                                    icon={<CloseIcon />}
                                    aria-label="Clear Claude session"
                                  ></Button>
                                </Tooltip>
                              </FlexItem>
                            )}
                          </Flex>
                        </FlexItem>
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
                      <DropdownItem
                        key="view"
                        onClick={() =>
                          window.open(task.url, "_blank", "noopener,noreferrer")
                        }
                      >
                        View
                      </DropdownItem>
                      {!task.plan && (
                        <DropdownItem
                          key="create-plan"
                          onClick={() => setCreatePlanTask(task)}
                        >
                          Create plan
                        </DropdownItem>
                      )}
                      {task.plan && (
                        <DropdownItem
                          key="edit-plan"
                          onClick={() => {
                            setWizardTask(task);
                            setWizardInitialStep(1);
                          }}
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
                          {task.project.name}
                        </DescriptionListDescription>
                      </DescriptionListGroup>
                      <DescriptionListGroup>
                        <DescriptionListTerm>Labels</DescriptionListTerm>
                        <DescriptionListDescription>
                          <LabelGroup>
                            {task.labels?.map(label => <Label key={label}>{label}</Label>)}
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

      <ExecutionOutputModal
        taskId={outputTaskId}
        isOpen={outputTaskId !== null}
        onClose={() => setOutputTaskId(null)}
      />

      <PlanWizardModal
        task={wizardTask}
        isOpen={wizardTask !== null}
        onClose={() => setWizardTask(null)}
        initialStep={wizardInitialStep}
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

      <ConfirmDialog
        isOpen={clearClaudeTask !== null}
        title="Clear Claude session"
        titleIconVariant="warning"
        message="Are you sure you want to clear the Claude session? This will start a new session next time you open Claude."
        confirmBtnLabel="Clear"
        cancelBtnLabel="Cancel"
        confirmBtnVariant={ButtonVariant.danger}
        inProgress={patchPlanMutation.isPending}
        onConfirm={() => {
          if (clearClaudeTask) {
            patchPlanMutation.mutate({
              taskId: clearClaudeTask.id,
              plan: { claudeSessionId: "" },
            });
          }
        }}
        onClose={() => setClearClaudeTask(null)}
        onCancel={() => setClearClaudeTask(null)}
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
