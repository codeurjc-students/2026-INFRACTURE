import { afterEach, describe, expect, it, vi } from "vitest";

import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";

const componentTemplates: ComponentTemplateDTO[] = [
  {
    key: "http-service",
    name: "HTTP Service",
    type: "HTTP_SERVICE",
  },
];

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("component template service", () => {
  it("returns the component templates from a successful response", async () => {
    const json = vi.fn().mockResolvedValue(componentTemplates);
    const fetch = vi.fn().mockResolvedValue({ ok: true, json });
    vi.stubGlobal("fetch", fetch);

    await expect(getComponentTemplates()).resolves.toEqual(componentTemplates);

    const requestedUrl = fetch.mock.calls[0][0] as URL;
    expect(requestedUrl.pathname).toBe("/api/v1/component-templates");
    expect(json).toHaveBeenCalledOnce();
  });

  it("rejects an unsuccessful HTTP response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));

    await expect(getComponentTemplates()).rejects.toThrow(
      "Failed to fetch component templates",
    );
  });

  it("preserves the cause when the API cannot be reached", async () => {
    const cause = new TypeError("Network unavailable");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(cause));

    await expect(getComponentTemplates()).rejects.toMatchObject({
      message: "Could not connect to the component catalogue API",
      cause,
    });
  });
});
