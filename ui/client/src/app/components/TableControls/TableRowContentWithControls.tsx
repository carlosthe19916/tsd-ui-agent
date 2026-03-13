import type React from "react";

import type { ITableControls } from "@app/hooks/table-controls";
import { Td } from "@patternfly/react-table";
import type { TdProps } from "@patternfly/react-table";

export interface ITableRowContentWithControlsProps<
  TItem,
  TColumnKey extends string,
  TSortableColumnKey extends TColumnKey,
  TFilterCategoryKey extends string = string,
  TPersistenceKeyPrefix extends string = string,
> {
  isExpansionEnabled?: boolean;
  expandableVariant?: "single" | "compound";
  getSelectCheckboxTdProps?: (args: {
    item: TItem;
    rowIndex: number;
  }) => Omit<TdProps, "ref">;
  propHelpers: ITableControls<
    TItem,
    TColumnKey,
    TSortableColumnKey,
    TFilterCategoryKey,
    TPersistenceKeyPrefix
  >["propHelpers"];
  item: TItem;
  rowIndex: number;
  children: React.ReactNode;
}

export const TableRowContentWithControls = <
  TItem,
  TColumnKey extends string,
  TSortableColumnKey extends TColumnKey,
>({
  isExpansionEnabled = false,
  expandableVariant,
  getSelectCheckboxTdProps,
  propHelpers: { getSingleExpandButtonTdProps },
  item,
  rowIndex,
  children,
}: React.PropsWithChildren<
  ITableRowContentWithControlsProps<TItem, TColumnKey, TSortableColumnKey>
>) => (
  <>
    {isExpansionEnabled && expandableVariant === "single" ? (
      <Td {...getSingleExpandButtonTdProps({ item, rowIndex })} />
    ) : null}
    {getSelectCheckboxTdProps ? (
      <Td {...getSelectCheckboxTdProps({ item, rowIndex })} />
    ) : null}
    {children}
  </>
);
