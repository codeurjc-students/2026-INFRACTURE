import { afterEach, describe, expect, it, vi } from "vitest";

import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { PageDTO } from "~/shared/model/PageDTO";

const componentTemplates: ComponentTemplateDTO[] = [
  {
    key: "http-service",
    name: "HTTP Service",
    type: "HTTP_SERVICE",
  },
];

const catalogue: PageDTO<ComponentTemplateDTO> = {
  content: componentTemplates,
  page: { size: 10, number: 0, totalElements: 1, totalPages: 1 },
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("component template service", () => {
  it("returns the component templates from a successful response", async () => {
    const json = vi.fn().mockResolvedValue(catalogue);
    const fetch = vi.fn().mockResolvedValue({ ok: true, json });
    vi.stubGlobal("fetch", fetch);

    await expect(getComponentTemplates()).resolves.toEqual(catalogue);

    expect(fetch).toHaveBeenCalledExactlyOnceWith("/api/v1/component-templates?page=0");
    expect(json).toHaveBeenCalledOnce();
  });

  it("rejects an unsuccessful HTTP response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));

    await expect(getComponentTemplates()).rejects.toThrow(
      "Failed to fetch component templates",
    );
  });

});
