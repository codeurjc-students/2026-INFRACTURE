import { fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, useActionData } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser, login } from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import LoginRoute, { clientAction, clientLoader } from "~/routes/login";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/auth/api/auth-service");

const routeAction = ({ request }: { request: Request }) =>
  clientAction({ request } as Parameters<typeof clientAction>[0]);

const loginLoader = (url: string) =>
  clientLoader({ request: new Request(url) } as Parameters<typeof clientLoader>[0]);

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function LoginTestRoute() {
  const actionData = useActionData() as Awaited<ReturnType<typeof clientAction>>;

  return <LoginRoute actionData={actionData instanceof Response ? undefined : actionData} />;
}

function renderLogin(entry = "/login") {
  const router = createMemoryRouter(
    [
      { path: "/login", action: routeAction, Component: LoginTestRoute },
      { path: "/", Component: () => <p>Home</p> },
      { path: "/account", Component: () => <p>Account</p> },
    ],
    { initialEntries: [entry] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

function signIn() {
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "alice@example.com" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "A secure passphrase 2026!" } });
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("login route", () => {
  it("signs in with the submitted email and password, then opens the home page", async () => {
    vi.mocked(login).mockResolvedValue();
    renderLogin();

    signIn();

    expect(await screen.findByText("Home")).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith("alice@example.com", "A secure passphrase 2026!");
  });

  it("returns to the page that asked for the session", async () => {
    vi.mocked(login).mockResolvedValue();
    renderLogin("/login?redirectTo=%2Faccount");

    signIn();

    expect(await screen.findByText("Account")).toBeInTheDocument();
  });

  it("ignores a return address outside the app", async () => {
    vi.mocked(login).mockResolvedValue();
    renderLogin("/login?redirectTo=https%3A%2F%2Fevil.example.com");

    signIn();

    expect(await screen.findByText("Home")).toBeInTheDocument();
  });

  it("asks to check the credentials when the backend rejects them", async () => {
    vi.mocked(login).mockRejectedValue(new HttpError(401, "Unauthorized"));
    renderLogin();

    signIn();

    expect(await screen.findByText("Check your email and password, then try again."))
      .toBeInTheDocument();
  });

  it("shows a general message when the backend cannot answer", async () => {
    vi.mocked(login).mockRejectedValue(new TypeError("Failed to fetch"));
    renderLogin();

    signIn();

    expect(await screen.findByText("Something went wrong. Try again in a moment."))
      .toBeInTheDocument();
  });

  it("sends a signed-in user on to the requested page", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({ username: "alice" } as UserDTO);

    const response = await loginLoader("http://localhost/login?redirectTo=%2Faccount")
      .catch((thrown) => thrown);

    expect(response.headers.get("Location")).toBe("/account");
  });

  it("shows the form when nobody is signed in", async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new HttpError(401, "Unauthorized"));

    await expect(loginLoader("http://localhost/login")).resolves.toBeNull();
  });
});
