import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import PasswordInput from "~/features/auth/components/password-input";

describe("password input", () => {
  it("shows and hides the password without losing what was typed", () => {
    render(<PasswordInput aria-label="Password" defaultValue="Correct-Horse-42" />);
    const input = screen.getByLabelText("Password");

    expect(input).toHaveAttribute("type", "password");
    expect(input).toHaveAttribute("maxLength", "128");

    fireEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(input).toHaveAttribute("type", "text");
    expect(input).toHaveValue("Correct-Horse-42");

    const hide = screen.getByRole("button", { name: "Hide password" });
    expect(hide).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(hide);
    expect(input).toHaveAttribute("type", "password");
  });
});
