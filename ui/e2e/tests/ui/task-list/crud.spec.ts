import { expect, test } from "@playwright/test";

test.describe("Task List", () => {
  const uniqueId = Date.now().toString(36);

  test("1. View task list page", async ({ page }) => {
    await page.goto("/tasks");
    await page.waitForLoadState("networkidle");

    await expect(
      page.getByRole("heading", { name: "Tasks", level: 1 }),
    ).toBeVisible();
  });

  test("2. Create task", async ({ page }) => {
    await page.goto("/tasks");
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: "Create task" }).click();

    const modal = page.locator(`.pf-v6-c-modal-box[aria-label="Create task"]`);
    await expect(modal).toBeVisible();

    const titleInput = modal.locator("#task-title");
    await titleInput.fill(`Task ${uniqueId}`);

    await modal.getByRole("button", { name: "Create" }).click();
    await expect(modal).not.toBeVisible();

    const dataList = page.locator(`ul[aria-label="Tasks list"]`);
    await expect(dataList.getByText(`Task ${uniqueId}`)).toBeVisible();
  });

  test("3. Cancel create task - no new item added", async ({ page }) => {
    await page.goto("/tasks");
    await page.waitForLoadState("networkidle");

    const cancelTitle = `Cancel Task ${uniqueId}`;

    await page.getByRole("button", { name: "Create task" }).click();

    const modal = page.locator(`.pf-v6-c-modal-box[aria-label="Create task"]`);
    await expect(modal).toBeVisible();

    const titleInput = modal.locator("#task-title");
    await titleInput.fill(cancelTitle);

    await modal.getByRole("button", { name: "Cancel" }).click();
    await expect(modal).not.toBeVisible();

    const dataList = page.locator(`ul[aria-label="Tasks list"]`);
    await expect(
      dataList
        .locator(".pf-v6-c-data-list__item")
        .filter({ hasText: cancelTitle }),
    ).toHaveCount(0);
  });

  test("4. Validation - empty form has disabled submit", async ({ page }) => {
    await page.goto("/tasks");
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: "Create task" }).click();

    const modal = page.locator(`.pf-v6-c-modal-box[aria-label="Create task"]`);
    await expect(modal).toBeVisible();

    const createBtn = modal.getByRole("button", { name: "Create" });
    await expect(createBtn).toBeDisabled();
  });
});
