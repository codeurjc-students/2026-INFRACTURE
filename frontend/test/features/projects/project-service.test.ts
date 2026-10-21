import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  createProject,
  getProject,
  getProjects,
  updateProject,
} from "~/features/projects/api/project-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import { HttpError } from "~/shared/api/api-fetch";

const project: ProjectDTO = {
  id: 7,
  name: "Cache lab",
  description: "Redis in front of PostgreSQL.",
  createdAt: "2026-10-20T10:00:00Z",
  updatedAt: "2026-10-20T10:00:00Z",
};

const details = {
  name: "Cache lab",
  description: "Redis in front of PostgreSQL.",
};

function requestOf(fetch: ReturnType<typeof vi.fn>) {
  const [path, init = {}] = fetch.mock.calls[0] as [string, RequestInit?];
  return { path, method: init.method ?? "GET", body: init.body };
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=token-123";
});

afterEach(() => {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  vi.unstubAllGlobals();
});

describe("project service", () => {
  it("reads the requested page of own projects", async () => {
    const page = {
      content: [project],
      page: { size: 10, totalElements: 11, totalPages: 2, number: 1 },
    };
    const fetch = vi.fn().mockResolvedValue(Response.json(page));
    vi.stubGlobal("fetch", fetch);

    await expect(getProjects(1)).resolves.toEqual(page);

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/projects?page=1",
      method: "GET",
      body: undefined,
    });
  });

  it("reads one project by its id", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(project));
    vi.stubGlobal("fetch", fetch);

    await expect(getProject("7")).resolves.toEqual(project);

    expect(requestOf(fetch).path).toBe("/api/v1/projects/7");
  });

  it("creates a project with its name and description", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(Response.json(project, { status: 201 }));
    vi.stubGlobal("fetch", fetch);

    await expect(createProject(details)).resolves.toEqual(project);

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/projects",
      method: "POST",
      body: JSON.stringify(details),
    });
  });

  it("replaces the name and description of a project", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(project));
    vi.stubGlobal("fetch", fetch);

    await expect(updateProject("7", details)).resolves.toEqual(project);

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/projects/7",
      method: "PUT",
      body: JSON.stringify(details),
    });
  });

  it("reports a project that does not exist for this account", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          Response.json(
            { status: 404, detail: "Project not found" },
            {
              status: 404,
              headers: { "Content-Type": "application/problem+json" },
            },
          ),
        ),
    );

    await expect(getProject("99")).rejects.toEqual(
      new HttpError(404, "Project not found"),
    );
  });
});
