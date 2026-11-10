import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

import {
  getCurrentExecution,
  runScenario,
  stopExecution,
} from "~/features/scenario-canvas/api/execution-service";
import { saveGraph } from "~/features/scenario-canvas/api/graph-service";
import type { ExecutionDTO } from "~/features/scenario-canvas/model/ExecutionDTO";
import { clientAction as createAction } from "~/routes/new-project";
import { clientAction as createScenarioAction } from "~/routes/new-scenario";
import { clientAction as registerAction } from "~/routes/register";
import { clientLoader as scenarioLoader } from "~/routes/scenario";
import { HttpError } from "~/shared/api/api-fetch";

// The real backend runs the experiment with the real Docker engine, so the test always ends with
// a released execution: the instance admits one execution at a time and the containers must go.
const nodeFetch = globalThis.fetch;
const cookies = new Map<string, string>();
const suffix = Date.now().toString(36);

function account(name: string) {
  return {
    username: `${name}_${suffix}`,
    displayName: `Projects ${name}`,
    email: `${name}.${suffix}@example.test`,
    password: "A secure integration password 2026!",
  };
}

beforeAll(() => {
  // Node has no cookie jar: keep the session and CSRF cookies between requests.
  vi.stubGlobal(
    "fetch",
    async (input: RequestInfo | URL, init: RequestInit = {}) => {
      const headers = new Headers(init.headers);
      if (cookies.size > 0) {
        headers.set(
          "Cookie",
          [...cookies].map(([name, value]) => `${name}=${value}`).join("; "),
        );
      }
      const response = await nodeFetch(
        new URL(String(input), window.location.origin),
        { ...init, headers },
      );
      for (const setCookie of response.headers.getSetCookie()) {
        const pair = setCookie.split(";")[0];
        const name = pair.slice(0, pair.indexOf("="));
        const value = pair.slice(pair.indexOf("=") + 1);
        if (value) cookies.set(name, value);
        else cookies.delete(name);
        if (!/httponly/i.test(setCookie)) document.cookie = setCookie;
      }
      return response;
    },
  );
});

let projectId = "";
let scenarioId = "";
let current: ExecutionDTO | null = null;

// Waits for the reconciler (every 2 s) to clean up and release the execution.
async function released() {
  for (let attempt = 0; attempt < 90; attempt++) {
    current = await getCurrentExecution(projectId, scenarioId);
    if (current?.phase === "RELEASED") return current;
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error("The execution was not released within 3 minutes");
}

afterAll(async () => {
  // A failed assertion must not leave containers behind or the instance reserved.
  if (current && current.phase !== "RELEASED") {
    await stopExecution(projectId, scenarioId, current.id).catch(() => null);
    await released().catch(() => null);
  }
  vi.unstubAllGlobals();
});

function submit(path: string, fields: Record<string, string>) {
  const body = new FormData();
  Object.entries(fields).forEach(([name, value]) => body.set(name, value));
  return {
    request: new Request(new URL(path, window.location.origin), {
      method: "POST",
      body,
    }),
  };
}

async function signUp(name: string) {
  const registered = await registerAction(
    submit("/register", account(name)) as never,
  );
  expect((registered as Response).headers.get("Location")).toBe("/");
}

async function create(name: string, description = "") {
  const created = (await createAction(
    submit("/projects/new", { name, description }) as never,
  )) as Response;
  const location = created.headers.get("Location")!;
  expect(location).toMatch(/^\/projects\/\d+$/);
  return location.split("/").pop()!;
}

async function createScenario(projectId: string, name: string) {
  const created = (await createScenarioAction({
    ...submit(`/projects/${projectId}/scenarios/new`, {
      name,
      description: "",
    }),
    params: { projectId },
  } as never)) as Response;
  const location = created.headers.get("Location")!;
  expect(location).toMatch(
    new RegExp(`^/projects/${projectId}/scenarios/\\d+$`),
  );
  return location.split("/").pop()!;
}

describe("execution client against the real API", () => {
  it("runs the saved revision, keeps the scenario read-only and stops it", async () => {
    await signUp("runner");
    projectId = await create("Run lab");
    scenarioId = await createScenario(projectId, "Stateless");
    const graph = {
      revision: 1,
      components: [
        {
          key: crypto.randomUUID(),
          templateKey: "http-service",
          name: "orders-api",
          position: { x: 0, y: 0 },
          profile: "STATELESS",
          parameters: { responseDelayMs: 0 },
        },
      ],
      connections: [],
      workload: null,
    };
    const saved = await saveGraph(projectId, scenarioId, graph);
    await expect(
      getCurrentExecution(projectId, scenarioId),
    ).resolves.toBeNull();

    current = await runScenario(projectId, scenarioId, saved.revision);
    expect(current).toMatchObject({
      scenarioRevision: saved.revision,
      stopCause: null,
      components: [{ name: "orders-api", type: "HTTP_SERVICE" }],
    });
    const again = await runScenario(projectId, scenarioId, saved.revision);
    expect(again.id).toBe(current.id);
    await expect(
      scenarioLoader({ params: { projectId, scenarioId } } as never),
    ).resolves.toMatchObject({ execution: { id: current.id } });

    const edit = await saveGraph(projectId, scenarioId, {
      ...graph,
      revision: saved.revision,
    }).catch((caught: unknown) => caught);
    expect(edit).toBeInstanceOf(HttpError);
    expect(edit).toMatchObject({ status: 409 });

    current = await stopExecution(projectId, scenarioId, current.id);
    expect(current).toMatchObject({
      stopCause: "STOP_REQUESTED",
      cleanupFailures: 0,
      cleanupError: null,
      cleanupRetryNeeded: false,
    });
    await expect(released()).resolves.toMatchObject({
      phase: "RELEASED",
      outcome: "COMPLETED",
      cleanupFailures: 0,
    });
    await expect(
      saveGraph(projectId, scenarioId, { ...graph, revision: saved.revision }),
    ).resolves.toMatchObject({ revision: saved.revision + 1 });
  }, 240_000);
});
