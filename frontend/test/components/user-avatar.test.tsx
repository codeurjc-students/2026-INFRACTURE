import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import UserAvatar, { initials } from "~/components/user-avatar";

describe("user avatar", () => {
  it("shows the photo when the account has one", () => {
    const { container } = render(
      <UserAvatar user={{ displayName: "Alice Cooper", avatarUrl: "/api/v1/users/me/avatar?v=1" }} />,
    );

    expect(container.querySelector("img")).toHaveAttribute("src", "/api/v1/users/me/avatar?v=1");
    expect(screen.queryByText("AC")).not.toBeInTheDocument();
  });

  it("shows the initials without a photo", () => {
    render(<UserAvatar user={{ displayName: "Alice Cooper", avatarUrl: null }} />);

    expect(screen.getByText("AC")).toBeInTheDocument();
  });

  it("falls back to the initials when the photo fails, and tries a new photo again", () => {
    const { container, rerender } = render(
      <UserAvatar user={{ displayName: "Alice", avatarUrl: "/api/v1/users/me/avatar?v=1" }} />,
    );

    fireEvent.error(container.querySelector("img")!);
    expect(screen.getByText("A")).toBeInTheDocument();

    rerender(<UserAvatar user={{ displayName: "Alice", avatarUrl: "/api/v1/users/me/avatar?v=2" }} />);
    expect(container.querySelector("img")).toHaveAttribute("src", "/api/v1/users/me/avatar?v=2");
  });

  it("takes the first letter of up to two words", () => {
    expect(initials("  ada   lovelace byron ")).toBe("AL");
  });
});
