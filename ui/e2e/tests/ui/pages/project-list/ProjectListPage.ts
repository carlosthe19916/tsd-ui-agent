import { expect, type Page } from "@playwright/test";

import { DeletionConfirmDialog } from "../ConfirmDialog";
import { Table } from "../Table";

interface ProjectFormData {
  name: string;
  type: "JIRA" | "GITHUB";
  url: string;
  query?: string;
  credentialName: string;
  gitUrl: string;
  gitBranch?: string;
}

export class ProjectListPage {
  readonly _page: Page;

  private constructor(page: Page) {
    this._page = page;
  }

  static async build(page: Page) {
    await page.goto("/projects");
    await page.waitForLoadState("networkidle");
    return new ProjectListPage(page);
  }

  async getTable() {
    return Table.build(
      this._page,
      "Projects table",
      ["Name", "URL", "Type", "Sync Status", "Git URL", "Git Branch"] as const,
      ["Edit", "Delete"] as const,
    );
  }

  async clickCreateProject() {
    await this._page.getByRole("button", { name: "Create project" }).click();
  }

  async fillProjectForm(data: ProjectFormData) {
    const modal = this._page.locator(".pf-v6-c-modal-box");
    await expect(modal).toBeVisible();

    const nameInput = modal.locator("#name");
    await nameInput.clear();
    await nameInput.fill(data.name);

    await modal.locator("#type").selectOption(data.type);

    await this.fillTypeahead(modal, "apiUrl", data.url);

    if (data.query !== undefined) {
      await this.fillTypeahead(modal, "query", data.query);
    }

    await modal
      .locator("#credentialId")
      .selectOption({ label: data.credentialName });

    const gitUrlInput = modal.locator("#gitUrl");
    await gitUrlInput.clear();
    await gitUrlInput.fill(data.gitUrl);

    if (data.gitBranch !== undefined) {
      const gitBranchInput = modal.locator("#gitBranch");
      await gitBranchInput.clear();
      await gitBranchInput.fill(data.gitBranch);
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

  private async fillTypeahead(
    container: ReturnType<Page["locator"]>,
    fieldId: string,
    value: string,
  ) {
    const input = container.locator(`#${fieldId}-typeahead-input input`);
    const clearButton = container
      .locator(`#${fieldId}-typeahead-select`)
      .locator('button[aria-label="Clear input value"]');

    if (await clearButton.isVisible()) {
      await clearButton.click();
    }

    await input.fill(value);
    await input.press("Tab");
  }

  async applyNameFilter(name: string) {
    const toolbar = this._page.locator(".pf-v6-c-toolbar");
    await toolbar.getByRole("textbox").fill(name);
    await this._page.keyboard.press("Enter");
  }
}
