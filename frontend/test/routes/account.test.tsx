import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser } from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import AccountRoute from "~/routes/account";
import PrivateLayout, { clientLoader } from "~/routes/private-layout";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/auth/api/auth-service");

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

const privateLoader = ({ request }: { request: Request }) =>
  clientLoader({ request } as Parameters<typeof clientLoader>[0]);

function LoginPage() {
  const { search } = useLocation();

  return <p>Login page {new URLSearchParams(search).get("redirectTo")}</p>;
}

function renderAccount() {
  const router = createMemoryRouter(
    [
      {
        id: "routes/private-layout",
        loader: privateLoader,
        Component: PrivateLayout,
        children: [{ path: "/account", Component: AccountRoute }],
      },
      { path: "/login", Component: LoginPage },
    ],
    { initialEntries: ["/account"] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("private account page", () => {
  it("shows the signed-in account", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({
      username: "alice",
      displayName: "Alice",
      email: "alice@example.com",
    } as UserDTO);
    renderAccount();

    expect(
      await screen.findByRole("heading", { name: "Your account" }),
    ).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Your account" }),
    ).toHaveTextContent("A");
  });

  it("sends a visitor without a session to the login and remembers the page", async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new HttpError(401, "Unauthorized"),
    );
    renderAccount();

    expect(await screen.findByText("Login page /account")).toBeInTheDocument();
  });
});
