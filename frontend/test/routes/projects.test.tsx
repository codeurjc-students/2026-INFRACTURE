import { fireEvent, render, screen, within } from "@testing-library/react";
import {
  createMemoryRouter,
  Outlet,
  RouterProvider,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { UserDTO } from "~/features/auth/model/UserDTO";
import { getProjects } from "~/features/projects/api/project-service";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import ProjectsRoute, { clientLoader, ErrorBoundary } from "~/routes/projects";
import { HttpError } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";

vi.mock("~/features/projects/api/project-service");

function project(id: number): ProjectDTO {
  return {
    id,
    name: `Project ${id}`,
    description: id === 1 ? "Redis in front of PostgreSQL." : null,
    createdAt: "2026-10-20T10:00:00Z",
    updatedAt: "2026-10-20T10:00:00Z",
  };
}

function pageOf(
  ids: number[],
  number: number,
  totalElements: number,
): PageDTO<ProjectDTO> {
  return {
    content: ids.map(project),
    page: {
      size: 10,
      number,
      totalElements,
      totalPages: Math.ceil(totalElements / 10),
    },
  };
}

const firstTen = Array.from({ length: 10 }, (_, index) => 12 - index);

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function ProjectsTestRoute() {
  const loaderData = useLoaderData() as Awaited<
    ReturnType<typeof clientLoader>
  >;

  return <ProjectsRoute loaderData={loaderData} />;
}

function renderProjects() {
  const router = createMemoryRouter(
    [
      {
        id: "routes/private-layout",
        loader: () => ({ user: { displayName: "Alice Smith" } as UserDTO }),
        Component: Outlet,
        children: [
          {
            path: "/projects",
            loader: clientLoader,
            Component: ProjectsTestRoute,
            ErrorBoundary,
          },
          { path: "/projects/new", Component: () => <p>New project page</p> },
        ],
      },
    ],
    { initialEntries: ["/projects"] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("projects route", () => {
  it("shows the first ten projects with their details", async () => {
    vi.mocked(getProjects).mockResolvedValue(pageOf(firstTen, 0, 12));
    renderProjects();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Projects" }),
    ).toBeInTheDocument();
    const cards = within(screen.getByRole("list")).getAllByRole("link");
    expect(cards).toHaveLength(10);
    expect(cards[0]).toHaveAttribute("href", "/projects/12");
    expect(
      within(cards[0]).getByRole("heading", { name: "Project 12" }),
    ).toBeInTheDocument();
    expect(cards[0]).toHaveTextContent("Not run yet");
    expect(cards[0]).toHaveTextContent("Updated");
    expect(cards[1]).toHaveTextContent("Example runs");
    expect(cards[0]).toHaveTextContent("Private");
    expect(screen.getByText("12 projects, all private")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Showing 10 of 12");
    expect(screen.getByRole("link", { name: "New project" })).toHaveAttribute(
      "href",
      "/projects/new",
    );
    expect(
      screen.getByRole("link", { name: "Your account" }),
    ).toHaveTextContent("AS");
    expect(getProjects).toHaveBeenCalledWith();
  });

  it("adds the next page after the projects already shown", async () => {
    vi.mocked(getProjects)
      .mockResolvedValueOnce(pageOf(firstTen, 0, 12))
      .mockResolvedValueOnce(pageOf([3, 2, 1], 1, 13));
    renderProjects();

    fireEvent.click(
      await screen.findByRole("button", { name: "More results" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Project 1" }),
    ).toBeInTheDocument();
    expect(within(screen.getByRole("list")).getAllByRole("link")).toHaveLength(
      12,
    );
    expect(
      screen.getByText("Redis in front of PostgreSQL."),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Showing 12 of 13");
    expect(
      screen.queryByRole("button", { name: "More results" }),
    ).not.toBeInTheDocument();
    expect(getProjects).toHaveBeenLastCalledWith(1);
  });

  it("keeps the shown projects when the next page fails and lets the user retry", async () => {
    vi.mocked(getProjects)
      .mockResolvedValueOnce(pageOf(firstTen, 0, 12))
      .mockRejectedValueOnce(new HttpError(503, "Service Unavailable"))
      .mockResolvedValueOnce(pageOf([2, 1], 1, 12));
    renderProjects();

    fireEvent.click(
      await screen.findByRole("button", { name: "More results" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load more projects",
    );
    expect(within(screen.getByRole("list")).getAllByRole("link")).toHaveLength(
      10,
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("heading", { name: "Project 1" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("invites the user to create a first project when there are none", async () => {
    vi.mocked(getProjects).mockResolvedValue(pageOf([], 0, 0));
    renderProjects();

    expect(
      await screen.findByRole("heading", { name: "Nothing here to break yet" }),
    ).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("link", { name: "Create your first project" }),
    );

    expect(await screen.findByText("New project page")).toBeInTheDocument();
  });

  it("shows an error when the projects cannot be loaded and retries", async () => {
    vi.mocked(getProjects)
      .mockRejectedValueOnce(new HttpError(500, "Internal Server Error"))
      .mockResolvedValueOnce(pageOf([1], 0, 1));
    renderProjects();

    expect(
      await screen.findByRole("heading", { name: "Projects unavailable" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("heading", { name: "Project 1" }),
    ).toBeInTheDocument();
    expect(screen.getByText("1 project, all private")).toBeInTheDocument();
  });
});
