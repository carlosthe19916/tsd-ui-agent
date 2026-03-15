import React from "react";

import {
  Button,
  ButtonVariant,
  PageSection,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  ActionsColumn,
  Table,
  Tbody,
  Td,
  Th,
  Thead,
  Tr,
} from "@patternfly/react-table";

import { TablePersistenceKeyPrefixes } from "@app/Constants";
import type { GitDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { FilterToolbar, FilterType } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
import {
  ConditionalTableBody,
  TableHeaderContentWithControls,
  TableRowContentWithControls,
} from "@app/components/TableControls";
import { useLocalTableControls } from "@app/hooks/table-controls";
import { useDeleteGitMutation, useFetchGits } from "@app/queries/gits";

import { GitFormModal } from "./components/git-form-modal";

type ModalState =
  | { type: "closed" }
  | { type: "create" }
  | { type: "edit"; git: GitDto };

export const GitList: React.FC = () => {
  const [modalState, setModalState] = React.useState<ModalState>({
    type: "closed",
  });
  const [gitToDelete, setGitToDelete] = React.useState<GitDto | null>(null);

  const { data: gits, isFetching } = useFetchGits();
  const deleteMutation = useDeleteGitMutation(() => setGitToDelete(null));

  const closeModal = () => setModalState({ type: "closed" });

  const tableControls = useLocalTableControls({
    persistenceKeyPrefix: TablePersistenceKeyPrefixes.gits,
    tableName: "gits-table",
    idProperty: "id",
    items: gits ?? [],
    isLoading: isFetching,
    columnNames: {
      url: "URL",
      branch: "Branch",
      forkUrl: "Fork URL",
    },
    hasActionsColumn: true,
    isSortEnabled: true,
    sortableColumns: ["url", "branch"],
    initialSort: {
      columnKey: "url",
      direction: "asc",
    },
    getSortValues: (item) => ({
      url: item.url,
      branch: item.branch ?? "",
    }),
    isPaginationEnabled: true,
    isFilterEnabled: true,
    filterCategories: [
      {
        categoryKey: "url",
        title: "URL",
        type: FilterType.search,
        placeholderText: "Search by URL...",
        getItemValue: (item) => item.url || "",
      },
    ],
    isExpansionEnabled: false,
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
  } = tableControls;

  return (
    <>
      <PageSection>
        <Title headingLevel="h1" size="2xl">
          Git Repositories
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
                Create git repository
              </Button>
            </ToolbarItem>
            <ToolbarItem {...paginationToolbarItemProps}>
              <SimplePagination
                idPrefix="gits-table"
                isTop
                paginationProps={paginationProps}
              />
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>

        <Table {...tableProps} aria-label="Git repositories table">
          <Thead>
            <Tr>
              <TableHeaderContentWithControls {...tableControls}>
                <Th {...getThProps({ columnKey: "url" })} />
                <Th {...getThProps({ columnKey: "branch" })} />
                <Th {...getThProps({ columnKey: "forkUrl" })} />
              </TableHeaderContentWithControls>
            </Tr>
          </Thead>
          <ConditionalTableBody
            isLoading={isFetching}
            isNoData={(gits ?? []).length === 0}
            numRenderedColumns={numRenderedColumns}
          >
            {currentPageItems?.map((git, rowIndex) => (
              <Tbody key={git.id}>
                <Tr {...getTrProps({ item: git })}>
                  <TableRowContentWithControls
                    {...tableControls}
                    item={git}
                    rowIndex={rowIndex}
                  >
                    <Td
                      width={40}
                      modifier="breakWord"
                      {...getTdProps({ columnKey: "url" })}
                    >
                      {git.url}
                    </Td>
                    <Td width={30} {...getTdProps({ columnKey: "branch" })}>
                      {git.branch || "Default"}
                    </Td>
                    <Td width={30} {...getTdProps({ columnKey: "forkUrl" })}>
                      {git.forkUrl || "None"}
                    </Td>
                    <Td isActionCell>
                      <ActionsColumn
                        items={[
                          {
                            title: "Edit",
                            onClick: () => setModalState({ type: "edit", git }),
                          },
                          {
                            title: "Delete",
                            onClick: () => setGitToDelete(git),
                          },
                        ]}
                      />
                    </Td>
                  </TableRowContentWithControls>
                </Tr>
              </Tbody>
            ))}
          </ConditionalTableBody>
        </Table>

        <SimplePagination
          idPrefix="gits-table"
          isTop={false}
          paginationProps={paginationProps}
        />
      </PageSection>

      <GitFormModal
        git={modalState.type === "edit" ? modalState.git : null}
        isOpen={modalState.type !== "closed"}
        onClose={closeModal}
      />

      {gitToDelete && (
        <ConfirmDialog
          isOpen
          title="Delete git repository"
          titleIconVariant="warning"
          message={`Are you sure you want to delete the git repository "${gitToDelete.url}"? This will also remove the local clone.`}
          confirmBtnLabel="Delete"
          cancelBtnLabel="Cancel"
          confirmBtnVariant={ButtonVariant.danger}
          onClose={() => setGitToDelete(null)}
          onConfirm={() => deleteMutation.mutate(gitToDelete.id as number)}
          onCancel={() => setGitToDelete(null)}
          inProgress={deleteMutation.isPending}
        />
      )}
    </>
  );
};
