import { fireEvent, render, screen } from "@testing-library/react";
import { afterAll, beforeAll, expect, it, vi } from "vitest";

import ComponentCatalogueRoute, { clientLoader } from "~/routes/component-catalogue";
import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";

const nodeFetch = globalThis.fetch;

beforeAll(() => {
  // Resolve browser-relative paths for Node while keeping real HTTP requests.
  vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) =>
    nodeFetch(typeof input === "string" ? new URL(input, window.location.origin) : input, init),
  );
});

afterAll(() => vi.unstubAllGlobals());

it("renders catalogue data persisted by the real backend", async () => {
  const loaderData = await clientLoader();

  expect(
    loaderData.componentTemplates.content,
    "Contract failure: the API did not return the seeded HTTP Service template",
  ).toEqual(
    expect.arrayContaining([
      expect.objectContaining({
        key: "http-service",
        name: "HTTP Service",
        type: "HTTP_SERVICE",
      }),
    ]),
  );

  expect(loaderData.componentTemplates.content).toHaveLength(10);
  expect(loaderData.componentTemplates.page)
    .toEqual({ size: 10, number: 0, totalElements: 11, totalPages: 2 });

  render(<ComponentCatalogueRoute loaderData={loaderData} />);

  expect(
    screen.getByRole("heading", { name: "HTTP Service" }),
    "Rendering failure: the persisted template did not reach the catalogue view",
  ).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "More results" }));
  expect(await screen.findByRole("heading", { name: "Worker variant 6" })).toBeInTheDocument();
  expect(screen.getAllByRole("listitem")).toHaveLength(11);
  expect(screen.queryByRole("button", { name: "More results" })).not.toBeInTheDocument();
});

it("rejects an invalid page through the real API", async () => {
  await expect(getComponentTemplates(-1)).rejects.toMatchObject({ status: 400 });
});
