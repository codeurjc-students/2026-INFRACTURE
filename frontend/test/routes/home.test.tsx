import { fireEvent, render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  useLoaderData,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser, logout } from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import Home, { clientLoader } from "~/routes/home";
import { clientAction as logoutAction } from "~/routes/logout";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/auth/api/auth-service");

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function HomeTestRoute() {
  const loaderData = useLoaderData() as Awaited<
    ReturnType<typeof clientLoader>
  >;

  return <Home loaderData={loaderData} />;
}

function renderHome() {
  const router = createMemoryRouter([
    { path: "/", loader: clientLoader, Component: HomeTestRoute },
    { path: "/logout", action: logoutAction },
    { path: "/login", Component: () => <p>Login page</p> },
  ]);
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("home route", () => {
  it("links to the component catalogue and to sign in without a session", async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new HttpError(401, "Unauthorized"),
    );
    renderHome();

    expect(
      await screen.findByRole("link", { name: "Sign in" }),
    ).toHaveAttribute("href", "/login");
    expect(screen.getByRole("img", { name: "Infracture" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Open component catalogue" }),
    ).toHaveAttribute("href", "/catalogue");
    expect(
      screen.queryByRole("link", { name: "Open your projects" }),
    ).not.toBeInTheDocument();
  });

  it("signs the user out and opens the sign-in page", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({
      displayName: "Alice",
    } as UserDTO);
    vi.mocked(logout).mockResolvedValue();
    renderHome();

    expect(await screen.findByRole("link", { name: "Alice" })).toHaveAttribute(
      "href",
      "/account",
    );
    expect(
      screen.getByRole("link", { name: "Open your projects" }),
    ).toHaveAttribute("href", "/projects");
    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByText("Login page")).toBeInTheDocument();
    expect(logout).toHaveBeenCalledOnce();
  });
});
