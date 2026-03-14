import React from "react";

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
  Icon,
  Label,
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
import CalendarAltIcon from "@patternfly/react-icons/dist/esm/icons/calendar-alt-icon";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";
import EllipsisVIcon from "@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon";
import InProgressIcon from "@patternfly/react-icons/dist/esm/icons/in-progress-icon";
import PendingIcon from "@patternfly/react-icons/dist/esm/icons/pending-icon";
import SortAmountDownIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-down-icon";
import SortAmountUpIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-up-icon";
import BookOpenIcon from "@patternfly/react-icons/dist/esm/icons/book-open-icon";

import type { TaskDto, TaskStatus } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { ConditionalDataListBody } from "@app/components/DataListControls";
import { FilterToolbar } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import { useUpdateTaskPlanMutation } from "@app/queries/tasks";
import { formatDateTime } from "@app/utils/utils";
import { ButtonVariant } from "@patternfly/react-core";

import { ManualPlanModal } from "./components/manual-plan-modal";
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
  const [planModalTask, setPlanModalTask] = React.useState<TaskDto | null>(
    null,
  );
  const [approveTask, setApproveTask] = React.useState<TaskDto | null>(null);

  const updatePlanMutation = useUpdateTaskPlanMutation(() =>
    setApproveTask(null),
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
                    <DataListCell key="info" width={3}>
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
                            ({task.type})
                          </small>
                        </FlexItem>
                        <FlexItem>
                          <Label isCompact>{task.externalStatus}</Label>
                        </FlexItem>
                      </Flex>
                    </DataListCell>,
                    <DataListCell key="plan" width={1}>
                      <Flex
                        direction={{ default: "column" }}
                        gap={{ default: "gapXs" }}
                      >
                        <FlexItem>
                          {task.plan ? (
                            <Button
                              variant="link"
                              isInline
                              onClick={() => setPlanModalTask(task)}
                            >
                              <Icon size="md" isInline>
                                {task.plan.status === "APPROVED" ? (
                                  <CheckCircleIcon color="var(--pf-t--global--color--status--success--default)" />
                                ) : (
                                  <BookOpenIcon />
                                )}
                              </Icon>{" "}
                              {task.plan.status === "APPROVED"
                                ? "Approved"
                                : "In progress"}
                            </Button>
                          ) : (
                            "No plan"
                          )}
                        </FlexItem>
                        {task.plan?.status === "IN_PROGRESS" && (
                          <FlexItem>
                            <Button
                              variant="primary"
                              size="sm"
                              onClick={() => setApproveTask(task)}
                            >
                              Approve
                            </Button>
                          </FlexItem>
                        )}
                      </Flex>
                    </DataListCell>,
                    <DataListCell key="dates" width={2}>
                      <Flex
                        direction={{ default: "column" }}
                        gap={{ default: "gapXs" }}
                      >
                        <FlexItem>
                          <Icon size="sm" isInline>
                            <CalendarAltIcon />
                          </Icon>{" "}
                          Created: {formatDateTime(task.createdAt)}
                        </FlexItem>
                        <FlexItem>
                          <Icon size="sm" isInline>
                            <CalendarAltIcon />
                          </Icon>{" "}
                          Updated: {formatDateTime(task.updatedAt)}
                        </FlexItem>
                      </Flex>
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
                      <DropdownItem
                        key="manual-plan"
                        onClick={() => setPlanModalTask(task)}
                      >
                        Manual plan
                      </DropdownItem>
                    </DropdownList>
                  </Dropdown>
                </DataListAction>
              </DataListItemRow>
              <DataListContent
                aria-label={`Details for ${task.title}`}
                isHidden={!expansionDerivedState.isCellExpanded(task)}
              >
                <DescriptionList>
                  <DescriptionListGroup>
                    <DescriptionListTerm>Project</DescriptionListTerm>
                    <DescriptionListDescription>
                      {task.project.name}
                    </DescriptionListDescription>
                  </DescriptionListGroup>
                </DescriptionList>
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

      <ManualPlanModal
        task={planModalTask}
        isOpen={planModalTask !== null}
        onClose={() => setPlanModalTask(null)}
      />

      <ConfirmDialog
        isOpen={approveTask !== null}
        title="Approve plan"
        titleIconVariant="warning"
        message="Are you sure you want to approve this plan?"
        confirmBtnLabel="Approve"
        cancelBtnLabel="Cancel"
        confirmBtnVariant={ButtonVariant.primary}
        inProgress={updatePlanMutation.isPending}
        onConfirm={() => {
          if (approveTask?.plan) {
            updatePlanMutation.mutate({
              taskId: approveTask.id,
              plan: { ...approveTask.plan, status: "APPROVED" },
            });
          }
        }}
        onClose={() => setApproveTask(null)}
        onCancel={() => setApproveTask(null)}
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
