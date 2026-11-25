import { Blob as NodeBlob, File as NodeFile } from "node:buffer";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

import { getCurrentUser, login, logout } from "~/features/auth/api/auth-service";
import { requireUser } from "~/features/auth/session";
import { clientAction as accountAction } from "~/routes/account";
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

// Valid PNG files: a 128 px square, the smallest side the server takes, and a 160 x 128 rectangle.
const SQUARE_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAAAAADmVT4XAAABEklEQVR42u3O4UZDAQCA0Wsyk8xkkslMkslMJjOZTCaZZDKTZJJkMjOZmZnJzEwmSTJJkkmSTJIkyWSSJDMzmcxMkklmMjOzx+jPd57gCIKoSyzp7pHKeuV9/YoBpWpwaFg9otGO6sb0hnHjhGnSPDVtmZm1ztns8wuLjqXllVXnmsvtWff6/IFgaCMcicY241vbO7t7if2Dw6Pj5Mnp2flF6vLq+ub27v4h/Zh5en55fcvm8oX34kepXPn8+q7+/Nbqf41mqy0QIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBP470AF7D8HSZO7hZAAAAABJRU5ErkJggg==";
const RECTANGLE_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAKAAAACACAAAAACpCD3HAAABH0lEQVR42u3O0UYdAAAA0CtJkiRJkmSSJEmSJJMkk0ySJEmSJEmSJEkmSZLMJEkmSZIkSZIkSZIkSZIkSZIkSZL0CT30er7gnEAgKDgkNCw8IjIqOiY2Lj4h8UdSckpqWnpGZlZ2Tm7ez/yCwqJfxSW/S8vKKyqrqmtq6+obGpuaW1rb2js6u7p7ev/09Q8MDg2P/P03OjY+Mfl/anpmdm5+YXFpeWV1bX1jc2t7Z3dv/+Dw6Pjk9Oz84vLq+ub27v7h8en55fXt/eMrPyAoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj43eAnbdqx8C/FX8sAAAAASUVORK5CYII=";

// Node's Request and fetch only read Node's FormData and Blob, and jsdom replaces them with its own.
function useNodeFormData() {
  vi.stubGlobal("FormData", NodeFormData);
  vi.stubGlobal("Blob", NodeBlob);
  vi.stubGlobal("File", NodeFile);
}
const NodeFormData = (await new Response(new URLSearchParams()).formData()).constructor as typeof FormData;

function submitPhoto(base64: string) {
  const body = new NodeFormData();
  body.set("intent", "avatar");
  body.set("avatar", new NodeBlob([Buffer.from(base64, "base64")], { type: "image/png" }) as Blob, "avatar.png");
  return { request: new Request(new URL("/account", window.location.origin), { method: "POST", body }) };
}

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

  it("renews an expired access token with the refresh token", async () => {
    cookies.set("AuthToken", "stale-or-expired-token");

    await expect(getCurrentUser()).resolves.toMatchObject({ username: account.username });
    expect(cookies.get("AuthToken")).not.toBe("stale-or-expired-token");
  });

  it("sends a session the backend rejects to the login, remembering the page", async () => {
    cookies.set("AuthToken", "stale-or-expired-token");
    cookies.set("RefreshToken", "stale-or-expired-token");

    const redirect = await requireUser(new Request(new URL("/account", window.location.origin)))
      .catch((thrown) => thrown);

    expect(redirect.headers.get("Location")).toBe("/login?redirectTo=%2Faccount");
  });

  describe("account editing", () => {
    const newPassword = "Another integration password 2027!";
    const changedEmail = `changed.${account.email}`;

    // The previous test leaves a rejected session behind.
    beforeAll(() => login(account.email, account.password));

    it("saves the profile through the real API", async () => {
      await expect(accountAction(submit("/account", {
        intent: "profile",
        displayName: "Renamed User",
        username: `${account.username}_2`,
      }) as never)).resolves.toEqual({ saved: true });

      await expect(getCurrentUser()).resolves.toMatchObject({
        displayName: "Renamed User",
        username: `${account.username}_2`,
      });
    });

    it("represents a wrong current password and a taken email", async () => {
      await expect(accountAction(submit("/account", {
        intent: "email",
        email: changedEmail,
        currentPassword: "Wrong integration password 2026!",
      }) as never)).resolves.toEqual({ error: "Current password is incorrect" });

      const other = `other_${suffix}`;
      await logout();
      await registerAction(submit("/register", {
        ...account,
        username: other,
        email: `${other}@example.test`,
      }) as never);
      await expect(accountAction(submit("/account", {
        intent: "email",
        email: account.email,
        currentPassword: account.password,
      }) as never)).resolves.toEqual({ error: "Email is already in use" });
      await logout();
      await login(account.email, account.password);
    });

    it("changes the email and password and keeps this session", async () => {
      await expect(accountAction(submit("/account", {
        intent: "email",
        email: changedEmail,
        currentPassword: account.password,
      }) as never)).resolves.toEqual({ saved: true });
      await expect(getCurrentUser()).resolves.toMatchObject({ email: changedEmail });

      await expect(accountAction(submit("/account", {
        intent: "password",
        currentPassword: account.password,
        newPassword,
      }) as never)).resolves.toEqual({ saved: true });
      await expect(getCurrentUser()).resolves.toMatchObject({ email: changedEmail });

      await logout();
      await expect(login(changedEmail, account.password)).rejects.toMatchObject({ status: 401 });
      await login(changedEmail, newPassword);
      await expect(getCurrentUser()).resolves.toMatchObject({ email: changedEmail });
    });

    it("uploads, refuses and removes the profile photo through the real API and storage", async () => {
      useNodeFormData();

      await expect(accountAction(submitPhoto(SQUARE_PNG) as never)).resolves.toEqual({ saved: true });
      const { avatarUrl } = await getCurrentUser();
      expect(avatarUrl).toMatch(/^\/api\/v1\/users\/me\/avatar\?v=/);
      const image = await fetch(avatarUrl!);
      expect(image.status).toBe(200);
      expect(image.headers.get("Content-Type")).toBe("image/png");

      await expect(accountAction(submitPhoto(RECTANGLE_PNG) as never))
        .resolves.toEqual({ error: "The avatar must be square" });
      await expect(getCurrentUser()).resolves.toMatchObject({ avatarUrl });

      await expect(accountAction(submit("/account", { intent: "remove-avatar" }) as never))
        .resolves.toEqual({ saved: true });
      await expect(getCurrentUser()).resolves.toMatchObject({ avatarUrl: null });
      expect((await fetch(avatarUrl!)).status).toBe(404);
    });

    it("sends a session the API revoked to the login", async () => {
      const revoked = ["AuthToken", "RefreshToken"].map((name) => [name, cookies.get(name)!] as const);
      await expect(accountAction(submit("/account", {
        intent: "password",
        currentPassword: newPassword,
        newPassword: `${newPassword}x`,
      }) as never)).resolves.toEqual({ saved: true });
      revoked.forEach(([name, value]) => cookies.set(name, value));

      const denied = await accountAction(submit("/account", {
        intent: "profile",
        displayName: "Not Saved",
      }) as never).catch((response: unknown) => response);

      expect(denied).toBeInstanceOf(Response);
      expect((denied as Response).headers.get("Location")).toBe("/login?redirectTo=%2Faccount");
    });
  });
});
