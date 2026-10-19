import { fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, useActionData } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser, login, register } from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import RegisterRoute, { clientAction, clientLoader } from "~/routes/register";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/auth/api/auth-service");

const registration = {
  username: "alice",
  displayName: "Alice",
  email: "alice@example.com",
  password: "A secure passphrase 2026!",
};

const routeAction = ({ request }: { request: Request }) =>
  clientAction({ request } as Parameters<typeof clientAction>[0]);

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

function RegisterTestRoute() {
  const actionData = useActionData() as Awaited<ReturnType<typeof clientAction>>;

  return <RegisterRoute actionData={actionData instanceof Response ? undefined : actionData} />;
}

function renderRegister() {
  const router = createMemoryRouter(
    [
      { path: "/register", action: routeAction, Component: RegisterTestRoute },
      { path: "/", Component: () => <p>Home</p> },
      { path: "/login", Component: () => <p>Login page</p> },
    ],
    { initialEntries: ["/register"] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

function createAccount() {
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: registration.displayName } });
  fireEvent.change(screen.getByLabelText("Username"), { target: { value: registration.username } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: registration.email } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: registration.password } });
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));
}

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
});

describe("register route", () => {
  it("creates the account, signs in and opens the home page", async () => {
    vi.mocked(register).mockResolvedValue({ username: "alice" } as UserDTO);
    vi.mocked(login).mockResolvedValue();
    renderRegister();

    createAccount();

    expect(await screen.findByText("Home")).toBeInTheDocument();
    expect(register).toHaveBeenCalledWith(registration);
    expect(login).toHaveBeenCalledWith(registration.email, registration.password);
  });

  it("shows the backend message when the account cannot be created", async () => {
    vi.mocked(register).mockRejectedValue(new HttpError(409, "Username is already in use"));
    renderRegister();

    createAccount();

    expect(await screen.findByText("Username is already in use")).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("shows a general message when the backend cannot answer", async () => {
    vi.mocked(register).mockRejectedValue(new HttpError(500, "Internal Server Error"));
    renderRegister();

    createAccount();

    expect(await screen.findByText("Something went wrong. Try again in a moment."))
      .toBeInTheDocument();
  });

  it("opens the sign-in page when the new account cannot sign in automatically", async () => {
    vi.mocked(register).mockResolvedValue({ username: "alice" } as UserDTO);
    vi.mocked(login).mockRejectedValue(new TypeError("Failed to fetch"));
    renderRegister();

    createAccount();

    expect(await screen.findByText("Login page")).toBeInTheDocument();
  });

  it("sends a signed-in user to the home page", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({ username: "alice" } as UserDTO);

    await expect(clientLoader()).rejects.toHaveProperty("status", 302);
  });
});
