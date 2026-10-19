import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import LoginForm from "~/features/auth/components/login-form";
import RegisterForm from "~/features/auth/components/register-form";

function renderInRouter(form: ReactElement) {
  render(<RouterProvider router={createMemoryRouter([{ path: "/", element: form }])} />);
}

describe("auth forms while submitting", () => {
  it.each([
    { name: "login", form: <LoginForm pending />, idle: "Sign in", busy: "Signing in…" },
    { name: "register", form: <RegisterForm pending />, idle: "Create account", busy: "Creating account…" },
  ])("disables the $name button so it cannot be sent twice", ({ form, idle, busy }) => {
    renderInRouter(form);

    expect(screen.getByRole("button", { name: new RegExp(busy) })).toBeDisabled();
    expect(screen.queryByRole("button", { name: idle })).not.toBeInTheDocument();
  });
});
