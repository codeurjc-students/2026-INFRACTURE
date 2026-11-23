import { fireEvent, render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  changeEmail,
  changePassword,
  getCurrentUser,
  updateProfile,
} from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import AccountRoute, { clientAction as accountAction } from "~/routes/account";
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
        children: [
          {
            path: "/account",
            Component: AccountRoute,
            action: ({ request }) =>
              accountAction({ request } as Parameters<typeof accountAction>[0]),
          },
        ],
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

const account = {
  id: 7,
  username: "alice",
  displayName: "Alice",
  email: "alice@example.com",
} as UserDTO;

function fill(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
}

async function section(name: string) {
  return within(await screen.findByRole("region", { name }));
}

describe("account forms", () => {
  it("saves the profile and shows the refreshed account", async () => {
    const saved = { ...account, displayName: "Alice Cooper", username: "alice_2" };
    vi.mocked(getCurrentUser).mockResolvedValueOnce(account).mockResolvedValue(saved);
    vi.mocked(updateProfile).mockResolvedValue(saved);
    renderAccount();
    const profile = await section("Profile");

    fill(profile.getByLabelText("Name"), "Alice Cooper");
    fill(profile.getByLabelText("Username"), "ALICE_2");
    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await profile.findByText("Profile saved")).toBeInTheDocument();
    expect(profile.getByRole("status")).toHaveFocus();
    expect(updateProfile).toHaveBeenCalledWith({ displayName: "Alice Cooper", username: "ALICE_2" });
    expect(await screen.findByText("alice_2")).toBeInTheDocument();
    expect(profile.getByLabelText("Username")).toHaveValue("alice_2");
  });

  it("keeps the new email, clears the password and shows the conflict without success", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(changeEmail).mockRejectedValue(new HttpError(409, "Email is already in use"));
    renderAccount();
    const email = await section("Email");

    fill(email.getByLabelText("New email"), "taken@example.com");
    fill(email.getByLabelText("Current password"), "my current secret");
    fireEvent.click(email.getByRole("button", { name: "Change email" }));

    expect(await email.findByRole("alert")).toHaveTextContent("Email is already in use");
    expect(email.queryByRole("status")).not.toBeInTheDocument();
    expect(email.getByLabelText("New email")).toHaveValue("taken@example.com");
    expect(email.getByLabelText("Current password")).toHaveValue("");
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("clears both passwords after a rejected and after a saved change", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(changePassword)
      .mockRejectedValueOnce(new HttpError(400, "Current password is incorrect"))
      .mockResolvedValueOnce(account);
    renderAccount();
    const password = await section("Password");

    fill(password.getByLabelText("Current password"), "wrong secret");
    fill(password.getByLabelText("New password"), "A new secret 2027!");
    fireEvent.click(password.getByRole("button", { name: "Change password" }));

    expect(await password.findByRole("alert")).toHaveTextContent("Current password is incorrect");
    expect(password.getByLabelText("Current password")).toHaveValue("");
    expect(password.getByLabelText("New password")).toHaveValue("");

    fill(password.getByLabelText("Current password"), "right secret");
    fill(password.getByLabelText("New password"), "A new secret 2027!");
    fireEvent.click(password.getByRole("button", { name: "Change password" }));

    expect(await password.findByText("Password changed")).toBeInTheDocument();
    expect(password.getByRole("status")).toHaveFocus();
    expect(password.queryByRole("alert")).not.toBeInTheDocument();
    expect(password.getByLabelText("Current password")).toHaveValue("");
    expect(password.getByLabelText("New password")).toHaveValue("");
  });

  it("moves keyboard focus to the result of the save", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(updateProfile).mockRejectedValue(new HttpError(409, "Username is already in use"));
    renderAccount();
    const profile = await section("Profile");

    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await profile.findByRole("alert")).toHaveFocus();
  });

  it("does not show a server failure as a saved change", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(updateProfile).mockRejectedValue(new HttpError(500, "Internal detail"));
    renderAccount();
    const profile = await section("Profile");

    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await profile.findByRole("alert")).toHaveTextContent("Something went wrong");
    expect(profile.queryByText("Internal detail")).not.toBeInTheDocument();
    expect(profile.queryByRole("status")).not.toBeInTheDocument();
  });

  it("sends an expired session to the login and back to the account", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(updateProfile).mockRejectedValue(new HttpError(401, "Authentication is required."));
    renderAccount();
    const profile = await section("Profile");

    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await screen.findByText("Login page /account")).toBeInTheDocument();
  });
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
