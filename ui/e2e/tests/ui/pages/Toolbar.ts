import { type Locator, type Page, expect } from "@playwright/test";

import {
  type FilterValueType,
  isDateRangeFilter,
  isMultiSelectFilter,
  isStringFilter,
  isTypeaheadFilter,
  type TDateRange,
  type TFilterValue,
} from "./utils";

export class Toolbar<
  TFilter extends Record<string, TFilterValue>,
  TFilterName extends Extract<keyof TFilter, string>,
  const TKebabActions extends readonly string[],
> {
  private readonly _page: Page;
  _toolbar: Locator;
  readonly _filters: TFilter;

  private readonly _kebabActionButton: Locator | null;
  _kebabActions: TKebabActions | null;

  private constructor(
    page: Page,
    toolbar: Locator,
    filters: TFilter,
    kebabActions: TKebabActions,
    kebabActionButton: Locator | null,
  ) {
    this._page = page;
    this._toolbar = toolbar;
    this._filters = filters;
    this._kebabActions = kebabActions;
    this._kebabActionButton = kebabActionButton;
  }

  static async build<
    TFilter extends Record<string, TFilterValue>,
    const TKebabActions extends readonly string[] = [],
  >(
    page: Page,
    toolbarAriaLabel: string,
    filters: TFilter = {} as TFilter,
    kebabActions?: {
      buttonAriaLabel: string;
      actions: TKebabActions;
    },
  ) {
    const toolbar = page.locator(`[aria-label="${toolbarAriaLabel}"]`);
    await expect(toolbar).toBeVisible();

    let kebabActionButton: Locator | null = null;
    if (kebabActions?.buttonAriaLabel) {
      kebabActionButton = page.getByRole("button", {
        name: kebabActions.buttonAriaLabel,
      });
      await expect(kebabActionButton).toBeVisible();
    }

    return new Toolbar(
      page,
      toolbar,
      filters,
      kebabActions?.actions ?? [],
      kebabActionButton,
    );
  }

  async applyFilter(filters: Partial<FilterValueType<TFilter>>) {
    for (const filterName of Object.keys(filters) as Array<TFilterName>) {
      const filterValue = filters[filterName];
      if (!filterValue) continue;

      const filterType = this._filters[filterName];

      await this.selectFilter(filterName);
      if (isStringFilter(filterType, filterValue)) {
        await this.applyTextFilter(filterName, filterValue);
      }
      if (isDateRangeFilter(filterType, filterValue)) {
        await this.applyDateRangeFilter(filterName, filterValue);
      }
      if (isMultiSelectFilter(filterType, filterValue)) {
        await this.applyMultiSelectFilter(filterName, filterValue);
      }
      if (isTypeaheadFilter(filterType, filterValue)) {
        await this.applyTypeaheadFilter(filterName, filterValue);
      }
    }
  }

  private async applyTextFilter(_filterName: TFilterName, filterValue: string) {
    await this._toolbar.getByRole("textbox").fill(filterValue);
    await this._page.keyboard.press("Enter");
  }

  private async applyDateRangeFilter(
    _filterName: TFilterName,
    dateRange: TDateRange,
  ) {
    await this._toolbar
      .locator("input[aria-label='Interval start']")
      .fill(dateRange.from);
    await this._toolbar
      .locator("input[aria-label='Interval end']")
      .fill(dateRange.to);
  }

  private async applyMultiSelectFilter(
    _filterName: TFilterName,
    selections: string[],
  ) {
    for (const option of selections) {
      const inputText = this._toolbar.locator(
        "input[aria-label='Type to filter']",
      );
      await inputText.clear();
      await inputText.fill(option);

      const dropdownOption = this._page.getByRole("menuitem", {
        name: option,
        exact: true,
      });
      await expect(dropdownOption).toBeVisible();
      await dropdownOption.click();
    }
  }

  private async applyTypeaheadFilter(
    _filterName: TFilterName,
    labels: string[],
  ) {
    for (const label of labels) {
      await this._toolbar
        .locator("input[aria-label='select-autocomplete-listbox']")
        .fill(label);

      const dropdownOption = this._page.getByRole("menuitem", {
        name: label,
        exact: true,
      });
      await expect(dropdownOption).toBeVisible();
      await dropdownOption.click();
    }
  }

  private async selectFilter(filterName: TFilterName) {
    await this._toolbar
      .locator(".pf-m-toggle-group button.pf-v6-c-menu-toggle")
      .click();
    await this._page.getByRole("menuitem", { name: filterName }).click();
  }

  async clearAllFilters() {
    const clearButton = this._toolbar.getByRole("button", {
      name: "Clear all filters",
    });
    await expect(clearButton).toBeVisible();
    await clearButton.click();

    await expect(this._toolbar.locator(".pf-m-label-group")).toHaveCount(0);
  }

  async clickKebabAction(actionName: TKebabActions[number]) {
    if (!this._kebabActionButton) {
      throw new Error("No Kebab action button defined");
    }

    await this._kebabActionButton.click();
    await this._page.getByRole("menuitem", { name: actionName }).click();
  }
}
