import type React from "react";
import type { AsyncMultiSelectOptionProps } from "./type-utils";

export interface AsyncMultiSelectProps {
  showBadgeCount?: boolean;
  isDisabled?: boolean;
  options: AsyncMultiSelectOptionProps[];
  selections?: AsyncMultiSelectOptionProps[];
  onChange: (selections: AsyncMultiSelectOptionProps[]) => void;
  noResultsMessage?: string;
  placeholderText?: string;
  searchInputAriaLabel?: string;
  onSearchChange?: (value: string) => void;
}

export const AsyncMultiSelect: React.FC<AsyncMultiSelectProps> = () => {
  return null;
};
