import { expect, type Page } from "@playwright/test";

import { DeletionConfirmDialog } from "../ConfirmDialog";
import { Table } from "../Table";

export class CredentialListPage {
  private readonly _page: Page;

  private constructor(page: Page) {
    this._page = page;
  }

  static async build(page: Page) {
    await page.goto("/credentials");
    await page.waitForLoadState("networkidle");
    return new CredentialListPage(page);
  }

  async getTable() {
    return Table.build(
      this._page,
      "Credentials table",
      ["Name"] as const,
      ["Edit", "Delete"] as const,
    );
  }

  async clickCreateCredential() {
    await this._page.getByRole("button", { name: "Create credential" }).click();
  }

  async fillCredentialForm(name: string, token?: string) {
    const modal = this._page.locator(".pf-v6-c-modal-box");
    await expect(modal).toBeVisible();

    const nameInput = modal.locator("#name");
    await nameInput.clear();
    await nameInput.fill(name);

    if (token !== undefined) {
      const tokenInput = modal.locator("#token");
      await tokenInput.clear();
      await tokenInput.fill(token);
    }
  }

  async toggleUpdateToken() {
    const modal = this._page.locator(".pf-v6-c-modal-box");
    await modal.getByLabel("Update token").click();
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

  async isSubmitDisabled(): Promise<boolean> {
    const modal = this._page.locator(".pf-v6-c-modal-box");
    const createBtn = modal.getByRole("button", { name: /Create|Save/ });
    return createBtn.isDisabled();
  }

  async getConfirmDialog() {
    return DeletionConfirmDialog.build(this._page, "Confirm dialog");
  }

  async clickRowAction(name: string, action: "Edit" | "Delete") {
    const row = this._page
      .locator("tbody")
      .filter({
        has: this._page.locator(`td[data-label="Name"]`, { hasText: name }),
      })
      .first();
    await row.locator(`button[aria-label="Kebab toggle"]`).click();
    await this._page.getByRole("menuitem", { name: action }).click();
  }

  async applyNameFilter(name: string) {
    const toolbar = this._page.locator(".pf-v6-c-toolbar");
    await toolbar.getByRole("textbox").fill(name);
    await this._page.keyboard.press("Enter");
  }
}
