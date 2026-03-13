import type React from "react";
import type { AutocompleteOptionProps } from "./type-utils";

export interface AutocompleteProps {
  isDisabled?: boolean;
  options: AutocompleteOptionProps[];
  selections?: AutocompleteOptionProps[];
  onChange: (selections: AutocompleteOptionProps[]) => void;
  noResultsMessage?: string;
  placeholderText?: string;
  searchInputAriaLabel?: string;
  onSearchChange?: (value: string) => void;
}

export const Autocomplete: React.FC<AutocompleteProps> = () => {
  return null;
};
