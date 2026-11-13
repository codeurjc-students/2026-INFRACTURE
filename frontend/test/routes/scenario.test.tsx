import "../react-flow-mocks";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
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
import { getWorkloadProfiles } from "~/features/component-catalogue/api/workload-profile-service";

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
  getCurrentExecution,
  runScenario,
  stopExecution,
} from "~/features/scenario-canvas/api/execution-service";
import type { ExecutionDTO } from "~/features/scenario-canvas/model/ExecutionDTO";
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
import {
  constantHttp,
  httpService,
  redis,
} from "../fixtures/component-templates";

vi.mock("~/features/projects/api/project-service");
vi.mock("~/features/projects/api/scenario-service");
vi.mock("~/features/scenario-canvas/api/graph-service");
vi.mock("~/features/scenario-canvas/api/execution-service");
vi.mock("~/features/component-catalogue/api/component-template-service");
vi.mock("~/features/component-catalogue/api/workload-profile-service");

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
  workload: null,
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
  workload: null,
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
    workload: null,
    issues: [],
  };
}

// The saved graph without errors, so Run is allowed.
const runnableGraph: ScenarioGraphDTO = {
  ...savedGraph,
  components: [
    { ...savedGraph.components[0], parameters: { responseDelayMs: 0 } },
  ],
  issues: [],
};

function execution(overrides: Partial<ExecutionDTO> = {}): ExecutionDTO {
  return {
    id: 12,
    scenarioId: 3,
    scenarioRevision: 2,
    phase: "RESERVED",
    stopCause: null,
    outcome: null,
    failureReason: null,
    cleanupFailures: 0,
    cleanupError: null,
    cleanupRetryNeeded: false,
    createdAt: "2026-11-07T10:00:00Z",
    deadlineAt: null,
    secondsLeft: null,
    releasedAt: null,
    components: [],
    workload: null,
    ...overrides,
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
  vi.mocked(getCurrentExecution).mockResolvedValue(null);
  vi.mocked(getComponentTemplates).mockResolvedValue({
    content: [httpService, redis],
    page: { size: 100, number: 0, totalElements: 2, totalPages: 1 },
  });
  vi.mocked(getWorkloadProfiles).mockResolvedValue([constantHttp]);
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
    expect(getWorkloadProfiles).toHaveBeenCalled();
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
        workload: graph.workload,
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

  it("adds a load, aims it at a component and saves it with the graph", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(saveGraph).mockImplementation(
      async (_project, _scenario, graph) => ({
        ...runnableGraph,
        revision: 3,
        workload: graph.workload,
      }),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Load" }));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(
      within(inspector).getByText(/The scenario has no load/),
    ).toBeInTheDocument();
    fireEvent.click(
      within(inspector).getByRole("button", { name: "Add load" }),
    );
    expect(within(inspector).getByLabelText("requestsPerSecond")).toHaveValue(
      5,
    );
    expect(within(inspector).getByLabelText("durationSeconds")).toHaveValue(60);
    fireEvent.click(within(inspector).getByLabelText("Target"));
    fireEvent.click(await screen.findByRole("option", { name: "Orders API" }));
    fireEvent.change(within(inspector).getByLabelText("requestsPerSecond"), {
      target: { value: "20" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Graph saved")).toBeInTheDocument();
    const sent = vi.mocked(saveGraph).mock.calls[0][2];
    expect(sent.workload).toMatchObject({
      targetKey: "11111111-1111-4111-8111-111111111111",
      profile: "CONSTANT_HTTP",
      parameters: {
        requestsPerSecond: 20,
        virtualUsers: 2,
        durationSeconds: 60,
      },
    });
    expect(sent.workload?.seed).toBeGreaterThanOrEqual(0);

    fireEvent.click(
      within(inspector).getByRole("button", { name: "Remove load" }),
    );
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    expect(
      within(inspector).getByRole("button", { name: "Add load" }),
    ).toBeInTheDocument();
  });

  it("offers as target only the components that accept the load", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue({
      ...connectedGraph("STATELESS"),
      connections: [],
    });
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Load" }));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    fireEvent.click(
      within(inspector).getByRole("button", { name: "Add load" }),
    );
    expect(
      within(inspector).getByText(
        "Only components that accept HTTP are listed.",
      ),
    ).toBeInTheDocument();
    fireEvent.click(within(inspector).getByLabelText("Target"));

    expect(
      await screen.findByRole("option", { name: "Orders API" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "orders-cache" })).toBeNull();
  });

  it("opens the load from its issue, and keeps it read-only while an experiment runs", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue({
      ...runnableGraph,
      workload: {
        targetKey: null,
        profile: "CONSTANT_HTTP",
        parameters: {
          requestsPerSecond: 5,
          virtualUsers: 2,
          durationSeconds: 60,
        },
        seed: 7,
      },
      issues: [
        {
          severity: "ERROR",
          code: "WORKLOAD_WITHOUT_TARGET",
          message: "Choose the node that receives the load",
          target: { kind: "WORKLOAD", key: null },
          field: "targetKey",
        },
      ],
    });
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "1 issue" }));
    fireEvent.click(
      screen.getByRole("button", {
        name: /Choose the node that receives the load/,
      }),
    );
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(
      within(inspector).getByRole("heading", { name: "Load" }),
    ).toBeInTheDocument();
    expect(within(inspector).getByLabelText("seed")).toHaveValue(7);
    expect(
      within(inspector).getByText("Choose the node that receives the load"),
    ).toBeInTheDocument();
  });

  it("draws the load as traffic entering its target, not as a connection", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue({
      ...runnableGraph,
      workload: {
        targetKey: "11111111-1111-4111-8111-111111111111",
        profile: "CONSTANT_HTTP",
        parameters: {
          requestsPerSecond: 5,
          virtualUsers: 2,
          durationSeconds: 60,
        },
        seed: 7,
      },
    });
    renderScenario();

    const node = await screen.findByLabelText(
      "Orders API, HTTP Service, receives the load at 5 req/s",
    );
    expect(within(node).getByTestId("traffic")).toHaveTextContent("5 req/s");
    expect(within(node).getByTestId("traffic")).not.toHaveAttribute(
      "data-sending",
    );
    expect(document.querySelectorAll(".react-flow__edge")).toHaveLength(0);
  });

  it("shows what the load did in the last experiment, without turning missing values into 0", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({
        phase: "RELEASED",
        stopCause: "STOP_REQUESTED",
        outcome: "COMPLETED",
        releasedAt: "2026-11-09T10:01:00Z",
        workload: {
          targetKey: "11111111-1111-4111-8111-111111111111",
          profile: "CONSTANT_HTTP",
          parameters: {
            requestsPerSecond: 5,
            virtualUsers: 2,
            durationSeconds: 60,
          },
          seed: 7,
          summaryCollectedAt: "2026-11-09T10:00:50Z",
          completed: false,
          summary: {
            requests: 42,
            failedRequests: 0,
            droppedIterations: 0,
            averageLatencyMs: 28.36,
            p95LatencyMs: null,
          },
        },
      }),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Load" }));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(within(inspector).getByText("Last experiment")).toBeInTheDocument();
    expect(within(inspector).getByText("Stopped early")).toBeInTheDocument();
    const summary = within(inspector).getByLabelText("Load summary");
    expect(summary).toHaveTextContent("requests42");
    expect(summary).toHaveTextContent("failed0");
    expect(summary).toHaveTextContent("average28.4 ms");
    expect(summary).toHaveTextContent("p95—");
    expect(
      within(inspector).getByRole("button", { name: "Add load" }),
    ).toBeInTheDocument();
  });

  it("shows the load but changes nothing while the experiment holds the scenario", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue({
      ...runnableGraph,
      workload: {
        targetKey: "11111111-1111-4111-8111-111111111111",
        profile: "CONSTANT_HTTP",
        parameters: {
          requestsPerSecond: 5,
          virtualUsers: 2,
          durationSeconds: 60,
        },
        seed: 7,
      },
    });
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({
        phase: "RUNNING",
        workload: {
          targetKey: "11111111-1111-4111-8111-111111111111",
          profile: "CONSTANT_HTTP",
          parameters: {
            requestsPerSecond: 5,
            virtualUsers: 2,
            durationSeconds: 60,
          },
          seed: 7,
          summaryCollectedAt: null,
          completed: null,
          summary: null,
        },
      }),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Load" }));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(
      within(inspector).getByLabelText("requestsPerSecond"),
    ).toBeDisabled();
    expect(
      within(inspector).getByRole("button", { name: "Remove load" }),
    ).toBeDisabled();
    expect(within(inspector).getByText("Sending traffic")).toBeInTheDocument();
    expect(screen.getByTestId("traffic")).toHaveAttribute(
      "data-sending",
      "true",
    );
  });

  it("shows saved nodes with their contract issues", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue(savedGraph);
    renderScenario();

    expect(await screen.findByText("Orders API")).toBeInTheDocument();
    expect(screen.getByText("1 error")).toBeInTheDocument();
    expect(
      screen.queryByRole("complementary", { name: "Inspector" }),
    ).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "1 issue" }));
    fireEvent.click(
      screen.getByRole("button", {
        name: /responseDelayMs must be between 0 and 5000/,
      }),
    );
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

  it("lists errors apart from warnings, locates them and flags them as old after an edit", async () => {
    const orders = "11111111-1111-4111-8111-111111111111";
    const cache = "22222222-2222-4222-8222-222222222222";
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue({ ...scenario, revision: 2 });
    vi.mocked(getGraph).mockResolvedValue({
      ...connectedGraph("CACHE_ASIDE"),
      issues: [
        {
          severity: "ERROR",
          code: "DEPENDENCY_CYCLE",
          message: "Orders API → orders-cache closes a dependency cycle",
          target: { kind: "CONNECTION", key: `${orders}:cache:${cache}` },
          field: "cache",
        },
        {
          severity: "WARNING",
          code: "NO_CONSUMER",
          message: "Tasks published to orders-cache accumulate",
          target: { kind: "COMPONENT", key: cache },
          field: null,
        },
        {
          severity: "ERROR",
          code: "EMPTY_SCENARIO",
          message: "Add at least one component to run the scenario",
          target: { kind: "SCENARIO", key: null },
          field: null,
        },
      ],
    });
    renderScenario();

    expect(
      await screen.findByLabelText(
        "Orders API Cache orders-cache, not allowed",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("1 warning")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "3 issues" }));
    const panel = screen.getByRole("complementary", { name: "Inspector" });
    expect(
      within(panel).getByRole("heading", { name: "Issues" }),
    ).toBeInTheDocument();
    expect(
      within(panel).getByText(
        "2 errors block running the scenario. Warnings do not.",
      ),
    ).toBeInTheDocument();
    expect(
      within(panel).getByText("Add at least one component to run the scenario"),
    ).toBeInTheDocument();
    expect(within(panel).getAllByText("Error:")).toHaveLength(2);
    expect(within(panel).getAllByText("Warning:")).toHaveLength(1);

    fireEvent.click(within(panel).getByRole("button", { name: /accumulate/ }));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(within(inspector).getByLabelText("Name")).toHaveValue(
      "orders-cache",
    );
    expect(
      within(inspector).getByText("Tasks published to orders-cache accumulate"),
    ).toBeInTheDocument();

    fireEvent.change(within(inspector).getByLabelText("Name"), {
      target: { value: "hot-cache" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "3 issues, from the last save" }),
    );
    expect(
      screen.getByText(
        "These issues come from the last save. Save to check your changes.",
      ),
    ).toBeInTheDocument();
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

describe("running the scenario", () => {
  beforeEach(() => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
  });

  it("runs the saved revision once even when Run is clicked twice", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    let reserve!: (execution: ExecutionDTO) => void;
    vi.mocked(runScenario).mockReturnValue(
      new Promise((resolve) => {
        reserve = resolve;
      }),
    );
    renderScenario();

    const run = await screen.findByRole("button", { name: "Run" });
    fireEvent.click(run);
    fireEvent.click(run);
    expect(run).toBeDisabled();
    reserve(execution());

    expect(await screen.findByRole("button", { name: "Stop" })).toBeEnabled();
    expect(runScenario).toHaveBeenCalledExactlyOnceWith("7", "3", 2);
  });

  it("waits for the save and for the errors to be fixed before Run", async () => {
    vi.mocked(getGraph).mockResolvedValue(savedGraph);
    renderScenario();

    const run = await screen.findByRole("button", { name: "Run" });
    expect(run).toBeDisabled();
    expect(run).toHaveAttribute(
      "title",
      "Fix the errors first: they block Run",
    );

    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    cleanup();
    renderScenario();
    fireEvent.click(
      await screen.findByRole("button", { name: "Add HTTP Service" }),
    );
    expect(screen.getByRole("button", { name: "Run" })).toHaveAttribute(
      "title",
      "Save your changes first: Run uses the saved scenario",
    );
  });

  it("stops the running experiment and shows that it is stopping", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({ phase: "RUNNING" }),
    );
    vi.mocked(stopExecution).mockResolvedValue(
      execution({ phase: "RUNNING", stopCause: "STOP_REQUESTED" }),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Stop" }));

    expect(
      await screen.findByRole("button", { name: "Stopping…" }),
    ).toBeDisabled();
    expect(stopExecution).toHaveBeenCalledExactlyOnceWith("7", "3", 12);
  });

  it("shows a failed cleanup and retries it once the automatic attempts are spent", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    const stuck = {
      phase: "STOPPING",
      stopCause: "STOP_REQUESTED",
      cleanupFailures: 3,
      cleanupError: "docker volume rm failed: volume is in use",
      cleanupRetryNeeded: true,
    } as const;
    vi.mocked(getCurrentExecution).mockResolvedValue(execution(stuck));
    vi.mocked(stopExecution).mockResolvedValue(
      execution({ ...stuck, cleanupFailures: 0, cleanupRetryNeeded: false }),
    );
    renderScenario();

    const alert = await screen.findByText("The cleanup could not finish");
    const message = alert.closest('[role="alert"]')!;
    expect(message).toHaveTextContent(
      "docker volume rm failed: volume is in use",
    );
    expect(message).toHaveTextContent("press Retry cleanup");
    expect(screen.getByText("Cleanup failed")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Run" }),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry cleanup" }));

    expect(
      await screen.findByRole("button", { name: "Stopping…" }),
    ).toBeDisabled();
    expect(stopExecution).toHaveBeenCalledExactlyOnceWith("7", "3", 12);
    expect(
      screen.queryByText("The cleanup could not finish"),
    ).not.toBeInTheDocument();
  });

  it("says the backend retries a failed cleanup on its own", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({
        phase: "STOPPING",
        stopCause: "FAILURE",
        failureReason: "Orders API is unhealthy",
        cleanupFailures: 1,
        cleanupError: "docker network rm failed: network has active endpoints",
      }),
    );
    renderScenario();

    const alerts = await screen.findAllByRole("alert");
    const cleanupAlert = alerts.find((alert) =>
      alert.textContent?.includes("The cleanup failed, trying again"),
    )!;
    expect(cleanupAlert).toHaveTextContent(
      "The backend tries again on its own.",
    );
    expect(
      alerts.some((alert) =>
        alert.textContent?.includes("Orders API is unhealthy"),
      ),
    ).toBe(true);
    expect(screen.getByText("Cleanup failed, retrying")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Stopping…" })).toBeDisabled();
  });

  it("follows the experiment every 2 s until it is released", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    const key = runnableGraph.components[0].key;
    vi.mocked(getCurrentExecution)
      .mockResolvedValueOnce(execution({ phase: "STARTING" }))
      .mockResolvedValueOnce(
        execution({
          phase: "RUNNING",
          components: [
            {
              key,
              name: "Orders API",
              type: "HTTP_SERVICE",
              status: "HEALTHY",
              response: '{"component":"orders-api"}',
            },
          ],
        }),
      )
      .mockResolvedValue(
        execution({
          phase: "RELEASED",
          stopCause: "STOP_REQUESTED",
          outcome: "COMPLETED",
        }),
      );
    renderScenario();

    expect(await screen.findByText("Starting")).toBeInTheDocument();
    // Real timers: each poll waits the real 2 s.
    expect(
      await screen.findByText("Running", {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    const node = screen.getByRole("group", { name: /Orders API/ });
    expect(within(node).getByText("Healthy")).toBeInTheDocument();
    fireEvent.click(within(node).getByText("Orders API"));
    const inspector = screen.getByRole("complementary", {
      name: "Inspector",
    });
    expect(
      within(inspector).getByRole("figure", { name: "Response" }),
    ).toHaveTextContent('{"component":"orders-api"}');

    expect(
      await screen.findByText("Completed", {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run" })).toBeEnabled();
    expect(screen.queryByText("Healthy")).not.toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 2100));
    expect(getCurrentExecution).toHaveBeenCalledTimes(3);
    // Two real polls and the check that no third one follows.
  }, 10_000);

  // Reopening the canvas recovers the server's countdown instead of starting a new one.
  it("counts down from the time the server gives and follows it on each poll", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution)
      .mockResolvedValueOnce(
        execution({
          phase: "RUNNING",
          deadlineAt: "2026-11-07T10:40:00Z",
          secondsLeft: 600,
        }),
      )
      .mockResolvedValue(
        execution({
          phase: "RUNNING",
          deadlineAt: "2026-11-07T10:40:00Z",
          secondsLeft: 300,
        }),
      );
    renderScenario();

    const timer = await screen.findByRole("timer", { name: "Time left" });
    expect(timer).toHaveTextContent("10:00 left");
    // Real timers: the poll after 2 s brings the server's figure back.
    await waitFor(
      () =>
        expect(
          screen.getByRole("timer", { name: "Time left" }),
        ).toHaveTextContent("5:00 left"),
      { timeout: 3000 },
    );
  }, 10_000);

  it("says the experiment ended at its time limit", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({
        phase: "RELEASED",
        stopCause: "TIME_LIMIT",
        outcome: "COMPLETED",
        deadlineAt: "2026-11-07T10:40:00Z",
      }),
    );
    renderScenario();

    expect(await screen.findByText("Time limit reached")).toBeInTheDocument();
    expect(screen.queryByRole("timer")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run" })).toBeEnabled();
  });

  it("keeps the scenario read-only while the experiment holds it", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({ phase: "RUNNING" }),
    );
    renderScenario();

    expect(
      await screen.findByText(
        "The scenario is read-only while the experiment runs",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add HTTP Service" }),
    ).toBeDisabled();
    expect(
      screen.getByText("Stop the experiment to add components."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByText("Orders API"));
    const inspector = screen.getByRole("complementary", { name: "Inspector" });
    expect(within(inspector).getByLabelText("Name")).toBeDisabled();
    expect(
      within(inspector).getByRole("button", { name: "Delete component" }),
    ).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Details" }));
    expect(screen.getByLabelText("Name")).toBeDisabled();
  });

  it("explains why the experiment failed until the message is dismissed", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(getCurrentExecution).mockResolvedValue(
      execution({
        phase: "RELEASED",
        stopCause: "FAILURE",
        outcome: "FAILED",
        failureReason: "Orders API is unhealthy",
      }),
    );
    renderScenario();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("The experiment failed");
    expect(alert).toHaveTextContent("Orders API is unhealthy");
    expect(screen.getByText("Failed")).toBeInTheDocument();
    fireEvent.click(within(alert).getByRole("button", { name: "Dismiss" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run" })).toBeEnabled();
  });

  it("explains why Run was rejected and lets the message go", async () => {
    vi.mocked(getGraph).mockResolvedValue(runnableGraph);
    vi.mocked(runScenario).mockRejectedValue(
      new HttpError(
        409,
        "Another experiment is running. Run this scenario when it ends.",
      ),
    );
    renderScenario();

    fireEvent.click(await screen.findByRole("button", { name: "Run" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Could not run the scenario");
    expect(alert).toHaveTextContent("Another experiment is running.");
    fireEvent.click(within(alert).getByRole("button", { name: "Dismiss" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run" })).toBeEnabled();
  });
});
