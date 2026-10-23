import { fireEvent, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  useActionData,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getProject } from "~/features/projects/api/project-service";
import { createScenario } from "~/features/projects/api/scenario-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";
import NewScenarioRoute, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "~/routes/new-scenario";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/projects/api/project-service");
vi.mock("~/features/projects/api/scenario-service");

const project = { id: 7, name: "Cache lab" } as ProjectDTO;

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function NewScenarioTestRoute() {
  const loaderData = useLoaderData() as Awaited<
    ReturnType<typeof clientLoader>
  >;
  const actionData = useActionData() as Awaited<
    ReturnType<typeof clientAction>
  >;

  return (
    <NewScenarioRoute
      loaderData={loaderData}
      actionData={actionData instanceof Response ? undefined : actionData}
    />
  );
}

function renderNewScenario() {
  const router = createMemoryRouter(
    [
      {
        path: "/projects/:projectId/scenarios/new",
        loader: clientLoader as never,
        action: clientAction as never,
        Component: NewScenarioTestRoute,
        ErrorBoundary,
      },
      {
        path: "/projects/:projectId/scenarios/:scenarioId",
        Component: () => <p>Scenario page</p>,
      },
      { path: "/projects/:projectId", Component: () => <p>Project page</p> },
    ],
    { initialEntries: ["/projects/7/scenarios/new"] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
  return router;
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("new scenario route", () => {
  it("creates an empty scenario in the project and opens it", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(createScenario).mockResolvedValue({ id: 3 } as ScenarioDTO);
    const router = renderNewScenario();

    expect(
      await screen.findByRole("link", { name: "Cache lab" }),
    ).toHaveAttribute("href", "/projects/7");
    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: " Cold cache " },
    });
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: "Empty Redis" },
    });
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Cold cache",
    );
    fireEvent.click(screen.getByRole("button", { name: "Create scenario" }));

    expect(await screen.findByText("Scenario page")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/projects/7/scenarios/3");
    expect(createScenario).toHaveBeenCalledWith("7", {
      name: "Cold cache",
      description: "Empty Redis",
    });
  });

  it("asks for a name without calling the backend", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    renderNewScenario();

    fireEvent.click(
      await screen.findByRole("button", { name: "Create scenario" }),
    );

    expect(
      await screen.findByText("Enter a name for the scenario."),
    ).toBeInTheDocument();
    expect(createScenario).not.toHaveBeenCalled();
  });

  it("shows the backend message and keeps the typed values", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    vi.mocked(createScenario).mockRejectedValue(
      new HttpError(400, "Scenario name must contain at most 30 characters"),
    );
    renderNewScenario();

    fireEvent.change(await screen.findByLabelText("Name"), {
      target: { value: "Cold cache" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create scenario" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Scenario name must contain at most 30 characters",
    );
    expect(screen.getByLabelText("Name")).toHaveValue("Cold cache");
  });

  it("goes back to the project when the user cancels", async () => {
    vi.mocked(getProject).mockResolvedValue(project);
    renderNewScenario();

    fireEvent.click(await screen.findByRole("link", { name: "Cancel" }));

    expect(await screen.findByText("Project page")).toBeInTheDocument();
  });

  it("shows the not-found page for a project of another account", async () => {
    vi.mocked(getProject).mockRejectedValue(
      new HttpError(404, "Project not found"),
    );
    renderNewScenario();

    expect(
      await screen.findByRole("heading", { name: "Project not found" }),
    ).toBeInTheDocument();
  });
});
