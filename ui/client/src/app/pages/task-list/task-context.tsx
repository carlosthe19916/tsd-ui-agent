import React from "react";

import type { AxiosError } from "axios";

import {
  FILTER_TEXT_CATEGORY_KEY,
  TablePersistenceKeyPrefixes,
} from "@app/Constants";
import type { TaskDto } from "@app/api/models";
import { FilterType } from "@app/components/FilterToolbar";
import {
  type ITableControls,
  getHubRequestParams,
  useTableControlProps,
  useTableControlState,
} from "@app/hooks/table-controls";
import { useFetchProjects } from "@app/queries/projects";
import { useFetchTasks } from "@app/queries/tasks";

interface ITaskSearchContext {
  tableControls: ITableControls<
    TaskDto,
    "projectName" | "title" | "status" | "createdAt" | "updatedAt",
    "title" | "createdAt" | "updatedAt",
    "" | "status" | "projectId",
    string
  >;
  totalItemCount: number;
  isFetching: boolean;
  fetchError: AxiosError | null;
}

const contextDefaultValue = {} as ITaskSearchContext;

export const TaskSearchContext =
  React.createContext<ITaskSearchContext>(contextDefaultValue);

interface ITaskProvider {
  children: React.ReactNode;
}

export const TaskSearchProvider: React.FunctionComponent<ITaskProvider> = ({
  children,
}) => {
  const { data: projects } = useFetchProjects();

  const tableControlState = useTableControlState({
    tableName: "tasks",
    persistenceKeyPrefix: TablePersistenceKeyPrefixes.tasks,
    persistTo: "urlParams",
    columnNames: {
      projectName: "Project",
      title: "Title",
      status: "Status",
      createdAt: "Created",
      updatedAt: "Updated",
    },
    isPaginationEnabled: true,
    isSortEnabled: true,
    sortableColumns: ["title", "createdAt", "updatedAt"],
    initialSort: {
      columnKey: "createdAt",
      direction: "desc",
    },
    isFilterEnabled: true,
    filterCategories: [
      {
        categoryKey: FILTER_TEXT_CATEGORY_KEY,
        title: "Filter text",
        placeholderText: "Search...",
        type: FilterType.search,
      },
      {
        categoryKey: "status",
        title: "Status",
        type: FilterType.select,
        placeholderText: "Status",
        selectOptions: [
          { value: "OPEN", label: "Open" },
          { value: "IN_PROGRESS", label: "In Progress" },
          { value: "CLOSED", label: "Closed" },
        ],
      },
      {
        categoryKey: "projectId",
        title: "Project",
        type: FilterType.multiselect,
        placeholderText: "Project",
        selectOptions: (projects ?? []).map((p) => ({
          value: String(p.id),
          label: p.name,
        })),
      },
    ],
    isExpansionEnabled: true,
    expandableVariant: "single",
  });

  const hubRequestParams = getHubRequestParams({
    ...tableControlState,
    hubSortFieldKeys: {
      title: "title",
      createdAt: "createdAt",
      updatedAt: "updatedAt",
    },
  });

  const {
    data: tasksResponse,
    isLoading,
    error: fetchError,
  } = useFetchTasks(hubRequestParams);

  const totalItemCount = tasksResponse?.meta.count ?? 0;

  const tableControls = useTableControlProps({
    ...tableControlState,
    idProperty: "id",
    currentPageItems: tasksResponse?.data ?? [],
    totalItemCount,
    isLoading: isLoading,
  });

  return (
    <TaskSearchContext.Provider
      value={{
        totalItemCount,
        isFetching: isLoading,
        fetchError: fetchError as AxiosError | null,
        tableControls,
      }}
    >
      {children}
    </TaskSearchContext.Provider>
  );
};
