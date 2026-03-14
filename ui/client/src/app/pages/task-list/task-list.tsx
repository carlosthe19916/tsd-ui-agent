import React from "react";

import {
  Label,
  PageSection,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";

import type { TaskDto, TaskStatus } from "@app/api/models";
import { FilterToolbar } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import {
  ConditionalTableBody,
  TableHeaderContentWithControls,
  TableRowContentWithControls,
} from "@app/components/TableControls";
import { formatDateTime } from "@app/utils/utils";

import { TaskSearchContext, TaskSearchProvider } from "./task-context";

const statusLabel = (status: TaskStatus) => {
  switch (status) {
    case "OPEN":
      return <Label color="green">Open</Label>;
    case "IN_PROGRESS":
      return <Label color="blue">In Progress</Label>;
    case "CLOSED":
      return <Label color="grey">Closed</Label>;
  }
};

const TaskListContent: React.FC = () => {
  const { tableControls, totalItemCount, isFetching } =
    React.useContext(TaskSearchContext);

  const {
    currentPageItems,
    numRenderedColumns,
    propHelpers: {
      toolbarProps,
      paginationToolbarItemProps,
      paginationProps,
      tableProps,
      filterToolbarProps,
      getThProps,
      getTrProps,
      getTdProps,
    },
  } = tableControls;

  return (
    <>
      <Toolbar {...toolbarProps}>
        <ToolbarContent>
          <FilterToolbar {...filterToolbarProps} />
          <ToolbarItem {...paginationToolbarItemProps}>
            <SimplePagination
              idPrefix="tasks-table"
              isTop
              paginationProps={paginationProps}
            />
          </ToolbarItem>
        </ToolbarContent>
      </Toolbar>

      <Table {...tableProps} aria-label="Tasks table">
        <Thead>
          <Tr>
            <TableHeaderContentWithControls {...tableControls}>
              <Th {...getThProps({ columnKey: "projectName" })} />
              <Th {...getThProps({ columnKey: "title" })} />
              <Th {...getThProps({ columnKey: "status" })} />
              <Th {...getThProps({ columnKey: "createdAt" })} />
              <Th {...getThProps({ columnKey: "updatedAt" })} />
            </TableHeaderContentWithControls>
          </Tr>
        </Thead>
        <ConditionalTableBody
          isLoading={isFetching}
          isNoData={totalItemCount === 0}
          numRenderedColumns={numRenderedColumns}
        >
          {currentPageItems?.map((task: TaskDto, rowIndex: number) => (
            <Tbody key={task.id}>
              <Tr {...getTrProps({ item: task })}>
                <TableRowContentWithControls
                  {...tableControls}
                  item={task}
                  rowIndex={rowIndex}
                >
                  <Td width={10} {...getTdProps({ columnKey: "projectName" })}>
                    {task.project.name}
                  </Td>
                  <Td
                    width={20}
                    modifier="breakWord"
                    {...getTdProps({ columnKey: "title" })}
                  >
                    <a
                      href={task.url}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {task.title}
                    </a>
                  </Td>
                  <Td width={10} {...getTdProps({ columnKey: "status" })}>
                    {statusLabel(task.status)}
                  </Td>
                  <Td width={15} {...getTdProps({ columnKey: "createdAt" })}>
                    {formatDateTime(task.createdAt)}
                  </Td>
                  <Td width={15} {...getTdProps({ columnKey: "updatedAt" })}>
                    {formatDateTime(task.updatedAt)}
                  </Td>
                </TableRowContentWithControls>
              </Tr>
            </Tbody>
          ))}
        </ConditionalTableBody>
      </Table>

      <SimplePagination
        idPrefix="tasks-table"
        isTop={false}
        paginationProps={paginationProps}
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
