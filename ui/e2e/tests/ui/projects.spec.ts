import { expect, test } from "@playwright/test";

test.describe("Projects page", () => {
  test("navigates to /projects and heading is visible", async ({ page }) => {
    await page.goto("/projects");
    await expect(page.getByRole("heading", { name: "Projects" })).toBeVisible();
  });

  test("Create project button is visible", async ({ page }) => {
    await page.goto("/projects");
    await expect(
      page.getByRole("button", { name: "Create project" }),
    ).toBeVisible();
  });

  test("clicking Create project opens modal with form fields", async ({
    page,
  }) => {
    await page.goto("/projects");
    await page.getByRole("button", { name: "Create project" }).click();

    await expect(
      page.getByRole("dialog", { name: "Create project" }),
    ).toBeVisible();

    await expect(page.getByLabel("Name", { exact: true })).toBeVisible();
    await expect(page.getByLabel("URL", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Type", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Git URL")).toBeVisible();
    await expect(page.getByLabel("Git Branch")).toBeVisible();
    await expect(page.getByLabel("Credential Name")).toBeVisible();
    await expect(page.getByLabel("Credential Type")).toBeVisible();
    await expect(page.getByLabel("Token")).toBeVisible();
  });

  test("Save button is disabled when form is empty", async ({ page }) => {
    await page.goto("/projects");
    await page.getByRole("button", { name: "Create project" }).click();

    await expect(
      page.getByRole("dialog", { name: "Create project" }),
    ).toBeVisible();

    await expect(page.getByRole("button", { name: "Create" })).toBeDisabled();
  });

  test("full CRUD flow", async ({ page }) => {
    await page.goto("/projects");

    // Create
    await page.getByRole("button", { name: "Create project" }).click();
    const dialog = page.getByRole("dialog", { name: "Create project" });
    await expect(dialog).toBeVisible();

    await dialog.getByLabel("Name", { exact: true }).fill("Test Project");
    await dialog.getByLabel("URL", { exact: true }).fill("https://example.com");
    await dialog.getByLabel("Type", { exact: true }).selectOption("JIRA");
    await dialog.getByLabel("Git URL").fill("https://github.com/test/repo");
    await dialog.getByLabel("Git Branch").fill("main");
    await dialog.getByLabel("Credential Name").fill("my-cred");
    await dialog.getByLabel("Credential Type").selectOption("JIRA");
    await dialog.getByLabel("Token").fill("secret-token");

    await dialog.getByRole("button", { name: "Create" }).click();

    // Verify in table
    await expect(
      page.getByRole("cell", { name: "Test Project" }),
    ).toBeVisible();
    await expect(
      page.getByRole("cell", { name: "https://example.com" }),
    ).toBeVisible();

    // Edit via kebab
    await page.getByRole("button", { name: "Kebab toggle" }).first().click();
    await page.getByRole("menuitem", { name: "Edit" }).click();

    const editDialog = page.getByRole("dialog", { name: "Edit project" });
    await expect(editDialog).toBeVisible();
    await editDialog
      .getByLabel("Name", { exact: true })
      .fill("Updated Project");
    await editDialog.getByRole("button", { name: "Save" }).click();

    await expect(
      page.getByRole("cell", { name: "Updated Project" }),
    ).toBeVisible();

    // Delete via kebab
    await page.getByRole("button", { name: "Kebab toggle" }).first().click();
    await page.getByRole("menuitem", { name: "Delete" }).click();

    const confirmDialog = page.getByRole("dialog", { name: "Confirm dialog" });
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole("button", { name: "confirm" }).click();

    await expect(
      page.getByRole("cell", { name: "Updated Project" }),
    ).not.toBeVisible();
  });
});
