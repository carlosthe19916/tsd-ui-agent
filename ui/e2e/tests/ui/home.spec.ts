import { expect, test } from "@playwright/test";

test.describe("Home page", () => {
  test("Shows Hello World on the home page", async ({ page }) => {
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "Hello World" }),
    ).toBeVisible();
  });
});
