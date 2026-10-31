import { expect, test, type Page } from "@playwright/test";

const suffix = Date.now().toString(36);

async function register(page: Page) {
  await page.goto("/register");
  await page.getByLabel("Name", { exact: true }).fill("Canvas owner");
  await page.getByLabel("Username", { exact: true }).fill(`canvas_${suffix}`);
  await page
    .getByLabel("Email", { exact: true })
    .fill(`canvas.${suffix}@example.test`);
  await page
    .getByLabel("Password", { exact: true })
    .fill("A secure browser password 2026!");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");
}

async function post(page: Page, path: string, name: string) {
  const token = (await page.context().cookies()).find(
    (c) => c.name === "XSRF-TOKEN",
  )!.value;
  const response = await page.request.post(path, {
    data: { name, description: "" },
    headers: { "X-XSRF-TOKEN": token },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

function inspector(page: Page) {
  return page.getByRole("complementary", { name: "Inspector" });
}

function node(page: Page, name: string) {
  return page.locator(".react-flow__node").filter({ hasText: name });
}

test("designs, saves and reopens a graph, and resolves a save from another tab", async ({
  page,
}, testInfo) => {
  let scenarioUrl = "";

  await test.step("Open an empty scenario", async () => {
    await register(page);
    const project = await post(page, "/api/v1/projects", "Cache lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Cold cache",
    );
    scenarioUrl = `/projects/${project.id}/scenarios/${scenario.id}`;
    await page.goto(scenarioUrl);
    await expect(
      page.getByRole("heading", { name: "No components yet" }),
    ).toBeVisible();
  });

  await test.step("Add nodes by click and by drag, and configure one", async () => {
    await page.getByRole("button", { name: "Add HTTP Service" }).click();
    await inspector(page).getByLabel("Name").fill("Orders API");
    await inspector(page).getByLabel("responseDelayMs").fill("250");

    await page
      .getByRole("button", { name: "Add Redis" })
      .dragTo(page.locator(".react-flow__pane"), {
        targetPosition: { x: 120, y: 320 },
      });
    await expect(node(page, "Redis")).toBeVisible();

    await page.getByRole("button", { name: "Add HTTP Service" }).click();
    await expect(inspector(page).getByLabel("Name")).toHaveValue(
      "HTTP Service",
    );
    await expect(inspector(page).getByLabel("responseDelayMs")).toHaveValue(
      "0",
    );
    await expect(page.getByText("Unsaved changes")).toBeVisible();
  });

  await test.step("Save and reopen the graph", async () => {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeDisabled();
    await expect(page.getByText("Revision 2")).toBeVisible();

    await page.reload();
    await expect(page.locator(".react-flow__node")).toHaveCount(3);
    await node(page, "Orders API").click();
    await expect(inspector(page).getByLabel("responseDelayMs")).toHaveValue(
      "250",
    );
    await node(page, "HTTP Service")
      .filter({ hasNotText: "Orders API" })
      .click();
    await expect(inspector(page).getByLabel("responseDelayMs")).toHaveValue(
      "0",
    );
    await testInfo.attach("scenario-canvas.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Detect a save from another tab and reload it", async () => {
    const other = await page.context().newPage();
    await other.goto(scenarioUrl);
    await other.getByRole("button", { name: "Add Redis" }).click();
    await other.getByRole("button", { name: "Save", exact: true }).click();
    await expect(other.getByText("Revision 3")).toBeVisible();

    await inspector(page).getByLabel("Name").fill("Payments API");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByText("This scenario was saved somewhere else"),
    ).toBeVisible();
    await expect(page.getByText("Unsaved changes")).toBeVisible();
    await testInfo.attach("scenario-conflict.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });

    await page.getByRole("button", { name: "Discard mine and reload" }).click();
    await expect(page.getByText("Revision 3")).toBeVisible();
    await expect(node(page, "Redis 2")).toBeVisible();
    await expect(node(page, "Payments API")).toHaveCount(0);
  });
});
