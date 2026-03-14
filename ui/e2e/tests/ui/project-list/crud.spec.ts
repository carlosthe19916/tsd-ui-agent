import { expect, test } from "@playwright/test";

import { ProjectListPage } from "../pages/project-list/ProjectListPage";

const API_BASE = "/api";

let projectPage: ProjectListPage;

test.describe
  .skip("Project CRUD", () => {
    const uniqueId = Date.now().toString(36);
    let credentialName: string;

    test.beforeAll(async ({ request }) => {
      credentialName = `cred-for-projects-${uniqueId}`;
      await request.post(`${API_BASE}/credentials`, {
        data: { name: credentialName, token: "test-token" },
      });
    });

    test.beforeEach(async ({ page }) => {
      projectPage = await ProjectListPage.build(page);
    });

    test("1. Create project", async () => {
      const projectName = `create-${uniqueId}`;
      await projectPage.clickCreateProject();
      await projectPage.fillProjectForm({
        name: projectName,
        type: "JIRA",
        url: "https://jira.example.com",
        query: "project=TEST",
        credentialName,
        gitUrl: "https://github.com/example/repo.git",
        gitBranch: "main",
      });
      await projectPage.submitCreate();

      const table = await projectPage.getTable();
      const column = await table.getColumn("Name");
      await expect(column.getByText(projectName)).toBeVisible();
    });

    test("2. Edit project", async () => {
      const projectName = `edit-${uniqueId}`;
      await projectPage.clickCreateProject();
      await projectPage.fillProjectForm({
        name: projectName,
        type: "GITHUB",
        url: "https://github.com/example",
        credentialName,
        gitUrl: "https://github.com/example/repo.git",
      });
      await projectPage.submitCreate();

      await projectPage.clickRowAction(projectName, "Edit");

      const updatedName = `${projectName}-updated`;
      const modal = projectPage._page.locator(".pf-v6-c-modal-box");
      await expect(modal).toBeVisible();
      const nameInput = modal.locator("#name");
      await nameInput.clear();
      await nameInput.fill(updatedName);

      await projectPage.submitEdit();

      const table = await projectPage.getTable();
      const column = await table.getColumn("Name");
      await expect(column.getByText(updatedName)).toBeVisible();
    });

    test("3. Delete project", async () => {
      const projectName = `delete-${uniqueId}`;
      await projectPage.clickCreateProject();
      await projectPage.fillProjectForm({
        name: projectName,
        type: "JIRA",
        url: "https://jira.example.com",
        query: "project=TEST",
        credentialName,
        gitUrl: "https://github.com/example/repo.git",
      });
      await projectPage.submitCreate();

      await projectPage.clickRowAction(projectName, "Delete");

      const dialog = await projectPage.getConfirmDialog();
      await dialog.clickConfirm();

      await projectPage._page.waitForTimeout(500);
      const nameCell = projectPage._page.locator(`td[data-label="Name"]`, {
        hasText: projectName,
      });
      await expect(nameCell).not.toBeVisible();
    });

    test("4. Validation - empty form has disabled submit", async ({ page }) => {
      await projectPage.clickCreateProject();

      const modal = page.locator(".pf-v6-c-modal-box");
      const createBtn = modal.getByRole("button", { name: "Create" });
      await expect(createBtn).toBeDisabled();
    });

    test("5. Filter by name", async ({ page }) => {
      const filterName1 = `filter-a-${uniqueId}`;
      const filterName2 = `filter-b-${uniqueId}`;

      await projectPage.clickCreateProject();
      await projectPage.fillProjectForm({
        name: filterName1,
        type: "JIRA",
        url: "https://jira.example.com",
        query: "project=TEST",
        credentialName,
        gitUrl: "https://github.com/example/repo.git",
      });
      await projectPage.submitCreate();

      await projectPage.clickCreateProject();
      await projectPage.fillProjectForm({
        name: filterName2,
        type: "GITHUB",
        url: "https://github.com/example",
        credentialName,
        gitUrl: "https://github.com/example/repo.git",
      });
      await projectPage.submitCreate();

      await projectPage.applyNameFilter(filterName1);
      await page.waitForTimeout(500);

      const nameCells = page.locator(`td[data-label="Name"]`);
      const count = await nameCells.count();
      expect(count).toBeGreaterThanOrEqual(1);

      for (let i = 0; i < count; i++) {
        await expect(nameCells.nth(i)).toContainText(filterName1);
      }
    });

    test("6. Cancel create - no new row added", async ({ page }) => {
      const cancelName = `cancel-${uniqueId}`;
      await projectPage.clickCreateProject();
      await projectPage.fillProjectForm({
        name: cancelName,
        type: "JIRA",
        url: "https://jira.example.com",
        query: "project=TEST",
        credentialName,
        gitUrl: "https://github.com/example/repo.git",
      });
      await projectPage.cancelModal();

      await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

      const nameCell = page.locator(`td[data-label="Name"]`, {
        hasText: cancelName,
      });
      await expect(nameCell).not.toBeVisible();
    });
  });
