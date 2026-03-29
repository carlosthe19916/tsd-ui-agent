import { expect, type Page } from "@playwright/test";

import { DeletionConfirmDialog } from "../ConfirmDialog";

export class GitListPage {
  private readonly _page: Page;

  private constructor(page: Page) {
    this._page = page;
  }

  static async build(page: Page) {
    await page.goto("/gits");
    await page.waitForLoadState("networkidle");
    return new GitListPage(page);
  }

  async getDataList() {
    const dataList = this._page.locator(
      `ul[aria-label="Git repositories list"]`,
    );
    await expect(dataList).toBeVisible();
    return dataList;
  }

  async clickCreateGit() {
    await this._page
      .getByRole("button", { name: "Create git repository" })
      .click();
  }

  async fillGitForm(url: string, branch?: string) {
    const modal = this._page.locator(
      `.pf-v6-c-modal-box[aria-label="Create git repository"], .pf-v6-c-modal-box[aria-label="Edit git repository"]`,
    );
    await expect(modal).toBeVisible();

    const urlInput = modal.locator("#url");
    await urlInput.clear();
    await urlInput.fill(url);

    if (branch !== undefined) {
      const branchInput = modal.locator("#branch");
      await branchInput.clear();
      await branchInput.fill(branch);
    }
  }

  async submitCreate() {
    const modal = this._page.locator(".pf-v6-c-modal-box");
    await modal.getByRole("button", { name: "Create" }).click();
    await expect(modal).not.toBeVisible();
  }

  async submitEdit() {
    const modal = this._page.locator(".pf-v6-c-modal-box");
    await modal.getByRole("button", { name: "Save" }).click();
    await expect(modal).not.toBeVisible();
  }

  async cancelModal() {
    await this._page
      .locator(".pf-v6-c-modal-box")
      .getByRole("button", { name: "Cancel" })
      .click();
  }

  async clickRowAction(url: string, action: "Edit" | "Delete") {
    const dataList = await this.getDataList();
    const item = dataList
      .locator(".pf-v6-c-data-list__item")
      .filter({ hasText: url })
      .first();
    await item.locator(`button[aria-label="Kebab toggle"]`).click();
    await this._page.getByRole("menuitem", { name: action }).click();
  }

  async getConfirmDialog() {
    return DeletionConfirmDialog.build(this._page, "Confirm dialog");
  }

  async applyUrlFilter(url: string) {
    const toolbar = this._page.locator(".pf-v6-c-toolbar");
    await toolbar.getByRole("textbox").fill(url);
    await this._page.keyboard.press("Enter");
  }

  async hasItemWithUrl(url: string): Promise<boolean> {
    const dataList = this._page.locator(
      `ul[aria-label="Git repositories list"]`,
    );
    const item = dataList
      .locator(".pf-v6-c-data-list__item")
      .filter({ hasText: url });
    return (await item.count()) > 0;
  }
}
