import { afterEach, describe, expect, it, vi } from "vitest";

import {
  getGraph,
  saveGraph,
} from "~/features/scenario-canvas/api/graph-service";
import { HttpError } from "~/shared/api/api-fetch";

const graph = { revision: 2, components: [], issues: [] };

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

describe("graph service", () => {
  it("reads the graph of a scenario", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(graph));
    vi.stubGlobal("fetch", fetch);

    await expect(getGraph("7", "3")).resolves.toEqual(graph);
    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      "/api/v1/projects/7/scenarios/3/graph",
      expect.anything(),
    );
  });

  it("saves the whole graph with the expected revision", async () => {
    document.cookie = "XSRF-TOKEN=token";
    const fetch = vi.fn().mockResolvedValue(Response.json(graph));
    vi.stubGlobal("fetch", fetch);
    const body = { revision: 1, components: [] };

    await expect(saveGraph("7", "3", body)).resolves.toEqual(graph);
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/projects/7/scenarios/3/graph");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual(body);
  });

  it("rejects a stale revision with the conflict status and message", async () => {
    document.cookie = "XSRF-TOKEN=token";
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 409, detail: "Reload it." }), {
          status: 409,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    const error = await saveGraph("7", "3", {
      revision: 1,
      components: [],
    }).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({ status: 409, message: "Reload it." });
  });
});
