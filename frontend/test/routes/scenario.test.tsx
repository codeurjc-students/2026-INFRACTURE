import { fireEvent, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getProject } from "~/features/projects/api/project-service";
import {
  getScenario,
  updateScenario,
} from "~/features/projects/api/scenario-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";
import ScenarioRoute, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "~/routes/scenario";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/projects/api/project-service");
vi.mock("~/features/projects/api/scenario-service");

const project = { id: 7, name: "Cache lab" } as ProjectDTO;
const scenario: ScenarioDTO = {
  id: 3,
  name: "Cold cache",
  description: "Empty Redis.",
  revision: 1,
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-20T10:00:00Z",
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

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("scenario route", () => {
  it("identifies the open scenario and explains that it has no components", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    renderScenario();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Cold cache" }),
    ).toBeInTheDocument();
    const breadcrumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(breadcrumb).toHaveTextContent("Projects/Cache lab/Cold cache");
    expect(screen.getByText("Cold cache", { selector: "li" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText("Empty Redis.")).toBeInTheDocument();
    expect(screen.getByText("Revision 1")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "No components yet" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: "Browse components" }));

    expect(await screen.findByText("Catalogue page")).toBeInTheDocument();
    expect(getScenario).toHaveBeenCalledWith("7", "3");
  });

  it("saves a new name and description", async () => {
    const saved = { ...scenario, name: "Warm cache", description: null };
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario)
      .mockResolvedValueOnce(scenario)
      .mockResolvedValue(saved);
    vi.mocked(updateScenario).mockResolvedValue(saved);
    renderScenario();

    fireEvent.click(
      await screen.findByRole("button", { name: "Edit details" }),
    );
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
    expect(screen.queryByText("Empty Redis.")).not.toBeInTheDocument();
    expect(updateScenario).toHaveBeenCalledWith("7", "3", {
      name: "Warm cache",
      description: "",
    });
  });

  it("asks for a name before saving", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenario).mockResolvedValue(scenario);
    renderScenario();

    fireEvent.click(
      await screen.findByRole("button", { name: "Edit details" }),
    );
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
