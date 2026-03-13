import { type Locator, type Page, expect } from "@playwright/test";

export class Table<
  const TColumns extends readonly string[],
  const TActions extends readonly string[],
> {
  private readonly _page: Page;
  readonly _table: Locator;
  readonly _columns: TColumns;
  private readonly _actions: TActions;

  private constructor(
    page: Page,
    table: Locator,
    columns: TColumns,
    actions: TActions,
  ) {
    this._page = page;
    this._table = table;
    this._columns = columns;
    this._actions = actions;
  }

  static async build<
    const TColumns extends readonly string[],
    const TActions extends readonly string[],
  >(page: Page, tableAriaLabel: string, columns: TColumns, actions: TActions) {
    const table = page.locator(`table[aria-label="${tableAriaLabel}"]`);
    await expect(table).toBeVisible();

    const result = new Table(page, table, columns, actions);
    await result.waitUntilDataIsLoaded();
    return result;
  }

  public async waitUntilDataIsLoaded(waitMs = 500) {
    await this._page.waitForTimeout(waitMs);

    const rows = this._table.locator(
      'xpath=//tbody[not(@aria-label="Table loading")]',
    );
    await expect(rows.first()).toBeVisible();

    await expect.poll(() => rows.count()).toBeGreaterThanOrEqual(1);
  }

  async clickSortBy(columnName: TColumns[number]) {
    await this._table
      .getByRole("button", { name: columnName, exact: true })
      .click();
    await this.waitUntilDataIsLoaded();
  }

  async clickAction(actionName: TActions[number], rowIndex: number) {
    await this._table
      .locator(`button[aria-label="Kebab toggle"]`)
      .nth(rowIndex)
      .click();

    await this._page.getByRole("menuitem", { name: actionName }).click();
  }

  async getRows() {
    const rows = this._table.locator("tbody");
    await expect(rows.first()).toBeVisible();
    return rows;
  }

  async getColumn(columnName: TColumns[number]) {
    const column = this._table.locator(`td[data-label="${columnName}"]`);
    await expect(column.first()).toBeVisible();
    return column;
  }

  async getRowsByCellValue(
    cellValues: Partial<Record<TColumns[number], string>>,
  ): Promise<Locator> {
    let rowLocator = this._table.locator("tbody tr");

    for (const columnName of Object.keys(cellValues) as Array<
      TColumns[number]
    >) {
      const value = cellValues[columnName];
      rowLocator = rowLocator.filter({
        has: this._page.locator(`td[data-label="${columnName}"]`, {
          hasText: value,
        }),
      });
    }

    await expect(rowLocator.first()).toBeVisible();
    return rowLocator;
  }
}
