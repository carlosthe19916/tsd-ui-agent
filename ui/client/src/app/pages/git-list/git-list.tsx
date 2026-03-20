import React from "react";

import {
  Button,
  ButtonVariant,
  DataList,
  DataListAction,
  DataListCell,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  Dropdown,
  DropdownItem,
  DropdownList,
  Flex,
  FlexItem,
  Icon,
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
import CodeBranchIcon from "@patternfly/react-icons/dist/esm/icons/code-branch-icon";

import { TablePersistenceKeyPrefixes } from "@app/Constants";
import type { GitDto } from "@app/api/models";
import { ConfirmDialog } from "@app/components/ConfirmDialog";
import { ConditionalDataListBody } from "@app/components/DataListControls";
import { FilterToolbar, FilterType } from "@app/components/FilterToolbar";
import { SimplePagination } from "@app/components/SimplePagination";
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
  const [isSortByOpen, setIsSortByOpen] = React.useState(false);
  const [openKebabId, setOpenKebabId] = React.useState<number | null>(null);

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

            <ToolbarGroup>
              <ToolbarItem>
                <Button
                  variant="control"
                  onClick={() =>
                    setActiveSort({
                      columnKey: activeSort?.columnKey ?? "url",
                      direction:
                        activeSort?.direction === "asc" ? "desc" : "asc",
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
                      direction: activeSort?.direction ?? "asc",
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
                      {activeSort
                        ? columnNames[activeSort.columnKey]
                        : "Sort by"}
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

        <ConditionalDataListBody
          isLoading={isFetching}
          isNoData={(gits ?? []).length === 0}
        >
          <DataList aria-label="Git repositories list">
            {currentPageItems?.map((git) => (
              <DataListItem key={git.id} aria-labelledby={`git-${git.id}`}>
                <DataListItemRow>
                  <DataListItemCells
                    dataListCells={[
                      <DataListCell key="url" width={3}>
                        <Flex
                          direction={{ default: "column" }}
                          gap={{ default: "gapXs" }}
                        >
                          <FlexItem>{git.url}</FlexItem>
                          <FlexItem>{git.branch || "Default"}</FlexItem>
                          <FlexItem>
                            <small>{git.vendorType || "Unknown"}</small>
                          </FlexItem>
                          <FlexItem>
                            <Icon>
                              <CodeBranchIcon />
                            </Icon>
                            <small>{git.forkUrl || "None"}</small>
                          </FlexItem>
                        </Flex>
                      </DataListCell>,
                    ]}
                  />
                  <DataListAction
                    id={`git-action-${git.id}`}
                    aria-label="Actions"
                    aria-labelledby={`git-${git.id} git-action-${git.id}`}
                  >
                    <Dropdown
                      isOpen={openKebabId === git.id}
                      onSelect={() => setOpenKebabId(null)}
                      onOpenChange={(isOpen) =>
                        setOpenKebabId(isOpen ? git.id : null)
                      }
                      toggle={(toggleRef) => (
                        <MenuToggle
                          ref={toggleRef}
                          aria-label="Kebab toggle"
                          variant="plain"
                          onClick={() =>
                            setOpenKebabId(
                              openKebabId === git.id ? null : git.id,
                            )
                          }
                          isExpanded={openKebabId === git.id}
                        >
                          <EllipsisVIcon />
                        </MenuToggle>
                      )}
                      popperProps={{ position: "right" }}
                    >
                      <DropdownList>
                        <DropdownItem
                          key="edit"
                          onClick={() => setModalState({ type: "edit", git })}
                        >
                          Edit
                        </DropdownItem>
                        <DropdownItem
                          key="delete"
                          onClick={() => setGitToDelete(git)}
                        >
                          Delete
                        </DropdownItem>
                      </DropdownList>
                    </Dropdown>
                  </DataListAction>
                </DataListItemRow>
              </DataListItem>
            ))}
          </DataList>
        </ConditionalDataListBody>

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
