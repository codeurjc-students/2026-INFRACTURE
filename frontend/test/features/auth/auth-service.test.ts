import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser, login, logout, register } from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";

const user: UserDTO = {
  id: 1,
  username: "alice",
  displayName: "Alice",
  email: "alice@example.com",
  role: "USER",
  active: true,
  avatarKey: null,
  createdAt: "2026-10-17T10:00:00Z",
  updatedAt: "2026-10-17T10:00:00Z",
};

function requestOf(fetch: ReturnType<typeof vi.fn>) {
  const [path, init] = fetch.mock.calls[0] as [string, RequestInit];
  return { path, method: init.method ?? "GET", body: init.body };
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=token-123";
});

afterEach(() => {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  vi.unstubAllGlobals();
});

describe("auth service", () => {
  it("signs in with the email and password", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ status: "SUCCESS" }));
    vi.stubGlobal("fetch", fetch);

    await expect(login("alice@example.com", "A secure passphrase 2026!")).resolves.toBeUndefined();

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/auth/login",
      method: "POST",
      body: JSON.stringify({ email: "alice@example.com", password: "A secure passphrase 2026!" }),
    });
  });

  it("registers the account and returns the created user", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(user, { status: 201 }));
    vi.stubGlobal("fetch", fetch);
    const registration = {
      username: "alice",
      displayName: "Alice",
      email: "alice@example.com",
      password: "A secure passphrase 2026!",
    };

    await expect(register(registration)).resolves.toEqual(user);

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/users/",
      method: "POST",
      body: JSON.stringify(registration),
    });
  });

  it("signs out without a body", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);

    await expect(logout()).resolves.toBeUndefined();

    expect(requestOf(fetch)).toEqual({ path: "/api/v1/auth/logout", method: "POST", body: undefined });
  });

  it("returns the signed-in user", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(user));
    vi.stubGlobal("fetch", fetch);

    await expect(getCurrentUser()).resolves.toEqual(user);

    expect(requestOf(fetch)).toMatchObject({ path: "/api/v1/users/me", method: "GET" });
  });
});
