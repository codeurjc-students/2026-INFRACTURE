import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ErrorBoundary, links } from "~/root";

function renderError(error: unknown) {
  render(
    ErrorBoundary({
      error,
    } as Parameters<typeof ErrorBoundary>[0]),
  );
}

describe("application root", () => {
  it("declares the application font resources", () => {
    expect(links()).toContainEqual(
      expect.objectContaining({
        rel: "stylesheet",
        href: expect.stringContaining("fonts.googleapis.com"),
      }),
    );
  });

  it("renders a not-found message for a 404 route response", () => {
    renderError({
      status: 404,
      statusText: "Not Found",
      internal: true,
      data: "",
    });

    expect(screen.getByRole("heading", { name: "404" })).toBeInTheDocument();
    expect(screen.getByText("The requested page could not be found."))
      .toBeInTheDocument();
  });

  it("renders the diagnostic details of an unexpected development error", () => {
    renderError(new Error("Unexpected catalogue failure"));

    expect(screen.getByRole("heading", { name: "Oops!" })).toBeInTheDocument();
    expect(screen.getByText("Unexpected catalogue failure"))
      .toBeInTheDocument();
  });
});
