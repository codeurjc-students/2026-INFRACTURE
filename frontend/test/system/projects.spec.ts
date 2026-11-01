import { expect, test, type Page } from "@playwright/test";

const suffix = Date.now().toString(36);

function account(name: string) {
  return {
    displayName: `Projects ${name}`,
    username: `pr_${name}_${suffix}`,
    email: `pr.${name}.${suffix}@example.test`,
    password: "A secure browser password 2026!",
  };
}

async function register(page: Page, user: ReturnType<typeof account>) {
  await page.goto("/register");
  await page.getByLabel("Name", { exact: true }).fill(user.displayName);
  await page.getByLabel("Username", { exact: true }).fill(user.username);
  await page.getByLabel("Email", { exact: true }).fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill(user.password);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");
}

test("creates, edits and reopens a private project that another account cannot read", async ({
  browser,
  page,
}, testInfo) => {
  let projectUrl = "";

  await test.step("Create a project from the empty list", async () => {
    await register(page, account("owner"));
    await page.getByRole("link", { name: "Open your projects" }).click();

    await expect(
      page.getByRole("heading", { name: "Nothing here to break yet" }),
    ).toBeVisible();
    await page.getByRole("link", { name: "Create your first project" }).click();
    await page.getByRole("button", { name: "Create project" }).click();
    await expect(page.getByText("Enter a name for the project.")).toBeVisible();

    await page.getByLabel("Name").fill("Cache lab");
    await page.getByLabel("Description").fill("Redis in front of PostgreSQL.");
    await page.getByRole("button", { name: "Create project" }).click();

    await expect(page).toHaveURL(/\/projects\/\d+$/);
    await expect(
      page.getByRole("heading", { level: 1, name: "Cache lab" }),
    ).toBeVisible();
    projectUrl = new URL(page.url()).pathname;
  });

  await test.step("Edit the name and description, then reopen the project", async () => {
    await page.getByRole("button", { name: "Edit details" }).click();
    await page.getByLabel("Name").fill("Cache drill");
    await page
      .getByLabel("Description")
      .fill("What happens without the cache?");
    await page.getByRole("button", { name: "Save changes" }).click();

    await expect(page.getByText("Changes saved")).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole("heading", { level: 1, name: "Cache drill" }),
    ).toBeVisible();
    await expect(
      page.getByText("What happens without the cache?"),
    ).toBeVisible();
    await testInfo.attach("project.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Browse more than ten projects", async () => {
    for (let index = 1; index <= 10; index++) {
      const response = await page.request.post("/api/v1/projects", {
        data: { name: `Project ${index}`, description: "" },
        headers: {
          "X-XSRF-TOKEN": (await page.context().cookies()).find(
            (c) => c.name === "XSRF-TOKEN",
          )!.value,
        },
      });
      expect(response.status()).toBe(201);
    }

    await page.goto("/projects");
    const cards = page.getByRole("main").getByRole("list").getByRole("link");
    await expect(cards).toHaveCount(10);
    await expect(page.getByRole("status")).toHaveText("Showing 10 of 11");
    await page.getByRole("button", { name: "More results" }).click();
    await expect(cards).toHaveCount(11);
    await expect(cards.last()).toContainText("Cache drill");
    await testInfo.attach("projects.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Another account and a visitor cannot open the project", async () => {
    const visitor = await browser.newPage({
      baseURL: testInfo.project.use.baseURL,
    });
    await visitor.goto(projectUrl);
    await expect(visitor).toHaveURL(
      `/login?redirectTo=${encodeURIComponent(projectUrl)}`,
    );

    await register(visitor, account("other"));
    await visitor.goto(projectUrl);
    await expect(
      visitor.getByRole("heading", { name: "Project not found" }),
    ).toBeVisible();
    await visitor.goto("/projects");
    await expect(
      visitor.getByRole("heading", { name: "Nothing here to break yet" }),
    ).toBeVisible();
    await visitor.close();
  });
});

test("creates, renames and reopens empty scenarios that another account cannot open", async ({
  browser,
  page,
}, testInfo) => {
  let scenarioUrl = "";
  const scenarios = page
    .getByRole("region", { name: "Scenarios" })
    .getByRole("list")
    .getByRole("link");

  async function post(path: string, name: string) {
    const response = await page.request.post(path, {
      data: { name, description: "" },
      headers: {
        "X-XSRF-TOKEN": (await page.context().cookies()).find(
          (c) => c.name === "XSRF-TOKEN",
        )!.value,
      },
    });
    expect(response.status()).toBe(201);
    return response.json();
  }

  await test.step("Create two empty scenarios in a project", async () => {
    await register(page, account("scenarios"));
    const project = await post("/api/v1/projects", "Cache lab");
    await page.goto(`/projects/${project.id}`);

    await expect(
      page.getByRole("heading", { name: "No scenarios yet" }),
    ).toBeVisible();
    await page
      .getByRole("link", { name: "Create your first scenario" })
      .click();
    await page.getByLabel("Name").fill("Cold cache");
    await page.getByRole("button", { name: "Create scenario" }).click();

    await expect(page).toHaveURL(/\/projects\/\d+\/scenarios\/\d+$/);
    await expect(
      page.getByRole("heading", { level: 1, name: "Cold cache" }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "No components yet" }),
    ).toBeVisible();
    scenarioUrl = new URL(page.url()).pathname;

    await page
      .getByRole("navigation", { name: "Breadcrumb" })
      .getByRole("link", { name: "Cache lab" })
      .click();
    await page.getByRole("link", { name: "New scenario" }).click();
    await page.getByLabel("Name").fill("Warm cache");
    await page.getByRole("button", { name: "Create scenario" }).click();
    await expect(page).toHaveURL(/\/scenarios\/\d+$/);
    expect(new URL(page.url()).pathname).not.toBe(scenarioUrl);
    await expect(
      page.getByRole("heading", { level: 1, name: "Warm cache" }),
    ).toBeVisible();
  });

  await test.step("Rename one scenario and reopen both", async () => {
    await page.goto(scenarioUrl);
    await page.getByRole("button", { name: "Details" }).click();
    await page.getByLabel("Name").fill("Cold start");
    await page.getByLabel("Description").fill("Redis starts empty.");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByText("Changes saved")).toBeVisible();

    await page.reload();
    await expect(
      page.getByRole("heading", { level: 1, name: "Cold start" }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Details" }).click();
    await expect(page.getByLabel("Description")).toHaveValue(
      "Redis starts empty.",
    );
    await testInfo.attach("scenario.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });

    await page
      .getByRole("navigation", { name: "Breadcrumb" })
      .getByRole("link", { name: "Cache lab" })
      .click();
    await expect(scenarios).toHaveCount(2);
    await scenarios.filter({ hasText: "Warm cache" }).click();
    await expect(
      page.getByRole("heading", { level: 1, name: "Warm cache" }),
    ).toBeVisible();
  });

  await test.step("Browse more than ten scenarios", async () => {
    const projectPath = scenarioUrl.replace(/\/scenarios\/\d+$/, "");
    for (let index = 1; index <= 9; index++) {
      await post(`/api/v1${projectPath}/scenarios`, `Scenario ${index}`);
    }

    await page.goto(projectPath);
    await expect(scenarios).toHaveCount(10);
    await page.getByRole("button", { name: "More results" }).click();
    await expect(scenarios).toHaveCount(11);
    await expect(scenarios.last()).toContainText("Cold start");
    await testInfo.attach("scenarios.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Another account cannot open the scenario", async () => {
    const other = await browser.newPage({
      baseURL: testInfo.project.use.baseURL,
    });
    await register(other, account("intruder"));
    await other.goto(scenarioUrl);
    await expect(
      other.getByRole("heading", { name: "Scenario not found" }),
    ).toBeVisible();
    await other.close();
  });
});
