import { execFileSync } from "node:child_process";
import { expect, test, type Page } from "@playwright/test";

const suffix = Date.now().toString(36);
const PASSWORD = "A secure browser password 2026!";
// The instance id that scripts/test-execution-stack.sh gives the backend.
const INSTANCE_LABEL = "label=io.infracture.instance=e2e";

async function register(page: Page, user = "runner") {
  await page.goto("/register");
  await page.getByLabel("Name", { exact: true }).fill("Experiment owner");
  await page.getByLabel("Username", { exact: true }).fill(`${user}_${suffix}`);
  await page
    .getByLabel("Email", { exact: true })
    .fill(`${user}.${suffix}@example.test`);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");
}

async function post(page: Page, path: string, name: string) {
  const token = (await page.context().cookies()).find(
    (cookie) => cookie.name === "XSRF-TOKEN",
  )!.value;
  const response = await page.request.post(path, {
    data: { name, description: "" },
    headers: { "X-XSRF-TOKEN": token },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

// The k6 containers of this test's installation, running or not.
function loadContainers() {
  return execFileSync(
    "docker",
    [
      "container",
      "ls",
      "-q",
      "--all",
      "--filter",
      INSTANCE_LABEL,
      "--filter",
      "label=com.docker.compose.service=workload",
    ],
    { encoding: "utf8" },
  )
    .split("\n")
    .filter(Boolean);
}

// Everything Docker holds for this test's installation: containers, networks and volumes.
function experimentResources() {
  const ids: string[] = [];
  for (const kind of ["container", "network", "volume"]) {
    const args = [kind, "ls", "-q", "--filter", INSTANCE_LABEL];
    if (kind === "container") args.push("--all");
    ids.push(
      ...execFileSync("docker", args, { encoding: "utf8" })
        .split("\n")
        .filter(Boolean),
    );
  }
  return ids;
}

const status = (page: Page) => page.locator("header output[aria-live]");
const inspector = (page: Page) =>
  page.getByRole("complementary", { name: "Inspector" });

test("runs an HTTP Service from the canvas, watches it and stops it", async ({
  page,
}, testInfo) => {
  const shot = (name: string) =>
    page.screenshot({
      path: testInfo.outputPath(`${name}.png`),
      fullPage: true,
    });

  await test.step("Save a scenario with one stateless HTTP Service", async () => {
    await register(page);
    const project = await post(page, "/api/v1/projects", "Run lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Stateless",
    );
    await page.goto(`/projects/${project.id}/scenarios/${scenario.id}`);
    await page.getByRole("button", { name: "Add HTTP Service" }).click();
    await inspector(page).getByLabel("Name").fill("orders-api");
    await expect(page.getByRole("button", { name: "Run" })).toBeDisabled();
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeVisible();
    await shot("1-saved");
  });

  await test.step("Run it and see it start, become healthy and answer", async () => {
    await page.getByRole("button", { name: "Run" }).click();
    await expect(page.getByRole("button", { name: "Stop" })).toBeVisible();
    await expect(status(page)).toHaveText(/Reserved|Starting/);
    await expect(
      page.getByText("The scenario is read-only while the experiment runs"),
    ).toBeVisible();
    await shot("2-starting");

    await expect(status(page)).toHaveText("Running", { timeout: 180_000 });
    const node = page
      .locator(".react-flow__node")
      .filter({ hasText: "orders-api" });
    await expect(node.getByText("Healthy")).toBeVisible();
    await node.getByText("orders-api").click();
    await expect(
      inspector(page).getByRole("figure", { name: "Response" }),
    ).toContainText('"component":"orders-api"', { timeout: 30_000 });
    await shot("3-running");
  });

  await test.step("Nothing can be edited while it runs", async () => {
    await expect(
      page.getByRole("button", { name: "Add HTTP Service" }),
    ).toBeDisabled();
    await expect(inspector(page).getByLabel("Name")).toBeDisabled();
    expect(experimentResources().length).toBeGreaterThan(0);
  });

  await test.step("Stop it and check that nothing is left in Docker", async () => {
    await page.getByRole("button", { name: "Stop" }).click();
    await expect(page.getByRole("button", { name: "Stopping…" })).toBeVisible();
    await expect(status(page)).toHaveText("Completed", { timeout: 120_000 });
    await expect(page.getByRole("button", { name: "Run" })).toBeEnabled();
    await expect(
      page.getByRole("button", { name: "Add HTTP Service" }),
    ).toBeEnabled();
    expect(experimentResources()).toEqual([]);
    await shot("4-completed");
  });
});

test("sends real traffic with a constant load, stops it first and keeps its summary", async ({
  page,
}, testInfo) => {
  const shot = (name: string) =>
    page.screenshot({
      path: testInfo.outputPath(`load-${name}.png`),
      fullPage: true,
    });

  await test.step("Save a scenario with an HTTP Service and a low constant load", async () => {
    await register(page, "loader");
    const project = await post(page, "/api/v1/projects", "Load lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Constant load",
    );
    await page.goto(`/projects/${project.id}/scenarios/${scenario.id}`);
    await page.getByRole("button", { name: "Add HTTP Service" }).click();
    await inspector(page).getByLabel("Name").fill("orders-api");
    await page.getByRole("button", { name: "Load" }).click();
    await inspector(page).getByRole("button", { name: "Add load" }).click();
    await inspector(page).getByLabel("Target").click();
    await page.getByRole("option", { name: "orders-api" }).click();
    await inspector(page).getByLabel("requestsPerSecond").fill("5");
    await inspector(page).getByLabel("virtualUsers").fill("2");
    await inspector(page).getByLabel("durationSeconds").fill("120");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("group", { name: /receives the load at 5 req\/s/ }),
    ).toBeVisible();
    await shot("1-saved");
  });

  await test.step("Run it: k6 starts once the service is healthy and sends traffic", async () => {
    await page.getByRole("button", { name: "Run" }).click();
    await expect(status(page)).toHaveText("Running", { timeout: 180_000 });
    await expect(inspector(page).getByText("Sending traffic")).toBeVisible();
    expect(loadContainers()).toHaveLength(1);
    // A few seconds of real requests before Stop.
    await page.waitForTimeout(5_000);
    await shot("2-sending");
  });

  await test.step("Stop it: the summary counts real requests and nothing is left", async () => {
    await page.getByRole("button", { name: "Stop" }).click();
    await expect(status(page)).toHaveText("Completed", { timeout: 120_000 });
    await expect(inspector(page).getByText("Stopped early")).toBeVisible();
    const summary = inspector(page).getByLabel("Load summary");
    const requests = Number(
      (await summary
        .locator("div", { hasText: "requests" })
        .locator("dd")
        .textContent()) ?? "0",
    );
    expect(requests).toBeGreaterThan(0);
    await expect(
      summary.locator("div", { hasText: "failed" }).locator("dd"),
    ).toHaveText("0");
    expect(loadContainers()).toEqual([]);
    expect(experimentResources()).toEqual([]);
    await shot("3-summary");
  });
});
