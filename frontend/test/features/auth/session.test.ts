import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser } from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import { requireUser, safeRedirect } from "~/features/auth/session";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/auth/api/auth-service");

afterEach(() => {
  vi.resetAllMocks();
});

describe("safeRedirect", () => {
  it.each(["/account", "/labs/42?tab=run", "/"])("keeps the app path %s", (target) => {
    expect(safeRedirect(target)).toBe(target);
  });

  it.each([
    "https://evil.example.com",
    "//evil.example.com",
    "/\\evil.example.com",
    "javascript:alert(1)",
    "account",
    "",
    null,
    undefined,
  ])("falls back to the home page for %s", (target) => {
    expect(safeRedirect(target)).toBe("/");
  });
});

describe("requireUser", () => {
  const request = new Request("http://localhost/account?tab=security");

  it("returns the signed-in user", async () => {
    const user = { username: "alice" } as UserDTO;
    vi.mocked(getCurrentUser).mockResolvedValue(user);

    await expect(requireUser(request)).resolves.toBe(user);
  });

  it("sends a visitor without a session to the login with the page to return to", async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new HttpError(401, "Unauthorized"));

    const response = await requireUser(request).catch((thrown) => thrown);

    expect(response).toBeInstanceOf(Response);
    expect(response.headers.get("Location")).toBe("/login?redirectTo=%2Faccount%3Ftab%3Dsecurity");
  });

  it("lets other failures reach the error boundary", async () => {
    const failure = new HttpError(500, "Internal Server Error");
    vi.mocked(getCurrentUser).mockRejectedValue(failure);

    await expect(requireUser(request)).rejects.toBe(failure);
  });
});
