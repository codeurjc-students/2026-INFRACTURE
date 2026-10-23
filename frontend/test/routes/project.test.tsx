import { fireEvent, render, screen, within } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  useLoaderData,
} from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  getProject,
  updateProject,
} from "~/features/projects/api/project-service";
import { getScenarios } from "~/features/projects/api/scenario-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";
import ProjectRoute, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "~/routes/project";
import { HttpError } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";

vi.mock("~/features/projects/api/project-service");
vi.mock("~/features/projects/api/scenario-service");

const project: ProjectDTO = {
  id: 7,
  name: "Cache lab",
  description: "Redis in front of PostgreSQL.",
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-20T10:00:00Z",
};

function scenarioPage(
  ids: number[],
  number: number,
  totalElements: number,
): PageDTO<ScenarioDTO> {
  return {
    content: ids.map((id) => ({
      id,
      name: `Scenario ${id}`,
      description: id === 1 ? "Empty Redis." : null,
      revision: 1,
      createdAt: "2026-10-20T10:00:00Z",
      updatedAt: "2026-10-20T10:00:00Z",
    })),
    page: {
      size: 10,
      number,
      totalElements,
      totalPages: Math.ceil(totalElements / 10),
    },
  };
}

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function ProjectTestRoute() {
  const loaderData = useLoaderData() as Awaited<
    ReturnType<typeof clientLoader>
  >;

  return <ProjectRoute loaderData={loaderData} />;
}

function renderProject(id = "7") {
  const router = createMemoryRouter(
    [
      {
        path: "/projects/:projectId",
        loader: clientLoader as never,
        action: clientAction as never,
        Component: ProjectTestRoute,
        ErrorBoundary,
      },
      { path: "/projects", Component: () => <p>Projects page</p> },
      {
        path: "/projects/:projectId/scenarios/new",
        Component: () => <p>New scenario page</p>,
      },
    ],
    { initialEntries: [`/projects/${id}`] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

function edit(name: string, description: string) {
  fireEvent.click(screen.getByRole("button", { name: "Edit details" }));
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: name } });
  fireEvent.change(screen.getByLabelText("Description"), {
    target: { value: description },
  });
}

beforeEach(() => {
  vi.mocked(getScenarios).mockResolvedValue(scenarioPage([], 0, 0));
});

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("project route", () => {
  it("shows the project with its details", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    renderProject();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Cache lab" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Redis in front of PostgreSQL."),
    ).toBeInTheDocument();
    expect(screen.getByText("Created 18 Oct 2026")).toBeInTheDocument();
    expect(screen.getByText(/^Last edited 20 Oct 2026/)).toBeInTheDocument();
    expect(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    ).toHaveTextContent("Cache lab");
    expect(getProject).toHaveBeenCalledWith("7");
  });

  it("saves the new name and description and closes the form", async () => {
    const saved = {
      ...project,
      name: "Cache drill",
      description: "Without Redis.",
    };
    vi.mocked(getProject)
      .mockResolvedValueOnce(project)
      .mockResolvedValue(saved);
    vi.mocked(updateProject).mockResolvedValue(saved);
    renderProject();

    await screen.findByRole("heading", { level: 1, name: "Cache lab" });
    edit("Cache drill", "Without Redis.");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Cache drill",
    );
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Changes saved")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "Cache drill" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Without Redis.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
    expect(updateProject).toHaveBeenCalledWith("7", {
      name: "Cache drill",
      description: "Without Redis.",
    });
  });

  it("keeps the changes in the form when the backend rejects them", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(updateProject).mockRejectedValue(
      new HttpError(
        400,
        "Project description must contain at most 280 characters",
      ),
    );
    renderProject();

    await screen.findByRole("heading", { level: 1, name: "Cache lab" });
    edit("Cache drill", "Too long");
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Project description must contain at most 280 characters",
    );
    expect(screen.getByLabelText("Name")).toHaveValue("Cache drill");
    expect(screen.getByLabelText("Description")).toHaveValue("Too long");
  });

  it("asks for a name before saving", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    renderProject();

    await screen.findByRole("heading", { level: 1, name: "Cache lab" });
    edit("", "Still here");
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(
      await screen.findByText("Enter a name for the project."),
    ).toBeInTheDocument();
    expect(updateProject).not.toHaveBeenCalled();
  });

  it("discards the changes when the user cancels", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    renderProject();

    await screen.findByRole("heading", { level: 1, name: "Cache lab" });
    edit("Cache drill", "Changed");
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(
      screen.getByRole("heading", { level: 1, name: "Cache lab" }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
    expect(updateProject).not.toHaveBeenCalled();
  });

  it.each([
    [404, "Project not found"],
    [400, "Bad Request"],
  ])(
    "shows the not-found page when the backend answers %i",
    async (status, message) => {
      vi.mocked(getProject).mockRejectedValue(new HttpError(status, message));
      renderProject("99");

      expect(
        await screen.findByRole("heading", { name: "Project not found" }),
      ).toBeInTheDocument();
      fireEvent.click(
        screen.getByRole("link", { name: "Go to your projects" }),
      );
      expect(await screen.findByText("Projects page")).toBeInTheDocument();
    },
  );

  it("shows an error when the project cannot be loaded and retries", async () => {
    vi.mocked(getProject)
      .mockRejectedValueOnce(new HttpError(503, "Service Unavailable"))
      .mockResolvedValueOnce(project);
    renderProject();

    expect(
      await screen.findByRole("heading", { name: "Project unavailable" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "Cache lab" }),
    ).toBeInTheDocument();
  });

  it("lists the scenarios of the project and adds the next page", async () => {
    const firstTen = Array.from({ length: 10 }, (_, index) => 11 - index);
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(getScenarios)
      .mockResolvedValueOnce(scenarioPage(firstTen, 0, 11))
      .mockResolvedValueOnce(scenarioPage([1], 1, 11));
    renderProject();

    const scenarios = await screen.findByRole("region", { name: "Scenarios" });
    const cards = within(scenarios).getAllByRole("link", {
      name: /^Scenario/,
    });
    expect(cards).toHaveLength(10);
    expect(cards[0]).toHaveAttribute("href", "/projects/7/scenarios/11");
    expect(within(scenarios).getByRole("status")).toHaveTextContent(
      "Showing 10 of 11",
    );
    expect(
      within(scenarios).getByRole("link", { name: "New scenario" }),
    ).toHaveAttribute("href", "/projects/7/scenarios/new");
    fireEvent.click(
      within(scenarios).getByRole("button", { name: "More results" }),
    );

    expect(
      await within(scenarios).findByRole("heading", { name: "Scenario 1" }),
    ).toBeInTheDocument();
    expect(within(scenarios).getByText("Empty Redis.")).toBeInTheDocument();
    expect(
      within(scenarios).queryByRole("button", { name: "More results" }),
    ).not.toBeInTheDocument();
    expect(getScenarios).toHaveBeenCalledWith("7");
    expect(getScenarios).toHaveBeenLastCalledWith("7", 1);
  });

  it("invites the owner to create a first scenario when there are none", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    renderProject();

    expect(
      await screen.findByRole("heading", { name: "No scenarios yet" }),
    ).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("link", { name: "Create your first scenario" }),
    );

    expect(await screen.findByText("New scenario page")).toBeInTheDocument();
  });
});
