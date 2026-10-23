import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  createScenario,
  getScenario,
  getScenarios,
  updateScenario,
} from "~/features/projects/api/scenario-service";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";

const scenario: ScenarioDTO = {
  id: 3,
  name: "Cold cache",
  description: null,
  revision: 1,
  createdAt: "2026-10-20T10:00:00Z",
  updatedAt: "2026-10-20T10:00:00Z",
};

const details = { name: "Cold cache", description: "" };

function requests(fetch: ReturnType<typeof vi.fn>) {
  return fetch.mock.calls
    .map(([path, init = {}]) => ({
      path,
      method: (init as RequestInit).method ?? "GET",
      body: (init as RequestInit).body,
    }))
    .filter(({ path }) => path !== "/api/v1/auth/csrf");
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=token-123";
});

afterEach(() => {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  vi.unstubAllGlobals();
});

describe("scenario service", () => {
  it("reads scenarios under their project", async () => {
    const page = {
      content: [scenario],
      page: { size: 10, totalElements: 11, totalPages: 2, number: 1 },
    };
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(Response.json(page))
      .mockResolvedValueOnce(Response.json(scenario));
    vi.stubGlobal("fetch", fetch);

    await expect(getScenarios("7", 1)).resolves.toEqual(page);
    await expect(getScenario("7", "3")).resolves.toEqual(scenario);

    expect(requests(fetch).map(({ path }) => path)).toEqual([
      "/api/v1/projects/7/scenarios?page=1",
      "/api/v1/projects/7/scenarios/3",
    ]);
  });

  it("creates and renames scenarios with their name and description", async () => {
    const fetch = vi
      .fn()
      .mockImplementation(() => Promise.resolve(Response.json(scenario)));
    vi.stubGlobal("fetch", fetch);

    await expect(createScenario("7", details)).resolves.toEqual(scenario);
    await expect(updateScenario("7", "3", details)).resolves.toEqual(scenario);

    expect(requests(fetch)).toEqual([
      {
        path: "/api/v1/projects/7/scenarios",
        method: "POST",
        body: JSON.stringify(details),
      },
      {
        path: "/api/v1/projects/7/scenarios/3",
        method: "PUT",
        body: JSON.stringify(details),
      },
    ]);
  });
});
