import React from "react";

import {
  Button,
  MenuToggle,
  Select,
  SelectList,
  SelectOption,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import SortAmountDownIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-down-icon";
import SortAmountUpIcon from "@patternfly/react-icons/dist/esm/icons/sort-amount-up-icon";

interface TaskSortControlsProps {
  activeSort: { columnKey: string; direction: string } | undefined;
  setActiveSort: (sort: { columnKey: string; direction: string }) => void;
  sortableColumns: string[];
  columnNames: Record<string, string>;
}

export const TaskSortControls: React.FC<TaskSortControlsProps> = ({
  activeSort,
  setActiveSort,
  sortableColumns,
  columnNames,
}) => {
  const [isSortByOpen, setIsSortByOpen] = React.useState(false);

  return (
    <ToolbarGroup>
      <ToolbarItem>
        <Button
          variant="control"
          onClick={() =>
            setActiveSort({
              columnKey: activeSort?.columnKey ?? "createdAt",
              direction: activeSort?.direction === "asc" ? "desc" : "asc",
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
              columnKey: value as string,
              direction: activeSort?.direction ?? "desc",
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
              {activeSort ? columnNames[activeSort.columnKey] : "Sort by"}
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
  );
};
