import { afterEach, describe, expect, it, vi } from "vitest";

import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import { template } from "../../../fixtures/component-templates";
import { HttpError } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";

const componentTemplates: ComponentTemplateDTO[] = [
  template("http-service", "HTTP Service", "HTTP_SERVICE"),
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
    const fetch = vi.fn().mockResolvedValue(Response.json(catalogue));
    vi.stubGlobal("fetch", fetch);

    await expect(getComponentTemplates()).resolves.toEqual(catalogue);

    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      "/api/v1/component-templates?page=0",
      expect.anything(),
    );
  });

  it("asks for a page size when one is given", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(catalogue));
    vi.stubGlobal("fetch", fetch);

    await getComponentTemplates(0, 100);

    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      "/api/v1/component-templates?page=0&size=100",
      expect.anything(),
    );
  });

  it("rejects an unsuccessful response with the ProblemDetail message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 400, detail: "Invalid page" }), {
          status: 400,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    const error = await getComponentTemplates(-1).catch((e) => e);

    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({ status: 400, message: "Invalid page" });
  });
});
