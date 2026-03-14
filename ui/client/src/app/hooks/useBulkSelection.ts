import type { TdProps } from "@patternfly/react-table";

import type { IToolbarBulkSelectorProps } from "@app/components/ToolbarBulkSelector";
import { useSelectionState } from "./useSelectionState";

export interface BulkSelectionArgs<ItemType> {
  isEqual?: (a: ItemType, b: ItemType) => boolean;
  items?: ItemType[];
  filteredItems?: ItemType[];
  currentPageItems: ItemType[];
  initialSelected?: ItemType[];
}

export interface BulkSelectionValues<ItemType> {
  selectedItems: ItemType[];
  propHelpers: {
    toolbarBulkSelectorProps: IToolbarBulkSelectorProps;
    getSelectCheckboxTdProps: (args: {
      item: ItemType;
      rowIndex: number;
    }) => Omit<TdProps, "ref">;
  };
}

export const useBulkSelection = <T>({
  isEqual = (a, b) => a === b,
  items,
  filteredItems,
  currentPageItems,
  initialSelected,
}: BulkSelectionArgs<T>): BulkSelectionValues<T> => {
  const biggestSetOfItems = items ?? filteredItems ?? currentPageItems;

  const {
    selectedItems,
    areAllSelected,
    isItemSelected,
    selectItems,
    selectOnly,
    selectAll,
  } = useSelectionState({
    items: biggestSetOfItems,
    isEqual,
    initialSelected,
  });

  const toolbarBulkSelectorProps: IToolbarBulkSelectorProps = {
    areAllSelected,
    itemCounts: {
      selected: selectedItems.length,
      page: currentPageItems.length,
      filtered: filteredItems ? filteredItems.length : undefined,
      totalItems: biggestSetOfItems.length,
    },

    onSelectNone: () => selectAll(false),

    onSelectCurrentPage: () => selectOnly(currentPageItems),

    onSelectAllFiltered: filteredItems
      ? () => selectOnly(filteredItems)
      : undefined,

    onSelectAll: items ? () => selectAll(true) : undefined,
  };

  const getSelectCheckboxTdProps: BulkSelectionValues<T>["propHelpers"]["getSelectCheckboxTdProps"] =
    ({ item, rowIndex }) => ({
      select: {
        rowIndex,
        onSelect: (_event, isSelecting) => {
          selectItems([item], isSelecting);
        },
        isSelected: isItemSelected(item),
      },
    });

  return {
    selectedItems,
    propHelpers: {
      toolbarBulkSelectorProps,
      getSelectCheckboxTdProps,
    },
  };
};
