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
    // Run would save these changes first; this step saves them by hand.
    await expect(page.getByRole("button", { name: "Run" })).toHaveAttribute(
      "title",
      "Save your changes and run them",
    );
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

  await test.step("Follow what happens and what the service prints in Activity", async () => {
    await page.getByRole("button", { name: "Activity" }).click();
    const activity = page.getByRole("region", { name: "Activity" });
    await expect(activity.getByRole("status")).toHaveText("Live");
    const events = activity.getByRole("list", { name: "Events" });
    await expect(events).toContainText("Every component is healthy");
    await expect(events.getByRole("listitem").filter({ hasText: "orders-api" }).first()).toBeVisible();
    // The demo app's own Spring Boot log, read from Docker and shown as it was printed.
    await expect(events).toContainText("Started DemoApplication", {
      timeout: 30_000,
    });
    await shot("3b-activity");
  });

  await test.step("Stop it and check that nothing is left in Docker", async () => {
    await page.getByRole("button", { name: "Stop" }).click();
    await expect(page.getByRole("button", { name: "Stopping…" })).toBeVisible();
    await expect(status(page)).toHaveText("Completed", { timeout: 120_000 });
    await expect(
      page.getByRole("region", { name: "Activity" }).getByRole("list", { name: "Events" }),
    ).toContainText("Cleanup finished");
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

test("charts the real CPU and memory of every component under the canvas, live and after Stop", async ({
  page,
}, testInfo) => {
  const shot = (name: string) =>
    page.screenshot({
      path: testInfo.outputPath(`metrics-${name}.png`),
      fullPage: true,
    });
  const metrics = page.getByRole("region", { name: "CPU and memory" });
  const toggle = metrics.getByRole("button", { name: "CPU and memory" });
  const rows = metrics.getByRole("list", { name: "Components" });
  const ordersCpu = metrics.getByRole("figure", {
    name: "orders-api CPU, percent of one core",
  });
  const ordersMemory = metrics.getByRole("figure", {
    name: "orders-api memory, MiB",
  });
  const usersMemory = metrics.getByRole("figure", {
    name: "users-api memory, MiB",
  });

  await test.step("Run a scenario with two HTTP Services", async () => {
    await register(page, "metrics");
    const project = await post(page, "/api/v1/projects", "Metrics lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Two services",
    );
    await page.goto(`/projects/${project.id}/scenarios/${scenario.id}`);
    await expect(metrics).toHaveCount(0);
    for (const name of ["orders-api", "users-api"]) {
      await page.getByRole("button", { name: "Add HTTP Service" }).click();
      await inspector(page).getByLabel("Name").fill(name);
    }
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Run" }).click();
    // Run opens the panel under the canvas.
    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    await expect(status(page)).toHaveText("Running", { timeout: 180_000 });
  });

  await test.step("See every component's real samples at once", async () => {
    await expect(ordersCpu).toContainText(/Latest \d+(\.\d+)?%/, {
      timeout: 30_000,
    });
    await expect(ordersCpu).toContainText("Limit 50%");
    await expect(ordersMemory).toContainText(/Latest [1-9]\d*(\.\d+)? MiB/);
    await expect(ordersMemory).toContainText("Limit 384 MiB");
    await expect(usersMemory).toContainText(/Latest [1-9]\d*(\.\d+)? MiB/);
    await expect(metrics).toContainText("Live · every 5 s");
    await expect(metrics).toContainText("2 components");
    // Two real readings at different moments draw a line over time.
    await expect(ordersCpu.locator("path.recharts-line-curve")).toHaveAttribute(
      "d",
      /^M[^L]+[LC]/,
      { timeout: 20_000 },
    );
    await shot("1-live");
  });

  await test.step("Fold the panel and open it again", async () => {
    await toggle.click();
    await expect(toggle).toHaveAttribute("aria-expanded", "false");
    await expect(rows).toBeHidden();
    await shot("2-folded");
    await toggle.click();
    await expect(rows).toBeVisible();
  });

  await test.step("Keep the series after Stop and after reloading", async () => {
    await page.getByRole("button", { name: "Stop" }).click();
    await expect(status(page)).toHaveText("Completed", { timeout: 120_000 });
    await expect(ordersMemory).toContainText(/Latest/);
    await page.reload();
    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    await expect(ordersCpu).toContainText(/Latest/);
    await expect(usersMemory).toContainText(/Latest/);
    await expect(metrics).toContainText("Docker stats · every 5 s");
    expect(experimentResources()).toEqual([]);
    await shot("3-after-stop");
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

test("runs the revision this tab saves and locks another tab without losing its work", async ({
  page,
}, testInfo) => {
  const path = await savedStatelessScenario(page, "tabs");
  const other = await page.context().newPage();
  await other.goto(page.url());
  await other.getByText("orders-api").click();
  await inspector(other).getByLabel("Name").fill("orders-other-tab");

  await test.step("Run saves this tab's change and runs exactly that revision", async () => {
    await page.bringToFront();
    await inspector(page).getByLabel("Name").fill("orders-v2");
    await page.getByRole("button", { name: "Run" }).click();
    await expect(page.getByRole("button", { name: "Stop" })).toBeVisible();
    const graph = await (await page.request.get(`${path}/graph`)).json();
    const execution = await (
      await page.request.get(`${path}/executions/current`)
    ).json();
    expect(graph.revision).toBe(3);
    expect(execution.scenarioRevision).toBe(3);
    expect(
      execution.components.map((node: { name: string }) => node.name),
    ).toEqual(["orders-v2"]);
  });

  await test.step("The other tab keeps its change in the browser and turns read-only", async () => {
    await other.bringToFront();
    await other.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      other.getByText("The scenario is read-only while the experiment runs"),
    ).toBeVisible();
    await expect(
      other.getByText("You have unsaved changes from earlier"),
    ).toBeVisible();
    await expect(
      other.getByRole("button", { name: "Restore my changes" }),
    ).toBeDisabled();
    await expect(other.getByText("orders-v2")).toBeVisible();
    await expect(
      other.getByText("This scenario was saved somewhere else"),
    ).toBeHidden();
    await other.screenshot({
      path: testInfo.outputPath("tabs-1-other-tab-locked.png"),
      fullPage: true,
    });
  });

  await test.step("After Stop both tabs unlock and nothing is left in Docker", async () => {
    await page.bringToFront();
    await page.getByRole("button", { name: "Stop" }).click();
    await expect(status(page)).toHaveText("Completed", { timeout: 120_000 });
    await expect(
      other.getByRole("button", { name: "Restore my changes" }),
    ).toBeEnabled({ timeout: 10_000 });
    expect(experimentResources()).toEqual([]);
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

// scripts/test-execution-stack.sh cuts the limit to 60 s, so the test reaches it without waiting 30 minutes.
test("ends the experiment at its time limit with the tab closed and shows why on return", async ({
  context,
  page,
}, testInfo) => {
  const shot = (target: Page, name: string) =>
    target.screenshot({
      path: testInfo.outputPath(`time-limit-${name}.png`),
      fullPage: true,
    });
  const scenario = await savedStatelessScenario(page, "timer");
  const canvas = page.url();
  let deadline = "";

  await test.step("Run it and see the server's countdown", async () => {
    await page.getByRole("button", { name: "Run" }).click();
    await expect(status(page)).toHaveText("Running", { timeout: 180_000 });
    await expect(page.getByRole("timer", { name: "Time left" })).toHaveText(
      /^[01]:\d\d left$/,
    );
    const current = await page.request.get(`${scenario}/executions/current`);
    ({ deadlineAt: deadline } = await current.json());
    expect(deadline).toBeTruthy();
    await shot(page, "1-running");
  });

  await test.step("Close the tab: the experiment keeps running with the same deadline", async () => {
    await page.close();
    const current = await context.request.get(`${scenario}/executions/current`);
    expect(await current.json()).toMatchObject({
      phase: "RUNNING",
      stopCause: null,
      deadlineAt: deadline,
    });
  });

  await test.step("Wait past the deadline with no tab open", async () => {
    await expect
      .poll(
        async () =>
          (
            await (
              await context.request.get(`${scenario}/executions/current`)
            ).json()
          ).phase,
        { timeout: 150_000, intervals: [2_000] },
      )
      .toBe("RELEASED");
  });

  await test.step("Reopen the canvas: it ended at the time limit and nothing is left", async () => {
    const reopened = await context.newPage();
    await reopened.goto(canvas);
    await expect(status(reopened)).toHaveText("Time limit reached");
    await expect(reopened.getByRole("timer")).not.toBeVisible();
    await expect(reopened.getByRole("button", { name: "Run" })).toBeEnabled();
    const current = await reopened.request.get(
      `${scenario}/executions/current`,
    );
    expect(await current.json()).toMatchObject({
      outcome: "COMPLETED",
      stopCause: "TIME_LIMIT",
      deadlineAt: deadline,
    });
    expect(experimentResources()).toEqual([]);
    await shot(reopened, "2-reopened");
  });
});

// The backend container is killed mid-run and started again, the way a crash and a restart look.
// Its JWT key is new after the restart, so the owner signs in again.
test("interrupts the experiment after a backend restart, removes only its containers and unlocks Run", async ({
  page,
}, testInfo) => {
  const shot = (name: string) =>
    page.screenshot({
      path: testInfo.outputPath(`restart-${name}.png`),
      fullPage: true,
    });
  const foreigner = `infracture-other-instance-${suffix}`;

  try {
    const scenario = await savedStatelessScenario(page, "restarter");
    const canvas = page.url();

    await test.step("Run it and add a container of another installation with the same execution id", async () => {
      await page.getByRole("button", { name: "Run" }).click();
      await expect(status(page)).toHaveText("Running", { timeout: 180_000 });
      const { id } = await (
        await page.request.get(`${scenario}/executions/current`)
      ).json();
      docker(
        "container",
        "run",
        "-d",
        "--name",
        foreigner,
        "--label",
        "io.infracture.instance=other",
        "--label",
        `io.infracture.execution=${id}`,
        "busybox:1.37",
        "sleep",
        "600",
      );
    });

    await test.step("Kill the backend and start it again: it removes the experiment on its own", async () => {
      docker("kill", "infracture-backend");
      docker("start", "infracture-backend");
      await expect
        .poll(() => experimentResources(), {
          timeout: 180_000,
          intervals: [2_000],
        })
        .toEqual([]);
    });

    // Docker Desktop stops forwarding the published port once the restarted backend leaves the
    // experiment network, so the test restarts it once more, as the documentation tells users.
    await test.step("Restart the backend once more and wait until it answers", async () => {
      docker("restart", "infracture-backend");
      await expect
        .poll(
          () =>
            page.request
              .get("http://127.0.0.1:8080/actuator/health", { timeout: 5_000 })
              .then((response) => response.status())
              .catch(() => 0),
          { timeout: 180_000, intervals: [2_000] },
        )
        .toBe(200);
    });

    await test.step("Sign in again: the canvas says the restart interrupted it and Run is back", async () => {
      await page.goto("/login");
      await page
        .getByLabel("Email", { exact: true })
        .fill(`restarter.${suffix}@example.test`);
      await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page).toHaveURL("/");
      await page.goto(canvas);
      await expect(
        page.getByText(
          "The backend restarted while this experiment was running",
        ),
      ).toBeVisible({ timeout: 60_000 });
      await expect(status(page)).toHaveText("Interrupted", { timeout: 60_000 });
      await expect(
        page.getByText("It was not resumed. Run starts a new experiment."),
      ).toBeVisible();
      await expect(page.getByRole("button", { name: "Run" })).toBeEnabled();
      const current = await page.request.get(`${scenario}/executions/current`);
      expect(await current.json()).toMatchObject({
        phase: "RELEASED",
        stopCause: "INTERRUPTION",
        outcome: "INTERRUPTED",
        interruptedPhase: "RUNNING",
      });
      await shot("1-interrupted");
    });

    await test.step("Only this installation's containers are gone", async () => {
      expect(experimentResources()).toEqual([]);
      expect(
        docker("container", "inspect", "-f", "{{.State.Running}}", foreigner),
      ).toBe("true");
    });
  } finally {
    removeQuietly("container", foreigner);
  }
});
