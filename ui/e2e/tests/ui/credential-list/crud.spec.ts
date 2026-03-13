import { expect, test } from "@playwright/test";

import { CredentialListPage } from "../pages/credential-list/CredentialListPage";

let credentialPage: CredentialListPage;

test.describe("Credential CRUD", () => {
  const uniqueId = Date.now().toString(36);
  const credentialToken = "test-token-value";

  test.beforeEach(async ({ page }) => {
    credentialPage = await CredentialListPage.build(page);
  });

  test("1. Create credential", async ({ page }) => {
    const credentialName = `create-${uniqueId}`;
    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(credentialName, credentialToken);
    await credentialPage.submitCreate();

    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    const table = await credentialPage.getTable();
    const column = await table.getColumn("Name");
    await expect(column.getByText(credentialName)).toBeVisible();
  });

  test("2. Edit credential (name only)", async ({ page }) => {
    const editName = `edit-name-${uniqueId}`;
    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(editName, credentialToken);
    await credentialPage.submitCreate();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    await credentialPage.clickRowAction(editName, "Edit");

    // Token field should be disabled by default in edit mode
    const tokenInput = page.locator(".pf-v6-c-modal-box #token");
    await expect(tokenInput).toBeDisabled();

    // Change name only
    const updatedName = `${editName}-updated`;
    await credentialPage.fillCredentialForm(updatedName);
    await credentialPage.submitEdit();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    const updatedTable = await credentialPage.getTable();
    const col = await updatedTable.getColumn("Name");
    await expect(col.getByText(updatedName)).toBeVisible();
  });

  test("3. Edit credential (name + token)", async ({ page }) => {
    const editName = `edit-both-${uniqueId}`;
    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(editName, credentialToken);
    await credentialPage.submitCreate();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    await credentialPage.clickRowAction(editName, "Edit");

    // Enable token update
    await credentialPage.toggleUpdateToken();
    const tokenInput = page.locator(".pf-v6-c-modal-box #token");
    await expect(tokenInput).toBeEnabled();

    // Update both name and token
    const updatedName = `${editName}-both`;
    await credentialPage.fillCredentialForm(updatedName, "new-token-value");
    await credentialPage.submitEdit();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    const updatedTable = await credentialPage.getTable();
    const col = await updatedTable.getColumn("Name");
    await expect(col.getByText(updatedName)).toBeVisible();
  });

  test("4. Delete credential", async ({ page }) => {
    const deleteName = `delete-${uniqueId}`;
    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(deleteName, credentialToken);
    await credentialPage.submitCreate();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    await credentialPage.clickRowAction(deleteName, "Delete");

    const dialog = await credentialPage.getConfirmDialog();
    await dialog.clickConfirm();

    await page.waitForTimeout(500);
    const nameCell = page.locator(`td[data-label="Name"]`, {
      hasText: deleteName,
    });
    await expect(nameCell).not.toBeVisible();
  });

  test("5. Validation - empty form has disabled submit", async ({ page }) => {
    await credentialPage.clickCreateCredential();

    const modal = page.locator(".pf-v6-c-modal-box");
    const createBtn = modal.getByRole("button", { name: "Create" });
    await expect(createBtn).toBeDisabled();
  });

  test("6. Filter by name", async ({ page }) => {
    const filterName1 = `filter-a-${uniqueId}`;
    const filterName2 = `filter-b-${uniqueId}`;

    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(filterName1, credentialToken);
    await credentialPage.submitCreate();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(filterName2, credentialToken);
    await credentialPage.submitCreate();
    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    await credentialPage.applyNameFilter(filterName1);
    await page.waitForTimeout(500);

    const nameCells = page.locator(`td[data-label="Name"]`);
    const count = await nameCells.count();
    expect(count).toBeGreaterThanOrEqual(1);

    for (let i = 0; i < count; i++) {
      await expect(nameCells.nth(i)).toContainText(filterName1);
    }
  });

  test("7. Cancel create - no new row added", async ({ page }) => {
    const cancelName = `cancel-${uniqueId}`;
    await credentialPage.clickCreateCredential();
    await credentialPage.fillCredentialForm(cancelName, credentialToken);
    await credentialPage.cancelModal();

    await expect(page.locator(".pf-v6-c-modal-box")).not.toBeVisible();

    const nameCell = page.locator(`td[data-label="Name"]`, {
      hasText: cancelName,
    });
    await expect(nameCell).not.toBeVisible();
  });
});
