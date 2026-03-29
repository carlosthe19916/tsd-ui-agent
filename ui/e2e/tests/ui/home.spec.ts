import { expect, test } from "@playwright/test";

test.describe("Home page", () => {
  test("Shows Dashboard on the home page", async ({ page }) => {
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "Dashboard" }),
    ).toBeVisible();
  });
});
