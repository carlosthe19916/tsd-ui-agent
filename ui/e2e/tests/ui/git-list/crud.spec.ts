import { expect, test } from "@playwright/test";

import { GitListPage } from "../pages/git-list/GitListPage";

let gitPage: GitListPage;

test.describe("Git Repository CRUD", () => {
  const uniqueId = Date.now().toString(36);

  test.beforeEach(async ({ page }) => {
    gitPage = await GitListPage.build(page);
  });

  test("1. Create git repository", async () => {
    const gitUrl = `https://github.com/test/repo-${uniqueId}`;
    await gitPage.clickCreateGit();
    await gitPage.fillGitForm(gitUrl);
    await gitPage.submitCreate();

    const dataList = await gitPage.getDataList();
    await expect(dataList.getByText(gitUrl)).toBeVisible();
  });

  test("2. Delete git repository", async ({ page }) => {
    const deleteUrl = `https://github.com/test/delete-${uniqueId}`;
    await gitPage.clickCreateGit();
    await gitPage.fillGitForm(deleteUrl);
    await gitPage.submitCreate();

    await gitPage.clickRowAction(deleteUrl, "Delete");

    const dialog = await gitPage.getConfirmDialog();
    await dialog.clickConfirm();

    await page.waitForTimeout(500);
    await expect(
      page
        .locator(`ul[aria-label="Git repositories list"]`)
        .locator(".pf-v6-c-data-list__item")
        .filter({ hasText: deleteUrl }),
    ).toHaveCount(0);
  });

  test("3. Cancel create - no new row added", async ({ page }) => {
    const cancelUrl = `https://github.com/test/cancel-${uniqueId}`;
    await gitPage.clickCreateGit();
    await gitPage.fillGitForm(cancelUrl);
    await gitPage.cancelModal();

    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    const hasItem = await gitPage.hasItemWithUrl(cancelUrl);
    expect(hasItem).toBe(false);
  });

  test("4. Validation - empty form has disabled submit", async ({ page }) => {
    await gitPage.clickCreateGit();

    const modal = page.locator(".pf-v6-c-modal-box");
    const createBtn = modal.getByRole("button", { name: "Create" });
    await expect(createBtn).toBeDisabled();
  });

  test("5. Filter by URL", async ({ page }) => {
    const filterUrl1 = `https://github.com/test/filter-a-${uniqueId}`;
    const filterUrl2 = `https://github.com/test/filter-b-${uniqueId}`;

    await gitPage.clickCreateGit();
    await gitPage.fillGitForm(filterUrl1);
    await gitPage.submitCreate();

    await gitPage.clickCreateGit();
    await gitPage.fillGitForm(filterUrl2);
    await gitPage.submitCreate();

    await gitPage.applyUrlFilter(`filter-a-${uniqueId}`);
    await page.waitForTimeout(500);

    const dataList = page.locator(`ul[aria-label="Git repositories list"]`);
    const items = dataList
      .locator(".pf-v6-c-data-list__item")
      .filter({ hasText: `filter-a-${uniqueId}` });
    await expect(items.first()).toBeVisible();
    await expect(items).toHaveCount(1);
  });
});
