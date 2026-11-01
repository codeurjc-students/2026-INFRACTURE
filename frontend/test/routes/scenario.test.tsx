import "../react-flow-mocks";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import {
  createMemoryRouter,
  Outlet,
  RouterProvider,
  useLoaderData,
} from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";

import { getProject } from "~/features/projects/api/project-service";
import {
  getScenario,
  updateScenario,
} from "~/features/projects/api/scenario-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";
import {
  getGraph,
  saveGraph,
} from "~/features/scenario-canvas/api/graph-service";
import {
  draftKey,
  readDraft,
  writeDraft,
} from "~/features/scenario-canvas/draft-storage";
import type { ScenarioGraphDTO } from "~/features/scenario-canvas/model/ScenarioGraphDTO";
import ScenarioRoute, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "~/routes/scenario";
import { HttpError } from "~/shared/api/api-fetch";
import { httpService, redis } from "../fixtures/component-templates";

vi.mock("~/features/projects/api/project-service");
vi.mock("~/features/projects/api/scenario-service");
vi.mock("~/features/scenario-canvas/api/graph-service");
vi.mock("~/features/component-catalogue/api/component-template-service");

const project = { id: 7, name: "Cache lab" } as ProjectDTO;
const scenario: ScenarioDTO = {
  id: 3,
  name: "Cold cache",
  description: "Empty Redis.",
  revision: 1,
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-20T10:00:00Z",
};

const emptyGraph: ScenarioGraphDTO = {
  revision: 1,
  components: [],
  connections: [],
  issues: [],
};
const savedGraph: ScenarioGraphDTO = {
  revision: 2,
  components: [
    {
      key: "11111111-1111-4111-8111-111111111111",
      templateKey: "http-service",
      type: "HTTP_SERVICE",
      name: "Orders API",
      position: { x: 0, y: 0 },
      profile: "STATELESS",
      parameters: { responseDelayMs: 9000 },
    },
  ],
  connections: [],
  issues: [
    {
      severity: "ERROR",
      code: "PARAMETER_OUT_OF_RANGE",
      message: "responseDelayMs must be between 0 and 5000",
      target: {
        kind: "COMPONENT",
        key: "11111111-1111-4111-8111-111111111111",
      },
      field: "responseDelayMs",
    },
  ],
};

// Orders API uses the cache port of CACHE_ASIDE; with STATELESS that port no longer exists.
function connectedGraph(profile: string): ScenarioGraphDTO {
  return {
    revision: 2,
    components: [
      {
        key: "11111111-1111-4111-8111-111111111111",
        templateKey: "http-service",
        type: "HTTP_SERVICE",
        name: "Orders API",
        position: { x: 0, y: 0 },
        profile,
        parameters: { responseDelayMs: 0 },
      },
      {
        key: "22222222-2222-4222-8222-222222222222",
        templateKey: "redis",
        type: "REDIS",
        name: "orders-cache",
        position: { x: 400, y: 0 },
        profile: "DEFAULT",
        parameters: {},
      },
    ],
    connections: [
      {
        sourceKey: "11111111-1111-4111-8111-111111111111",
        port: "cache",
        targetKey: "22222222-2222-4222-8222-222222222222",
      },
    ],
    issues: [],
  };
}

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function ScenarioTestRoute() {
  const loaderData = useLoaderData() as Awaited<
    ReturnType<typeof clientLoader>
  >;

  return <ScenarioRoute loaderData={loaderData} />;
}

// With a user id, the scenario sits under the private layout that loads the signed-in account.
function renderScenario(id = "3", userId?: number) {
  const scenarioRoute = {
    path: "/projects/:projectId/scenarios/:scenarioId",
    loader: clientLoader as never,
    action: clientAction as never,
    Component: ScenarioTestRoute,
    ErrorBoundary,
  };
  const routes =
    userId === undefined
      ? [scenarioRoute]
      : [
          {
            id: "routes/private-layout",
            loader: () => ({ user: { id: userId } }),
            Component: Outlet,
            children: [scenarioRoute],
          },
        ];
  const router = createMemoryRouter(
    [
      ...routes,
      { path: "/catalogue", Component: () => <p>Catalogue page</p> },
      { path: "/login", Component: () => <p>Login page</p> },
    ],
    { initialEntries: [`/projects/7/scenarios/${id}`] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

beforeEach(() => {
  vi.mocked(getGraph).mockResolvedValue(emptyGraph);
  vi.mocked(getComponentTemplates).mockResolvedValue({
    content: [httpService, redis],
    page: { size: 100, number: 0, totalElements: 2, totalPages: 1 },
  });
});

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
  localStorage.clear();
});

describe("scenario route", () => {
  it("identifies the open scenario and starts with an empty canvas", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    renderScenario();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Cold cache" }),
    ).toBeInTheDocument();
    const breadcrumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(breadcrumb).toHaveTextContent("Cache lab/Cold cache");
    expect(
      within(breadcrumb).getByRole("link", { name: "Cache lab" }),
    ).toHaveAttribute("href", "/projects/7");
    fireEvent.click(screen.getByRole("button", { name: "Details" }));
    expect(await screen.findByLabelText("Description")).toHaveValue(
      "Empty Redis.",
    );
    expect(screen.getByText("Scenario, revision 1")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "No components yet" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Graph saved")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Saved" })).toBeDisabled();
    expect(getScenario).toHaveBeenCalledWith("7", "3");
    expect(getGraph).toHaveBeenCalledWith("7", "3");
    expect(getComponentTemplates).toHaveBeenCalledWith(0, 100);
  });

  it("adds a node from the palette, edits it and saves the graph", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph).mockImplementation(
      async (_project, _scenario, graph) => ({
        revision: 2,
        components: graph.components.map((component) => ({
          ...component,
          type: "HTTP_SERVICE",
        })),
        connections: graph.connections,
        issues: [],
      }),
    );
    renderScenario();

    fireEvent.click(
      await screen.findByRole("button", { name: "Add HTTP Service" }),
    );
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    fireEvent.change(within(inspector).getByLabelText("Name"), {
      target: { value: "Orders API" },
    });
    fireEvent.change(within(inspector).getByLabelText("responseDelayMs"), {
      target: { value: "2.5" },
    });
    expect(
      within(inspector).getByText("Use a whole number."),
    ).toBeInTheDocument();
    const delay = within(inspector).getByLabelText("responseDelayMs");
    fireEvent.blur(delay);
    expect(delay).toHaveValue(0);
    fireEvent.change(delay, { target: { value: "9000" } });
    expect(
      within(inspector).getByText("Use a value from 0 to 5000."),
    ).toBeInTheDocument();
    fireEvent.blur(delay);
    expect(delay).toHaveValue(5000);
    fireEvent.change(delay, { target: { value: "" } });
    fireEvent.blur(delay);
    expect(delay).toHaveValue(0);
    fireEvent.change(delay, { target: { value: "250" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Graph saved")).toBeInTheDocument();
    expect(screen.getByText("Revision 2")).toBeInTheDocument();
    const sent = vi.mocked(saveGraph).mock.calls[0];
    expect(sent[0]).toBe("7");
    expect(sent[1]).toBe("3");
    expect(sent[2]).toMatchObject({
      revision: 1,
      components: [
        {
          templateKey: "http-service",
          name: "Orders API",
          profile: "STATELESS",
          parameters: { responseDelayMs: 250 },
        },
      ],
    });
    expect(sent[2].components[0].key).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("shows saved nodes with their contract issues", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue(savedGraph);
    renderScenario();

    expect(await screen.findByText("Orders API")).toBeInTheDocument();
    expect(screen.getByText("1 issue to review")).toBeInTheDocument();
    expect(
      screen.queryByRole("complementary", { name: "Inspector" }),
    ).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "1 issue" }));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(within(inspector).getByLabelText("Name")).toHaveValue("Orders API");
    expect(
      within(inspector).getByText("Use a value from 0 to 5000."),
    ).toBeInTheDocument();
    fireEvent.click(
      within(inspector).getByRole("button", { name: "Close configuration" }),
    );
    expect(
      screen.queryByRole("complementary", { name: "Inspector" }),
    ).toBeNull();
    expect(
      screen.queryByRole("heading", { name: "No components yet" }),
    ).toBeNull();
  });

  it("draws saved connections and lists them in the inspector", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue(connectedGraph("CACHE_ASIDE"));
    renderScenario();

    expect(
      await screen.findByLabelText("Orders API Cache orders-cache"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByText("Orders API"));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(
      within(inspector).getByRole("heading", { name: "Connections" }),
    ).toBeInTheDocument();
    expect(
      within(inspector).getByText("Cache → orders-cache"),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByText("orders-cache"));
    expect(
      within(
        screen.getByRole("complementary", { name: "Inspector" }),
      ).getByText("Orders API → Cache"),
    ).toBeInTheDocument();
  });

  it("keeps a connection the profile no longer allows, flags it and removes it", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue(connectedGraph("STATELESS"));
    vi.mocked(saveGraph).mockImplementation(
      async (_project, _scenario, graph) => ({
        ...connectedGraph("STATELESS"),
        revision: 3,
        connections: graph.connections,
      }),
    );
    renderScenario();

    expect(
      await screen.findByLabelText(
        "Orders API cache orders-cache, not allowed",
      ),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByText("Orders API"));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(
      within(inspector).getByText(
        "Orders API has no port cache in profile STATELESS",
      ),
    ).toBeInTheDocument();

    fireEvent.click(
      within(inspector).getByRole("button", {
        name: "Remove connection cache → orders-cache",
      }),
    );
    expect(within(inspector).queryByText("cache → orders-cache")).toBeNull();
    expect(screen.getAllByText("Orders API").length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Graph saved")).toBeInTheDocument();
    expect(vi.mocked(saveGraph).mock.calls[0][2]).toMatchObject({
      revision: 2,
      components: [{ name: "Orders API" }, { name: "orders-cache" }],
      connections: [],
    });
  });

  it("saves with the keyboard shortcut and shows where a dragged component lands", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph).mockResolvedValue({ ...emptyGraph, revision: 2 });
    renderScenario();

    const canvas = (
      await screen.findByRole("heading", { name: "No components yet" })
    ).closest(".relative")!;
    fireEvent.dragOver(canvas, {
      dataTransfer: {
        types: ["application/x-infracture-template"],
        dropEffect: "none",
      },
    });
    expect(screen.getByText("Drop to add it here")).toBeInTheDocument();

    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
    expect(saveGraph).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Add Redis" }));
    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
    expect(await screen.findByText("Revision 2")).toBeInTheDocument();
    expect(saveGraph).toHaveBeenCalledTimes(1);
  });

  it("keeps unsaved work in the browser and offers it back on the next visit", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    renderScenario("3", 5);

    fireEvent.click(await screen.findByRole("button", { name: "Add Redis" }));
    expect(readDraft(draftKey(5, 3))).toMatchObject({
      baseRevision: 1,
      components: [{ templateKey: "redis", type: "REDIS", name: "Redis" }],
    });

    activeRouters.splice(0).forEach((router) => router.dispose());
    cleanup();
    renderScenario("3", 5);
    expect(
      await screen.findByText("You have unsaved changes from earlier"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Redis", { selector: "p" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Restore my changes" }));

    expect(
      await screen.findByText("Redis", { selector: "p[title]" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
    expect(
      screen.queryByText("You have unsaved changes from earlier"),
    ).toBeNull();
  });

  it("discards a stored draft when asked and never shows it to another account", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    writeDraft(draftKey(5, 3), {
      baseRevision: 1,
      components: [{ ...savedGraph.components[0], position: { x: 10, y: 10 } }],
      savedAt: "2026-10-31T10:00:00Z",
    });

    renderScenario("3", 6);
    expect(await screen.findByText("Graph saved")).toBeInTheDocument();
    expect(
      screen.queryByText("You have unsaved changes from earlier"),
    ).toBeNull();

    activeRouters.splice(0).forEach((router) => router.dispose());
    cleanup();
    renderScenario("3", 5);
    fireEvent.click(
      await screen.findByRole("button", { name: "Discard them" }),
    );
    expect(
      screen.queryByText("You have unsaved changes from earlier"),
    ).toBeNull();
    expect(readDraft(draftKey(5, 3))).toBeNull();
  });

  it("keeps the work when the session expires and links back after signing in", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph).mockRejectedValue(
      new HttpError(401, "Authentication is required."),
    );
    renderScenario("3", 5);

    fireEvent.click(await screen.findByRole("button", { name: "Add Redis" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("Your session has expired"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign in again" })).toHaveAttribute(
      "href",
      "/login?redirectTo=%2Fprojects%2F7%2Fscenarios%2F3",
    );
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    expect(readDraft(draftKey(5, 3))?.components).toHaveLength(1);
  });

  it("offers back the version discarded after a conflict", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph).mockRejectedValue(
      new HttpError(409, "Saved after revision 1."),
    );
    renderScenario("3", 5);

    fireEvent.click(await screen.findByRole("button", { name: "Add Redis" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    vi.mocked(getGraph).mockResolvedValueOnce(savedGraph);
    fireEvent.click(
      await screen.findByRole("button", { name: "Discard mine and reload" }),
    );

    expect(
      await screen.findByText("You have unsaved changes from earlier"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/The scenario was saved after that \(revision 2\)/),
    ).toBeInTheDocument();
    expect(readDraft(draftKey(5, 3))?.components[0].templateKey).toBe("redis");
  });

  it("keeps local changes when the save fails and explains why", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph).mockRejectedValue(
      new HttpError(400, "Component Redis: name is required"),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Add Redis" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText(
        "Component Redis: name is required Your changes are still here.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });

  it("detects a save from another tab and can save the local graph over it", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph)
      .mockRejectedValueOnce(
        new HttpError(
          409,
          "The scenario was saved after revision 1. Reload it to continue from revision 2.",
        ),
      )
      .mockResolvedValueOnce({ ...savedGraph, revision: 3, issues: [] });
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Add Redis" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("This scenario was saved somewhere else"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    vi.mocked(getGraph).mockResolvedValueOnce(savedGraph);
    fireEvent.click(screen.getByRole("button", { name: "Save mine over it" }));

    expect(await screen.findByText("Revision 3")).toBeInTheDocument();
    expect(vi.mocked(saveGraph).mock.calls[1][2].revision).toBe(2);
    expect(
      vi.mocked(saveGraph).mock.calls[1][2].components[0].templateKey,
    ).toBe("redis");
  });

  it("can discard the local graph and reload the saved one after a conflict", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    vi.mocked(saveGraph).mockRejectedValue(
      new HttpError(409, "Saved after revision 1."),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Add Redis" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    vi.mocked(getGraph).mockResolvedValueOnce(savedGraph);
    fireEvent.click(
      await screen.findByRole("button", { name: "Discard mine and reload" }),
    );

    expect(await screen.findByText("Orders API")).toBeInTheDocument();
    expect(screen.getByText("Revision 2")).toBeInTheDocument();
    expect(screen.getByText("Graph saved")).toBeInTheDocument();
  });

  it("saves a new name and description", async () => {
    const saved = { ...scenario, name: "Warm cache", description: null };
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario)
      .mockResolvedValueOnce(scenario)
      .mockResolvedValue(saved);
    vi.mocked(updateScenario).mockResolvedValue(saved);
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Details" }));
    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "Warm cache" },
    });
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: " " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Changes saved")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "Warm cache" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toHaveValue("");
    expect(updateScenario).toHaveBeenCalledWith("7", "3", {
      name: "Warm cache",
      description: "",
    });
  });

  it("asks for a name before saving", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Details" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(
      await screen.findByText("Enter a name for the scenario."),
    ).toBeInTheDocument();
    expect(updateScenario).not.toHaveBeenCalled();
  });

  it("shows the not-found page for a scenario outside the project", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockRejectedValue(
      new HttpError(404, "Scenario not found"),
    );
    renderScenario("99");

    expect(
      await screen.findByRole("heading", { name: "Scenario not found" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Go to your projects" }),
    ).toHaveAttribute("href", "/projects");
  });

  it("shows an error when the scenario cannot be loaded and retries", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario)
      .mockRejectedValueOnce(new HttpError(503, "Service Unavailable"))
      .mockResolvedValueOnce(scenario);
    renderScenario();

    expect(
      await screen.findByRole("heading", { name: "Scenario unavailable" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "Cold cache" }),
    ).toBeInTheDocument();
  });
});
