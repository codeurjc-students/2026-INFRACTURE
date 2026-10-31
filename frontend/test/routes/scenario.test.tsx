import "../react-flow-mocks";
import { fireEvent, render, screen, within } from "@testing-library/react";
import {
  createMemoryRouter,
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

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function ScenarioTestRoute() {
  const loaderData = useLoaderData() as Awaited<
    ReturnType<typeof clientLoader>
  >;

  return <ScenarioRoute loaderData={loaderData} />;
}

function renderScenario(id = "3") {
  const router = createMemoryRouter(
    [
      {
        path: "/projects/:projectId/scenarios/:scenarioId",
        loader: clientLoader as never,
        action: clientAction as never,
        Component: ScenarioTestRoute,
        ErrorBoundary,
      },
      { path: "/catalogue", Component: () => <p>Catalogue page</p> },
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
