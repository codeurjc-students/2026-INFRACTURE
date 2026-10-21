import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

import { getCurrentUser } from "~/features/auth/api/auth-service";
import { requireUser } from "~/features/auth/session";
import { clientAction as loginAction } from "~/routes/login";
import { clientAction as logoutAction } from "~/routes/logout";
import { clientAction as registerAction } from "~/routes/register";

const nodeFetch = globalThis.fetch;
const cookies = new Map<string, string>();
const suffix = Date.now().toString(36);
const account = {
  username: `it_${suffix}`,
  displayName: "Integration User",
  email: `it.${suffix}@example.test`,
  password: "A secure integration password 2026!",
};

beforeAll(() => {
  vi.stubGlobal("fetch", async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    if (cookies.size > 0) {
      headers.set("Cookie", [...cookies].map(([name, value]) => `${name}=${value}`).join("; "));
    }
    const response = await nodeFetch(new URL(String(input), window.location.origin), { ...init, headers });
    for (const setCookie of response.headers.getSetCookie()) {
      const pair = setCookie.split(";")[0];
      const name = pair.slice(0, pair.indexOf("="));
      const value = pair.slice(pair.indexOf("=") + 1);
      if (value) cookies.set(name, value);
      else cookies.delete(name);
      if (!/httponly/i.test(setCookie)) document.cookie = setCookie;
    }
    return response;
  });
});

afterAll(() => vi.unstubAllGlobals());

function submit(path: string, fields: Record<string, string> = {}) {
  const body = new FormData();
  Object.entries(fields).forEach(([name, value]) => body.set(name, value));
  return { request: new Request(new URL(path, window.location.origin), { method: "POST", body }) };
}

describe("auth client against the real API", () => {
  it("registers, signs in automatically, reads the account and signs out", async () => {
    const registered = await registerAction(submit("/register", account) as never);
    expect(registered).toBeInstanceOf(Response);
    expect((registered as Response).headers.get("Location")).toBe("/");

    await expect(getCurrentUser()).resolves.toMatchObject({
      username: account.username,
      email: account.email,
      role: "USER",
    });

    const signedOut = (await logoutAction()) as Response;
    expect(signedOut.headers.get("Location")).toBe("/login");
    expect(cookies.has("AuthToken")).toBe(false);
    await expect(getCurrentUser()).rejects.toMatchObject({
      status: 401,
      message: "Authentication is required.",
    });
  });

  it("shows the backend message for a duplicate account", async () => {
    await expect(registerAction(submit("/register", account) as never))
      .resolves.toEqual({ error: "Username is already in use" });
  });

  it("asks to check the credentials when the password is wrong", async () => {
    await expect(loginAction(
      submit("/login", { email: account.email, password: "Wrong integration password 2026!" }) as never,
    )).resolves.toEqual({ error: "Check your email and password, then try again." });
  });

  it("signs in and returns to the page that asked for the session", async () => {
    const signedIn = (await loginAction(
      submit("/login?redirectTo=%2Faccount", { email: account.email, password: account.password }) as never,
    )) as Response;

    expect(signedIn.headers.get("Location")).toBe("/account");
    await expect(requireUser(new Request(new URL("/account", window.location.origin))))
      .resolves.toMatchObject({ username: account.username });
  });

  it("sends a session the backend rejects to the login, remembering the page", async () => {
    cookies.set("AuthToken", "stale-or-expired-token");

    const redirect = await requireUser(new Request(new URL("/account", window.location.origin)))
      .catch((thrown) => thrown);

    expect(redirect.headers.get("Location")).toBe("/login?redirectTo=%2Faccount");
  });
});
