import type { Page } from "playwright-core";

export class Navigation {
  private readonly _page: Page;

  private constructor(page: Page) {
    this._page = page;
  }

  static async build(page: Page) {
    return new Navigation(page);
  }

  async goToSidebar(menu: "Home" | "Credentials" | "Projects") {
    await this._page.goto("/credentials");
    await this._page.getByRole("link", { name: menu }).click();
  }
}
