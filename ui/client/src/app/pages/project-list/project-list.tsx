import React from "react";

import {
  Button,
  ButtonVariant,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  PageSection,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  ActionsColumn,
  ExpandableRowContent,
  Table,
  Tbody,
  Td,
  Th,
  Thead,
  Tr,
} from "@patternfly/react-table";
import spacing from "@patternfly/react-styles/css/utilities/Spacing/spacing";

import {
  DEFAULT_REFETCH_INTERVAL,
  TablePersistenceKeyPrefixes,
} from "@app/Constants";
import type { ProjectDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { FilterToolbar, FilterType } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import {
  ConditionalTableBody,
  TableHeaderContentWithControls,
  TableRowContentWithControls,
} from "@app/components/TableControls";
import { ToolbarBulkSelector } from "@app/components/ToolbarBulkSelector";
import { useBulkSelection } from "@app/hooks/useBulkSelection";
import { useLocalTableControls } from "@app/hooks/table-controls";
import {
  useDeleteProjectMutation,
  useFetchProjects,
  useSyncProjectMutation,
} from "@app/queries/projects";
import { formatDateTime } from "@app/utils/utils";

import { ProjectFormModal } from "./components/project-form-modal";
import { SyncStatus } from "./components/sync-status";

type ModalState =
  | { type: "closed" }
  | { type: "create" }
  | { type: "edit"; project: ProjectDto };

export const ProjectList: React.FC = () => {
  const [modalState, setModalState] = React.useState<ModalState>({
    type: "closed",
  });
  const [projectToDelete, setProjectToDelete] =
    React.useState<ProjectDto | null>(null);
  const [syncTarget, setSyncTarget] = React.useState<ProjectDto[] | null>(null);
  const [isPolling, setIsPolling] = React.useState(false);

  const { data: projects, isFetching } = useFetchProjects(
    isPolling ? DEFAULT_REFETCH_INTERVAL : false,
  );

  React.useEffect(() => {
    const anySyncing = (projects ?? []).some(
      (p) => p.syncStatus === "SYNCHRONIZATION_IN_PROGRESS",
    );
    setIsPolling(anySyncing);
  }, [projects]);

  const deleteMutation = useDeleteProjectMutation(() =>
    setProjectToDelete(null),
  );
  const syncMutation = useSyncProjectMutation();

  const closeModal = () => setModalState({ type: "closed" });

  const tableControls = useLocalTableControls({
    persistenceKeyPrefix: TablePersistenceKeyPrefixes.projects,
    tableName: "projects-table",
    idProperty: "id",
    items: projects ?? [],
    isLoading: isFetching,
    isSelectionEnabled: true,
    columnNames: {
      name: "Name",
      apiUrl: "API URL",
      type: "Type",
      syncStatus: "Sync Status",
      gitUrl: "Git URL",
      gitBranch: "Git Branch",
    },
    hasActionsColumn: true,
    isSortEnabled: true,
    sortableColumns: ["name", "apiUrl", "type"],
    initialSort: {
      columnKey: "name",
      direction: "asc",
    },
    getSortValues: (item) => ({
      name: item.name || "",
      apiUrl: item.apiUrl || "",
      type: item.type || "",
    }),
    isPaginationEnabled: true,
    isFilterEnabled: true,
    filterCategories: [
      {
        categoryKey: "name",
        title: "Name",
        type: FilterType.search,
        placeholderText: "Search by name...",
        getItemValue: (item) => item.name || "",
      },
    ],
    isExpansionEnabled: true,
    expandableVariant: "single",
  });

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
    expansionDerivedState: { isCellExpanded },
  } = tableControls;

  const {
    selectedItems,
    propHelpers: { toolbarBulkSelectorProps, getSelectCheckboxTdProps },
  } = useBulkSelection<ProjectDto>({
    isEqual: (a, b) => a.id === b.id,
    items: projects ?? [],
    filteredItems: tableControls.filteredItems,
    currentPageItems,
  });

  const handleConfirmSync = () => {
    if (!syncTarget) return;
    for (const project of syncTarget) {
      syncMutation.mutate(project.id as number);
    }
    setSyncTarget(null);
  };

  return (
    <>
      <PageSection>
        <Title headingLevel="h1" size="2xl">
          Projects
        </Title>
      </PageSection>
      <PageSection>
        <Toolbar {...toolbarProps}>
          <ToolbarContent>
            <FilterToolbar {...filterToolbarProps} />
            <ToolbarBulkSelector {...toolbarBulkSelectorProps} />
            <ToolbarItem>
              <Button
                variant={ButtonVariant.secondary}
                isDisabled={selectedItems.length === 0}
                onClick={() => setSyncTarget([...selectedItems])}
              >
                Synchronize
              </Button>
            </ToolbarItem>
            <ToolbarItem>
              <Button
                variant={ButtonVariant.primary}
                onClick={() => setModalState({ type: "create" })}
              >
                Create project
              </Button>
            </ToolbarItem>
            <ToolbarItem {...paginationToolbarItemProps}>
              <SimplePagination
                idPrefix="projects-table"
                isTop
                paginationProps={paginationProps}
              />
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>

        <Table {...tableProps} aria-label="Projects table">
          <Thead>
            <Tr>
              <TableHeaderContentWithControls {...tableControls}>
                <Th {...getThProps({ columnKey: "name" })} />
                <Th {...getThProps({ columnKey: "apiUrl" })} />
                <Th {...getThProps({ columnKey: "type" })} />
                <Th {...getThProps({ columnKey: "syncStatus" })} />
                <Th {...getThProps({ columnKey: "gitUrl" })} />
                <Th {...getThProps({ columnKey: "gitBranch" })} />
              </TableHeaderContentWithControls>
            </Tr>
          </Thead>
          <ConditionalTableBody
            isLoading={isFetching}
            isNoData={(projects ?? []).length === 0}
            numRenderedColumns={numRenderedColumns}
          >
            {currentPageItems?.map((project, rowIndex) => (
              <Tbody key={project.id} isExpanded={isCellExpanded(project)}>
                <Tr {...getTrProps({ item: project })}>
                  <TableRowContentWithControls
                    {...tableControls}
                    item={project}
                    rowIndex={rowIndex}
                    getSelectCheckboxTdProps={getSelectCheckboxTdProps}
                  >
                    <Td
                      width={15}
                      modifier="breakWord"
                      {...getTdProps({ columnKey: "name" })}
                    >
                      {project.name}
                    </Td>
                    <Td
                      width={20}
                      modifier="breakWord"
                      {...getTdProps({ columnKey: "apiUrl" })}
                    >
                      {project.apiUrl}
                    </Td>
                    <Td width={10} {...getTdProps({ columnKey: "type" })}>
                      {project.type}
                    </Td>
                    <Td width={10} {...getTdProps({ columnKey: "syncStatus" })}>
                      <SyncStatus status={project.syncStatus} />
                    </Td>
                    <Td
                      width={20}
                      modifier="breakWord"
                      {...getTdProps({ columnKey: "gitUrl" })}
                    >
                      {project.git?.url}
                    </Td>
                    <Td width={10} {...getTdProps({ columnKey: "gitBranch" })}>
                      {project.git?.branch || "default"}
                    </Td>
                    <Td isActionCell>
                      <ActionsColumn
                        items={[
                          {
                            title: "Synchronize",
                            onClick: () => setSyncTarget([project]),
                          },
                          {
                            title: "Edit",
                            onClick: () =>
                              setModalState({ type: "edit", project }),
                          },
                          {
                            title: "Delete",
                            onClick: () => setProjectToDelete(project),
                          },
                        ]}
                      />
                    </Td>
                  </TableRowContentWithControls>
                </Tr>
                {isCellExpanded(project) ? (
                  <Tr isExpanded>
                    <Td colSpan={numRenderedColumns}>
                      <ExpandableRowContent>
                        <div className={spacing.ptLg}>
                          <DescriptionList>
                            <DescriptionListGroup>
                              <DescriptionListTerm>
                                Last Sync
                              </DescriptionListTerm>
                              <DescriptionListDescription>
                                {formatDateTime(project.lastSyncAt) ?? "Never"}
                              </DescriptionListDescription>
                            </DescriptionListGroup>
                          </DescriptionList>
                        </div>
                      </ExpandableRowContent>
                    </Td>
                  </Tr>
                ) : null}
              </Tbody>
            ))}
          </ConditionalTableBody>
        </Table>

        <SimplePagination
          idPrefix="projects-table"
          isTop={false}
          paginationProps={paginationProps}
        />
      </PageSection>

      <ProjectFormModal
        project={modalState.type === "edit" ? modalState.project : null}
        isOpen={modalState.type !== "closed"}
        onClose={closeModal}
      />

      {projectToDelete && (
        <ConfirmDialog
          isOpen
          title="Delete project"
          titleIconVariant="warning"
          message={`Are you sure you want to delete the project "${projectToDelete.name}"?`}
          confirmBtnLabel="Delete"
          cancelBtnLabel="Cancel"
          confirmBtnVariant={ButtonVariant.danger}
          onClose={() => setProjectToDelete(null)}
          onConfirm={() => deleteMutation.mutate(projectToDelete.id as number)}
          onCancel={() => setProjectToDelete(null)}
          inProgress={deleteMutation.isPending}
        />
      )}

      {syncTarget && (
        <ConfirmDialog
          isOpen
          title={
            syncTarget.length === 1
              ? "Synchronize project"
              : "Synchronize projects"
          }
          message={
            syncTarget.length === 1
              ? `Are you sure you want to synchronize "${syncTarget[0].name}"?`
              : `Are you sure you want to synchronize ${syncTarget.length} projects?`
          }
          confirmBtnLabel="Synchronize"
          cancelBtnLabel="Cancel"
          confirmBtnVariant={ButtonVariant.primary}
          onClose={() => setSyncTarget(null)}
          onConfirm={handleConfirmSync}
          onCancel={() => setSyncTarget(null)}
        />
      )}
    </>
  );
};
