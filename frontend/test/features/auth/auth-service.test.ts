import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  changeEmail,
  changePassword,
  getCurrentUser,
  login,
  logout,
  register,
  removeAvatar,
  updateProfile,
  uploadAvatar,
} from "~/features/auth/api/auth-service";
import type { UserDTO } from "~/features/auth/model/UserDTO";

const user: UserDTO = {
  id: 1,
  username: "alice",
  displayName: "Alice",
  email: "alice@example.com",
  role: "USER",
  active: true,
  avatarUrl: null,
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
    const fetch = vi
      .fn()
      .mockResolvedValue(Response.json({ status: "SUCCESS" }));
    vi.stubGlobal("fetch", fetch);

    await expect(
      login("alice@example.com", "A secure passphrase 2026!"),
    ).resolves.toBeUndefined();

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/auth/login",
      method: "POST",
      body: JSON.stringify({
        email: "alice@example.com",
        password: "A secure passphrase 2026!",
      }),
    });
  });

  it("registers the account and returns the created user", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(Response.json(user, { status: 201 }));
    vi.stubGlobal("fetch", fetch);
    const registration = {
      username: "alice",
      displayName: "Alice",
      email: "alice@example.com",
      password: "A secure passphrase 2026!",
    };

    await expect(register(registration)).resolves.toEqual(user);

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/users",
      method: "POST",
      body: JSON.stringify(registration),
    });
  });

  it("signs out without a body", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);

    await expect(logout()).resolves.toBeUndefined();

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/auth/logout",
      method: "POST",
      body: undefined,
    });
  });

  it("returns the signed-in user", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(user));
    vi.stubGlobal("fetch", fetch);

    await expect(getCurrentUser()).resolves.toEqual(user);

    expect(requestOf(fetch)).toMatchObject({
      path: "/api/v1/users/me",
      method: "GET",
    });
  });

  it.each([
    ["PATCH", "/api/v1/users/me", () => updateProfile({ displayName: "Alice" }), { displayName: "Alice" }],
    [
      "PUT",
      "/api/v1/users/me/email",
      () => changeEmail({ email: "new@example.com", currentPassword: "secret" }),
      { email: "new@example.com", currentPassword: "secret" },
    ],
    [
      "PUT",
      "/api/v1/users/me/password",
      () => changePassword({ currentPassword: "secret", newPassword: "new secret" }),
      { currentPassword: "secret", newPassword: "new secret" },
    ],
  ] as const)("sends the owner edit %s %s", async (method, path, save, body) => {
    const fetch = vi.fn().mockResolvedValue(Response.json(user));
    vi.stubGlobal("fetch", fetch);

    await expect(save()).resolves.toEqual(user);

    expect(requestOf(fetch)).toEqual({ path, method, body: JSON.stringify(body) });
  });

  it("uploads the avatar as a multipart file with the CSRF token", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(user));
    vi.stubGlobal("fetch", fetch);
    const image = new Blob(["png bytes"], { type: "image/png" });

    await expect(uploadAvatar(image)).resolves.toEqual(user);

    const [path, init] = fetch.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/v1/users/me/avatar");
    expect(init.method).toBe("PUT");
    const file = (init.body as FormData).get("file") as File;
    expect(file.name).toBe("avatar.png");
    expect(await file.text()).toBe("png bytes");
    const headers = new Headers(init.headers);
    expect(headers.get("X-XSRF-TOKEN")).toBe("token-123");
    // The browser writes the multipart boundary; a JSON type would break the upload.
    expect(headers.has("Content-Type")).toBe(false);
  });

  it("removes the avatar", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(user));
    vi.stubGlobal("fetch", fetch);

    await expect(removeAvatar()).resolves.toEqual(user);

    expect(requestOf(fetch)).toEqual({
      path: "/api/v1/users/me/avatar",
      method: "DELETE",
      body: undefined,
    });
  });
});
