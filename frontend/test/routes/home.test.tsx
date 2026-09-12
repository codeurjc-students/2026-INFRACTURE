import { act, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  type RouteObject,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { HydrateFallback } from "~/root";
import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import Home, { clientLoader, ErrorBoundary } from "~/routes/home";

vi.mock(
  "~/features/component-catalogue/api/component-template-service",
  () => ({
    getComponentTemplates: vi.fn(),
  }),
);

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
  vi.clearAllMocks();
});

describe("component catalogue route", () => {
  it("shows the loading state before the catalogue request resolves", async () => {
    const request = deferred<ComponentTemplateDTO[]>();
    vi.mocked(getComponentTemplates).mockReturnValue(request.promise);

    renderCatalogueRoute();

    expect(screen.getByRole("status")).toHaveTextContent(
      "Loading component catalogue…",
    );

    await act(async () => {
      request.resolve(componentTemplates);
    });

    expect(
      await screen.findByRole("heading", { name: "Component catalogue" }),
    ).toBeInTheDocument();
  });

  it("renders the component templates returned by the HTTP boundary", async () => {
    vi.mocked(getComponentTemplates).mockResolvedValue(componentTemplates);

    renderCatalogueRoute();

    expect(
      await screen.findByRole("heading", { name: "HTTP Service" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "PostgreSQL" }),
    ).toBeInTheDocument();
    expect(screen.getByText("HTTP_SERVICE")).toBeInTheDocument();
    expect(screen.getByText("http-service")).toBeInTheDocument();
    expect(getComponentTemplates).toHaveBeenCalledOnce();
  });

  it("renders the route error state when the HTTP boundary rejects", async () => {
    vi.mocked(getComponentTemplates).mockRejectedValue(
      new Error("Catalogue request failed"),
    );

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
