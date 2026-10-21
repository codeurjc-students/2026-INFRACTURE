import { act, fireEvent, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  type RouteObject,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import { HydrateFallback } from "~/root";
import ComponentCatalogueRoute, { clientLoader, ErrorBoundary } from "~/routes/component-catalogue";
import type { PageDTO } from "~/shared/model/PageDTO";

const componentTemplates: ComponentTemplateDTO[] = [
  {
    key: "http-service",
    name: "HTTP Service",
    type: "HTTP_SERVICE",
  },
  {
    key: "postgresql",
    name: "PostgreSQL",
    type: "POSTGRESQL",
  },
];

const catalogue: PageDTO<ComponentTemplateDTO> = {
  content: componentTemplates,
  page: { size: 10, number: 0, totalElements: 2, totalPages: 1 },
};
const firstPage: PageDTO<ComponentTemplateDTO> = {
  content: componentTemplates,
  page: { size: 2, number: 0, totalElements: 3, totalPages: 2 },
};
const nextPage: PageDTO<ComponentTemplateDTO> = {
  content: [{ key: "worker", name: "Worker", type: "WORKER" }],
  page: { size: 2, number: 1, totalElements: 3, totalPages: 2 },
};

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function CatalogueTestRoute() {
  const loaderData = useLoaderData() as Awaited<ReturnType<typeof clientLoader>>;

  return <ComponentCatalogueRoute loaderData={loaderData} />;
}

function renderCatalogueRoute() {
  const routes: RouteObject[] = [
    {
      path: "/",
      loader: clientLoader,
      Component: CatalogueTestRoute,
      ErrorBoundary,
      HydrateFallback,
    },
  ];
  const router = createMemoryRouter(routes, { initialEntries: ["/"] });
  activeRouters.push(router);

  render(<RouterProvider router={router} />);
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });

  return { promise, resolve, reject };
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.unstubAllGlobals();
});

describe("component catalogue route", () => {
  it("shows the loading state before the catalogue request resolves", async () => {
    const request = deferred<Response>();
    const fetch = vi.fn().mockReturnValue(request.promise);
    vi.stubGlobal("fetch", fetch);

    renderCatalogueRoute();

    expect(screen.getByRole("status")).toHaveTextContent("Loading…");

    await act(async () => {
      request.resolve(Response.json(catalogue));
    });

    expect(
      await screen.findByRole("heading", { name: "HTTP Service" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("HTTP_SERVICE")).toBeInTheDocument();
    expect(screen.getByText("http-service")).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      "/api/v1/component-templates?page=0",
      expect.anything(),
    );
    expect(screen.queryByRole("button", { name: "More results" })).not.toBeInTheDocument();
  });

  it("renders the empty state when the HTTP response contains no templates", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({
      content: [],
      page: { size: 10, number: 0, totalElements: 0, totalPages: 0 },
    })));

    renderCatalogueRoute();

    expect(
      await screen.findByText("No component templates are currently available."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "More results" })).not.toBeInTheDocument();
  });

  it("shows an initial HTTP error and allows retrying", async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockResolvedValueOnce(Response.json(catalogue));
    vi.stubGlobal("fetch", fetch);

    renderCatalogueRoute();

    expect(
      await screen.findByRole("heading", {
        name: "Component catalogue unavailable",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "The catalogue could not be loaded. Please try again.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("heading", { name: "HTTP Service" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("keeps cards while loading, deduplicates and stops at the final page", async () => {
    const request = deferred<Response>();
    const fetch = vi.fn()
      .mockResolvedValueOnce(Response.json(firstPage))
      .mockReturnValueOnce(request.promise);
    vi.stubGlobal("fetch", fetch);

    renderCatalogueRoute();
    fireEvent.click(await screen.findByRole("button", { name: "More results" }));

    const loadingButton = screen.getByRole("button", { name: "Loading…" });
    expect(loadingButton).toBeDisabled();
    fireEvent.click(loadingButton);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("heading", { name: "HTTP Service" })).toBeInTheDocument();

    await act(async () => request.resolve(Response.json({
      ...nextPage,
      content: [componentTemplates[1], ...nextPage.content],
    })));

    expect(await screen.findByRole("heading", { name: "Worker" })).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
    expect(screen.getAllByRole("heading", { name: "PostgreSQL" })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: "More results" })).not.toBeInTheDocument();
    expect(fetch).toHaveBeenLastCalledWith(
      "/api/v1/component-templates?page=1",
      expect.anything(),
    );
  });
});
