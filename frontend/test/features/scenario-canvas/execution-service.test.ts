import { afterEach, describe, expect, it, vi } from "vitest";

import {
  getCurrentExecution,
  runScenario,
  stopExecution,
} from "~/features/scenario-canvas/api/execution-service";
import { HttpError } from "~/shared/api/api-fetch";

const execution = { id: 12, phase: "RESERVED", components: [] };

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

describe("execution service", () => {
  it("runs the saved revision", async () => {
    document.cookie = "XSRF-TOKEN=token";
    const fetch = vi
      .fn()
      .mockResolvedValue(Response.json(execution, { status: 202 }));
    vi.stubGlobal("fetch", fetch);

    await expect(runScenario("7", "3", 2)).resolves.toEqual(execution);
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/projects/7/scenarios/3/executions");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ revision: 2 });
  });

  it("reads the latest execution of the scenario", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(execution));
    vi.stubGlobal("fetch", fetch);

    await expect(getCurrentExecution("7", "3")).resolves.toEqual(execution);
    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      "/api/v1/projects/7/scenarios/3/executions/current",
      expect.anything(),
    );
  });

  it("returns null when the scenario has never run", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    );

    await expect(getCurrentExecution("7", "3")).resolves.toBeNull();
  });

  it("asks to stop an execution", async () => {
    document.cookie = "XSRF-TOKEN=token";
    const fetch = vi
      .fn()
      .mockResolvedValue(Response.json(execution, { status: 202 }));
    vi.stubGlobal("fetch", fetch);

    await stopExecution("7", "3", 12);
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/projects/7/scenarios/3/executions/12/stop");
    expect(init.method).toBe("POST");
  });

  it("rejects a run while another experiment is running", async () => {
    document.cookie = "XSRF-TOKEN=token";
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 409,
            detail: "Another experiment is running.",
          }),
          {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    const error = await runScenario("7", "3", 2).catch(
      (caught: unknown) => caught,
    );
    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({
      status: 409,
      message: "Another experiment is running.",
    });
  });
});
