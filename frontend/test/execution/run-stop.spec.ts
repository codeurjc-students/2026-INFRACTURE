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

async function savedStatelessScenario(page: Page, user: string) {
  await register(page, user);
  const project = await post(page, "/api/v1/projects", "Recovery lab");
  const scenario = await post(
    page,
    `/api/v1/projects/${project.id}/scenarios`,
    "Stateless",
  );
  const path = `/api/v1/projects/${project.id}/scenarios/${scenario.id}`;
  await page.goto(`/projects/${project.id}/scenarios/${scenario.id}`);
  await page.getByRole("button", { name: "Add HTTP Service" }).click();
  await inspector(page).getByLabel("Name").fill("orders-api");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Saved", exact: true }),
  ).toBeVisible();
  return path;
}

function docker(...args: string[]) {
  return execFileSync("docker", args, { encoding: "utf8" }).trim();
}

function removeQuietly(kind: string, id: string) {
  try {
    execFileSync("docker", [kind, "rm", "-f", id], { stdio: "ignore" });
  } catch {
    // Nothing to remove.
  }
}

test("stops an experiment that is still starting and leaves nothing behind", async ({
  page,
}, testInfo) => {
  await savedStatelessScenario(page, "starter");

  await page.getByRole("button", { name: "Run" }).click();
  await expect(status(page)).toHaveText("Starting", { timeout: 30_000 });
  await page.getByRole("button", { name: "Stop" }).click();
  await expect(page.getByRole("button", { name: "Stopping…" })).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("startup-1-stopping.png"),
    fullPage: true,
  });

  await expect(status(page)).toHaveText("Completed", { timeout: 60_000 });
  await expect(page.getByRole("button", { name: "Run" })).toBeEnabled();
  expect(experimentResources()).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("startup-2-completed.png"),
    fullPage: true,
  });
});

// A volume of the experiment that a container of someone else uses cannot be removed, so the cleanup
// fails for real. Once that container goes, Retry cleanup removes the rest.
test("keeps Run blocked while a cleanup fails and finishes it with Retry cleanup", async ({
  page,
}, testInfo) => {
  const shot = (name: string) =>
    page.screenshot({
      path: testInfo.outputPath(`cleanup-${name}.png`),
      fullPage: true,
    });
  const bystander = `infracture-bystander-${suffix}`;
  let volume = "";

  try {
    await test.step("Run a scenario and let another container use one of its volumes", async () => {
      const scenario = await savedStatelessScenario(page, "cleaner");
      await page.getByRole("button", { name: "Run" }).click();
      await expect(status(page)).toHaveText("Running", { timeout: 180_000 });
      const current = await page.request.get(`${scenario}/executions/current`);
      const { id } = await current.json();
      volume = docker(
        "volume",
        "create",
        "--label",
        "io.infracture.instance=e2e",
        "--label",
        `io.infracture.execution=${id}`,
      );
      docker(
        "container",
        "run",
        "-d",
        "--name",
        bystander,
        "-v",
        `${volume}:/data`,
        "busybox:1.37",
        "sleep",
        "600",
      );
    });

    await test.step("Stop: the cleanup fails, retries on its own and then waits", async () => {
      await page.getByRole("button", { name: "Stop" }).click();
      await expect(
        page.getByText("The cleanup failed, trying again"),
      ).toBeVisible({ timeout: 60_000 });
      await expect(page.getByText("The cleanup could not finish")).toBeVisible({
        timeout: 120_000,
      });
      await expect(status(page)).toHaveText("Cleanup failed");
      await expect(page.getByText(/docker volume rm failed/)).toBeVisible();
      await expect(page.getByRole("button", { name: "Run" })).not.toBeVisible();
      await expect(
        page.getByRole("button", { name: "Add HTTP Service" }),
      ).toBeDisabled();
      expect(
        docker("container", "inspect", "-f", "{{.State.Running}}", bystander),
      ).toBe("true");
      await shot("1-failed");
    });

    await test.step("Free the volume and retry: the experiment ends as stopped, not failed", async () => {
      docker("container", "rm", "-f", bystander);
      await page.getByRole("button", { name: "Retry cleanup" }).click();
      await expect(status(page)).toHaveText("Completed", { timeout: 60_000 });
      await expect(page.getByRole("button", { name: "Run" })).toBeEnabled();
      expect(experimentResources()).toEqual([]);
      await shot("2-completed");
    });
  } finally {
    // Gone already when the test passed.
    removeQuietly("container", bystander);
    if (volume) removeQuietly("volume", volume);
  }
});
