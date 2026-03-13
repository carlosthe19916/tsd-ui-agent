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
import type { CredentialDto } from "@app/api/models";
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
  useDeleteCredentialMutation,
  useFetchCredentials,
} from "@app/queries/credentials";

import { CredentialFormModal } from "./components/credential-form-modal";

type ModalState =
  | { type: "closed" }
  | { type: "create" }
  | { type: "edit"; credential: CredentialDto };

export const CredentialList: React.FC = () => {
  const [modalState, setModalState] = React.useState<ModalState>({
    type: "closed",
  });
  const [credentialToDelete, setCredentialToDelete] =
    React.useState<CredentialDto | null>(null);

  const { data: credentials, isFetching } = useFetchCredentials();
  const deleteMutation = useDeleteCredentialMutation(() =>
    setCredentialToDelete(null),
  );

  const closeModal = () => setModalState({ type: "closed" });

  const tableControls = useLocalTableControls({
    persistenceKeyPrefix: TablePersistenceKeyPrefixes.credentials,
    tableName: "credentials-table",
    idProperty: "id",
    items: credentials ?? [],
    isLoading: isFetching,
    columnNames: {
      name: "Name",
    },
    hasActionsColumn: true,
    isSortEnabled: true,
    sortableColumns: ["name"],
    initialSort: {
      columnKey: "name",
      direction: "asc",
    },
    getSortValues: (item) => ({
      name: item.name,
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
    expansionDerivedState: { isCellExpanded },
  } = tableControls;

  return (
    <>
      <PageSection>
        <Title headingLevel="h1" size="2xl">
          Credentials
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
                Create credential
              </Button>
            </ToolbarItem>
            <ToolbarItem {...paginationToolbarItemProps}>
              <SimplePagination
                idPrefix="credentials-table"
                isTop
                paginationProps={paginationProps}
              />
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>

        <Table {...tableProps} aria-label="Credentials table">
          <Thead>
            <Tr>
              <TableHeaderContentWithControls {...tableControls}>
                <Th {...getThProps({ columnKey: "name" })} />
              </TableHeaderContentWithControls>
            </Tr>
          </Thead>
          <ConditionalTableBody
            isLoading={isFetching}
            isNoData={(credentials ?? []).length === 0}
            numRenderedColumns={numRenderedColumns}
          >
            {currentPageItems?.map((credential, rowIndex) => (
              <Tbody
                key={credential.id}
                isExpanded={isCellExpanded(credential)}
              >
                <Tr {...getTrProps({ item: credential })}>
                  <TableRowContentWithControls
                    {...tableControls}
                    item={credential}
                    rowIndex={rowIndex}
                  >
                    <Td
                      width={100}
                      modifier="breakWord"
                      {...getTdProps({ columnKey: "name" })}
                    >
                      {credential.name}
                    </Td>
                    <Td isActionCell>
                      <ActionsColumn
                        items={[
                          {
                            title: "Edit",
                            onClick: () =>
                              setModalState({ type: "edit", credential }),
                          },
                          {
                            title: "Delete",
                            onClick: () => setCredentialToDelete(credential),
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
          idPrefix="credentials-table"
          isTop={false}
          paginationProps={paginationProps}
        />
      </PageSection>

      <CredentialFormModal
        credential={modalState.type === "edit" ? modalState.credential : null}
        isOpen={modalState.type !== "closed"}
        onClose={closeModal}
      />

      {credentialToDelete && (
        <ConfirmDialog
          isOpen
          title="Delete credential"
          titleIconVariant="warning"
          message={`Are you sure you want to delete the credential "${credentialToDelete.name}"?`}
          confirmBtnLabel="Delete"
          cancelBtnLabel="Cancel"
          confirmBtnVariant={ButtonVariant.danger}
          onClose={() => setCredentialToDelete(null)}
          onConfirm={() =>
            deleteMutation.mutate(credentialToDelete.id as number)
          }
          onCancel={() => setCredentialToDelete(null)}
          inProgress={deleteMutation.isPending}
        />
      )}
    </>
  );
};
