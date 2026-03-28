import React, { use } from "react";

import {
  Button,
  DataList,
  PageSection,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";

import type { TaskDto } from "@app/api/models";
import { ConditionalDataListBody } from "@app/components/DataListControls";
import { FilterToolbar } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import { useCreateChangeRequestMutation } from "@app/queries/tasks";

import { ExecutionOutputModal } from "./components/execution-output-modal";
import { RequirementModal, PlanModal } from "./components/plan-wizard-modal";
import { TaskDataListItem } from "./components/task-data-list-item";
import { TaskFormModal } from "./components/task-form-modal";
import { TaskSortControls } from "./components/task-sort-controls";
import { useTaskPlanActions } from "./components/use-task-plan-actions";
import { TaskSearchContext, TaskSearchProvider } from "./task-context";

const TaskListContent: React.FC = () => {
  const { tableControls, totalItemCount, isFetching, fetchError } =
    use(TaskSearchContext);

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
  const [requirementTask, setRequirementTask] = React.useState<TaskDto | null>(
    null,
  );
  const [planTask, setPlanTask] = React.useState<TaskDto | null>(null);
  const [outputTaskId, setOutputTaskId] = React.useState<number | null>(null);

  const {
    setCreatePlanTask,
    setRunAllTask,
    dialogs: planActionDialogs,
  } = useTaskPlanActions();
  const changeRequestMutation = useCreateChangeRequestMutation();

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

          <TaskSortControls
            activeSort={activeSort}
            setActiveSort={setActiveSort}
            sortableColumns={sortableColumns}
            columnNames={columnNames}
          />

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
            <TaskDataListItem
              key={task.id}
              task={task}
              isExpanded={expansionDerivedState.isCellExpanded(task)}
              onToggleExpand={() =>
                expansionDerivedState.setCellExpanded({
                  item: task,
                  isExpanding: !expansionDerivedState.isCellExpanded(task),
                })
              }
              onEditRequirement={() => setRequirementTask(task)}
              onEditPlan={() => setPlanTask(task)}
              onViewOutput={() => setOutputTaskId(task.id)}
              onCreatePlan={() => setCreatePlanTask(task)}
              onRunAll={() => setRunAllTask(task)}
              onChangeRequest={() => changeRequestMutation.mutate(task.id)}
            />
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

      {planActionDialogs}
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
