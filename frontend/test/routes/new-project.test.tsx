import { fireEvent, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  useActionData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { createProject } from "~/features/projects/api/project-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import NewProjectRoute, { clientAction } from "~/routes/new-project";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/projects/api/project-service");

const routeAction = ({ request }: { request: Request }) =>
  clientAction({ request } as Parameters<typeof clientAction>[0]);

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function NewProjectTestRoute() {
  const actionData = useActionData() as Awaited<
    ReturnType<typeof clientAction>
  >;

  return (
    <NewProjectRoute
      actionData={actionData instanceof Response ? undefined : actionData}
    />
  );
}

function renderNewProject() {
  const router = createMemoryRouter(
    [
      {
        path: "/projects/new",
        action: routeAction,
        Component: NewProjectTestRoute,
      },
      { path: "/projects/:projectId", Component: () => <p>Project page</p> },
      { path: "/projects", Component: () => <p>Projects page</p> },
    ],
    { initialEntries: ["/projects/new"] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
  return router;
}

function fill(name: string, description = "") {
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: name } });
  fireEvent.change(screen.getByLabelText("Description"), {
    target: { value: description },
  });
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("new project route", () => {
  it("creates the project and opens it", async () => {
    vi.mocked(createProject).mockResolvedValue({ id: 42 } as ProjectDTO);
    const router = renderNewProject();

    fill("  Cache lab ", "What happens without the cache?");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Cache lab",
    );
    expect(screen.getByText("12/30")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create project" }));

    expect(await screen.findByText("Project page")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/projects/42");
    expect(createProject).toHaveBeenCalledWith({
      name: "Cache lab",
      description: "What happens without the cache?",
    });
  });

  it("asks for a name without calling the backend", async () => {
    renderNewProject();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "New project",
    );
    fill("   ", "Kept description");
    fireEvent.click(screen.getByRole("button", { name: "Create project" }));

    expect(
      await screen.findByText("Enter a name for the project."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("Description")).toHaveValue(
      "Kept description",
    );
    expect(createProject).not.toHaveBeenCalled();
  });

  it("shows the backend message and keeps the typed values", async () => {
    vi.mocked(createProject).mockRejectedValue(
      new HttpError(400, "Project name must contain at most 30 characters"),
    );
    renderNewProject();

    fill("Cache lab", "Kept description");
    fireEvent.click(screen.getByRole("button", { name: "Create project" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Project name must contain at most 30 characters",
    );
    expect(screen.getByLabelText("Name")).toHaveValue("Cache lab");
    expect(screen.getByLabelText("Description")).toHaveValue(
      "Kept description",
    );
  });

  it("shows a general message when the backend cannot answer", async () => {
    vi.mocked(createProject).mockRejectedValue(
      new HttpError(500, "Internal Server Error"),
    );
    renderNewProject();

    fill("Cache lab");
    fireEvent.click(screen.getByRole("button", { name: "Create project" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Try again in a moment.",
    );
  });

  it("goes back to the projects when the user cancels", async () => {
    renderNewProject();

    fireEvent.click(screen.getByRole("link", { name: "Cancel" }));

    expect(await screen.findByText("Projects page")).toBeInTheDocument();
  });
});
