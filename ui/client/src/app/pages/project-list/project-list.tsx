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

import { TablePersistenceKeyPrefixes } from "@app/Constants";
import type { ProjectDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { FilterToolbar, FilterType } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import {
  ConditionalTableBody,
  TableHeaderContentWithControls,
  TableRowContentWithControls,
} from "@app/components/TableControls";
import { useLocalTableControls } from "@app/hooks/table-controls";
import {
  useDeleteProjectMutation,
  useFetchProjects,
} from "@app/queries/projects";
import { formatDateTime } from "@app/utils/utils";

import { ProjectFormModal } from "./components/project-form-modal";

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

  const { data: projects, isFetching } = useFetchProjects();
  const deleteMutation = useDeleteProjectMutation(() =>
    setProjectToDelete(null),
  );

  const closeModal = () => setModalState({ type: "closed" });

  const tableControls = useLocalTableControls({
    persistenceKeyPrefix: TablePersistenceKeyPrefixes.projects,
    tableName: "projects-table",
    idProperty: "id",
    items: projects ?? [],
    isLoading: isFetching,
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
                      {project.syncStatus ?? "N/A"}
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
    </>
  );
};
