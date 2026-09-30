import { act, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  type RouteObject,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { HydrateFallback } from "~/root";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import Home, { clientLoader, ErrorBoundary } from "~/routes/home";

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

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function CatalogueTestRoute() {
  const loaderData = useLoaderData() as Awaited<ReturnType<typeof clientLoader>>;

  return <Home loaderData={loaderData} />;
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
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(request.promise));

    renderCatalogueRoute();

    expect(screen.getByRole("status")).toHaveTextContent(
      "Loading component catalogue…",
    );

    await act(async () => {
      request.resolve(Response.json(componentTemplates));
    });

    expect(
      await screen.findByRole("heading", { name: "Component catalogue" }),
    ).toBeInTheDocument();
  });

  it("renders the component templates returned through the real HTTP client", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(componentTemplates));
    vi.stubGlobal("fetch", fetch);

    renderCatalogueRoute();

    expect(
      await screen.findByRole("heading", { name: "HTTP Service" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "PostgreSQL" }),
    ).toBeInTheDocument();
    expect(screen.getByText("HTTP_SERVICE")).toBeInTheDocument();
    expect(screen.getByText("http-service")).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      new URL("/api/v1/component-templates", window.location.origin),
    );
  });

  it("renders the empty state when the HTTP response contains no templates", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json([])));

    renderCatalogueRoute();

    expect(
      await screen.findByText("No component templates are currently available."),
    ).toBeInTheDocument();
  });

  it.each([
    ["HTTP failure", () => Promise.resolve(new Response(null, { status: 503 }))],
    ["network failure", () => Promise.reject(new TypeError("Network unavailable"))],
  ])("renders the route error state after a %s", async (_failure, respond) => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(respond));

    renderCatalogueRoute();

    expect(
      await screen.findByRole("heading", {
        name: "Component catalogue unavailable",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Check that PostgreSQL and the backend are running, then reload this page.",
    );
  });
});
