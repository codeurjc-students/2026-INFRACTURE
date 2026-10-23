import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

import {
  getProject,
  getProjects,
} from "~/features/projects/api/project-service";
import { getScenarios } from "~/features/projects/api/scenario-service";
import { clientAction as logoutAction } from "~/routes/logout";
import { clientAction as createAction } from "~/routes/new-project";
import {
  clientAction as createScenarioAction,
  clientLoader as newScenarioLoader,
} from "~/routes/new-scenario";
import {
  clientAction as updateAction,
  clientLoader as projectLoader,
} from "~/routes/project";
import { clientAction as registerAction } from "~/routes/register";
import {
  clientAction as updateScenarioAction,
  clientLoader as scenarioLoader,
} from "~/routes/scenario";

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

afterAll(() => vi.unstubAllGlobals());

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

describe("projects client against the real API", () => {
  let projectId = "";
  let scenarioId = "";

  it("creates, edits and reopens a private project", async () => {
    await signUp("owner");

    projectId = await create("Cache lab", "Redis in front of PostgreSQL.");
    await expect(
      projectLoader({ params: { projectId } } as never),
    ).resolves.toMatchObject({
      project: {
        name: "Cache lab",
        description: "Redis in front of PostgreSQL.",
      },
    });

    await expect(
      updateAction({
        ...submit(`/projects/${projectId}`, {
          name: "Cache drill",
          description: "",
        }),
        params: { projectId },
      } as never),
    ).resolves.toEqual({ saved: true });

    await expect(
      projectLoader({ params: { projectId } } as never),
    ).resolves.toMatchObject({
      project: { name: "Cache drill", description: null },
    });
  });

  it("shows the backend validation message", async () => {
    const result = await createAction(
      submit("/projects/new", {
        name: "x".repeat(31),
        description: "",
      }) as never,
    );

    expect(result).toEqual({
      error: "Project name must contain at most 30 characters",
    });
  });

  it("lists more than ten projects in pages, newest first", async () => {
    for (let index = 1; index <= 10; index++) {
      await create(`Project ${index}`);
    }

    const first = await getProjects();
    const second = await getProjects(1);

    expect(first.content).toHaveLength(10);
    expect(first.content[0].name).toBe("Project 10");
    expect(first.page).toMatchObject({
      number: 0,
      totalElements: 11,
      totalPages: 2,
    });
    expect(second.content.map((project) => project.name)).toEqual([
      "Cache drill",
    ]);
  });

  it("creates, renames and reopens empty scenarios in the project", async () => {
    scenarioId = await createScenario(projectId, "Cold cache");
    const otherId = await createScenario(projectId, "Warm cache");

    await expect(
      updateScenarioAction({
        ...submit(`/projects/${projectId}/scenarios/${scenarioId}`, {
          name: "Cold start",
          description: "Empty Redis.",
        }),
        params: { projectId, scenarioId },
      } as never),
    ).resolves.toEqual({ saved: true });

    await expect(
      scenarioLoader({ params: { projectId, scenarioId } } as never),
    ).resolves.toMatchObject({
      scenario: {
        name: "Cold start",
        description: "Empty Redis.",
        revision: 1,
      },
    });
    await expect(
      scenarioLoader({ params: { projectId, scenarioId: otherId } } as never),
    ).resolves.toMatchObject({ scenario: { name: "Warm cache" } });
    const scenarios = await getScenarios(projectId);
    expect(scenarios.content.map((scenario) => scenario.name)).toEqual([
      "Warm cache",
      "Cold start",
    ]);
  });

  it("treats another account's project as not found", async () => {
    await logoutAction();
    await signUp("visitor");

    await expect(
      projectLoader({ params: { projectId } } as never),
    ).rejects.toMatchObject({
      init: { status: 404 },
    });
    await expect(
      newScenarioLoader({ params: { projectId } } as never),
    ).rejects.toMatchObject({ init: { status: 404 } });
    await expect(
      scenarioLoader({ params: { projectId, scenarioId } } as never),
    ).rejects.toMatchObject({ init: { status: 404 } });
    await expect(getProject(String(projectId))).rejects.toMatchObject({
      status: 404,
      message: "Project not found",
    });
    await expect(getProjects()).resolves.toMatchObject({
      page: { totalElements: 0 },
    });
  });
});
