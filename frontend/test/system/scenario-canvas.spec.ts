import { expect, test, type Page } from "@playwright/test";

const suffix = Date.now().toString(36);
const PASSWORD = "A secure browser password 2026!";

function email(name: string) {
  return `${name}.${suffix}@example.test`;
}

async function register(page: Page, name = "canvas") {
  await page.goto("/register");
  await page.getByLabel("Name", { exact: true }).fill("Canvas owner");
  await page.getByLabel("Username", { exact: true }).fill(`${name}_${suffix}`);
  await page.getByLabel("Email", { exact: true }).fill(email(name));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
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

// Drags from a node's port dot to the middle of another node, moving in steps as a mouse user does:
// the whole target node only becomes a drop zone once the drag has started.
async function connect(page: Page, from: string, port: string, to: string) {
  const handle = await node(page, from)
    .locator(`.react-flow__handle[data-handleid="${port}"]`)
    .boundingBox();
  const target = await node(page, to).boundingBox();
  await page.mouse.move(
    handle!.x + handle!.width / 2,
    handle!.y + handle!.height / 2,
  );
  await page.mouse.down();
  await page.mouse.move(
    target!.x + target!.width / 2,
    target!.y + target!.height / 2,
    { steps: 10 },
  );
  await page.mouse.up();
}

async function chooseProfile(page: Page, profile: string) {
  await inspector(page).getByLabel("Profile").click();
  await page.getByRole("option", { name: profile }).click();
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
    await expect(page.getByText("Revision 2", { exact: true })).toBeVisible();

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
    await expect(other.getByText("Revision 3", { exact: true })).toBeVisible();

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
    await expect(page.getByText("Revision 3", { exact: true })).toBeVisible();
    await expect(node(page, "Redis 2")).toBeVisible();
    // The discarded version is not lost: it is offered back.
    await expect(
      page.getByText("You have unsaved changes from earlier"),
    ).toBeVisible();
    await expect(node(page, "Payments API")).toHaveCount(0);
  });
});

test("keeps unsaved work through an expired session and offers it only to its owner", async ({
  page,
}) => {
  let scenarioUrl = "";

  await test.step("Edit a scenario and lose the session before saving", async () => {
    await register(page, "draft");
    const project = await post(page, "/api/v1/projects", "Draft lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Expiring session",
    );
    scenarioUrl = `/projects/${project.id}/scenarios/${scenario.id}`;
    await page.goto(scenarioUrl);
    await page.getByRole("button", { name: "Add Redis" }).click();
    await inspector(page).getByLabel("Name").fill("Session cache");

    await page.context().clearCookies();
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByText("Your session has expired")).toBeVisible();
  });

  await test.step("Sign in again and recover the draft", async () => {
    await page.getByRole("link", { name: "Sign in again" }).click();
    await page.getByLabel("Email", { exact: true }).fill(email("draft"));
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(scenarioUrl);

    await expect(
      page.getByText("You have unsaved changes from earlier"),
    ).toBeVisible();
    await page.getByRole("button", { name: "Restore my changes" }).click();
    await expect(node(page, "Session cache")).toBeVisible();
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeDisabled();

    await page.reload();
    await expect(node(page, "Session cache")).toBeVisible();
    await expect(
      page.getByText("You have unsaved changes from earlier"),
    ).toHaveCount(0);
  });

  await test.step("Another account on the same browser never sees the draft", async () => {
    await page.getByRole("button", { name: "Add Worker", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Save", exact: true }),
    ).toBeEnabled();
    await page.context().clearCookies();

    await register(page, "other");
    await page.goto(scenarioUrl);
    await expect(
      page.getByRole("heading", { name: "Scenario not found" }),
    ).toBeVisible();
    await expect(
      page.getByText("You have unsaved changes from earlier"),
    ).toHaveCount(0);
    expect(
      await page.evaluate(() =>
        Object.keys(localStorage).filter((key) =>
          key.startsWith("infracture:scenario-draft:"),
        ),
      ),
    ).toHaveLength(1);
  });
});

test("connects ports to compatible nodes, keeps arrows through a profile change and deletes them with their node", async ({
  page,
}, testInfo) => {
  const arrows = page.locator(".react-flow__edge");

  await test.step("Add an API with a cache-aside profile and its dependencies", async () => {
    await register(page, "links");
    const project = await post(page, "/api/v1/projects", "Links lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Cache aside",
    );
    await page.goto(`/projects/${project.id}/scenarios/${scenario.id}`);
    await page.getByRole("button", { name: "Add HTTP Service" }).click();
    await inspector(page).getByLabel("Name").fill("Orders API");
    await chooseProfile(page, "CACHE_ASIDE");
    await page.getByRole("button", { name: "Add Redis" }).click();
    await page.getByRole("button", { name: "Add PostgreSQL" }).click();
  });

  await test.step("Only compatible targets accept an arrow", async () => {
    await connect(page, "Orders API", "cache", "PostgreSQL");
    await expect(arrows).toHaveCount(0);

    await connect(page, "Orders API", "cache", "Redis");
    await connect(page, "Orders API", "database", "PostgreSQL");
    await expect(arrows).toHaveCount(2);
    await expect(page.getByLabel("Orders API Cache Redis")).toBeVisible();
    await expect(
      page.getByLabel("Orders API Database PostgreSQL"),
    ).toBeVisible();
  });

  await test.step("Save, reopen and change the profile without losing arrows", async () => {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeDisabled();
    await page.reload();
    await expect(arrows).toHaveCount(2);

    await node(page, "Orders API").click();
    await chooseProfile(page, "STATELESS");
    await expect(arrows).toHaveCount(2);
    await expect(
      page.getByLabel("Orders API cache Redis, not allowed"),
    ).toBeVisible();
    await expect(
      inspector(page).getByText(
        "Orders API has no port cache in profile STATELESS",
      ),
    ).toBeVisible();
    await testInfo.attach("scenario-connections.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Delete a node with its arrow, save and reopen", async () => {
    await node(page, "Redis").click();
    await inspector(page)
      .getByRole("button", { name: "Delete component" })
      .click();
    await expect(arrows).toHaveCount(1);
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeDisabled();

    await page.reload();
    await expect(page.locator(".react-flow__node")).toHaveCount(2);
    await expect(arrows).toHaveCount(1);
    await expect(
      page.getByLabel("Orders API database PostgreSQL, not allowed"),
    ).toBeVisible();
  });
});

test("finds errors in an invalid graph, corrects them and keeps only allowed warnings", async ({
  page,
}, testInfo) => {
  const issues = (count: string) =>
    page.getByRole("button", { name: count, exact: true });

  await test.step("An empty scenario cannot run yet", async () => {
    await register(page, "checks");
    const project = await post(page, "/api/v1/projects", "Checks lab");
    const scenario = await post(
      page,
      `/api/v1/projects/${project.id}/scenarios`,
      "Task queue",
    );
    await page.goto(`/projects/${project.id}/scenarios/${scenario.id}`);
    await issues("1 issue").click();
    await expect(
      inspector(page).getByText(
        "Add at least one component to run the scenario",
      ),
    ).toBeVisible();
  });

  await test.step("Save a draft with a cycle and a missing dependency", async () => {
    for (const [name, profile] of [
      ["Gateway", "DOWNSTREAM_HTTP"],
      ["Edge", "DOWNSTREAM_HTTP"],
      ["Tasks API", "QUEUE_PRODUCER"],
    ]) {
      await page.getByRole("button", { name: "Add HTTP Service" }).click();
      await inspector(page).getByLabel("Name").fill(name);
      await chooseProfile(page, profile);
    }
    await page.getByRole("button", { name: "Add RabbitMQ" }).click();
    await connect(page, "Gateway", "downstream", "Edge");
    await connect(page, "Edge", "downstream", "Gateway");
    await page.getByRole("button", { name: "Save", exact: true }).click();

    await issues("3 issues").click();
    await expect(
      inspector(page).getByText(
        "2 errors block running the scenario. Warnings do not.",
      ),
    ).toBeVisible();
    await expect(
      inspector(page).getByText(
        "Tasks API needs at least 1 connection on port queue",
      ),
    ).toBeVisible();
    await testInfo.attach("scenario-issues.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Locate the cycle and correct the graph", async () => {
    await inspector(page)
      .getByRole("button", { name: /Edge → Gateway closes a dependency cycle/ })
      .click();
    await expect(inspector(page).getByLabel("Name")).toHaveValue("Edge");
    await inspector(page)
      .getByRole("button", { name: "Remove connection Calls → Gateway" })
      .click();
    await connect(page, "Edge", "downstream", "Tasks API");
    await connect(page, "Tasks API", "queue", "RabbitMQ");

    await issues("3 issues, from the last save").click();
    await expect(
      inspector(page).getByText(
        "These issues come from the last save. Save to check your changes.",
      ),
    ).toBeVisible();
  });

  await test.step("Save and keep only the warnings that do not block running", async () => {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Saved", exact: true }),
    ).toBeDisabled();
    // The issues panel stays open and now shows the issues of this save.
    await expect(issues("2 issues")).toBeVisible();
    await expect(
      inspector(page).getByText("Warnings do not block running the scenario."),
    ).toBeVisible();
    await expect(
      inspector(page).getByText(
        "Tasks published to RabbitMQ accumulate because no Worker consumes them",
      ),
    ).toBeVisible();
    await expect(
      inspector(page).getByText(
        "The scenario has no load, so the experiment receives no traffic",
      ),
    ).toBeVisible();
    await expect(node(page, "RabbitMQ")).toContainText("1 warning");
    await testInfo.attach("scenario-warning.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });
});
