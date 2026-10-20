import { afterEach, describe, expect, it, vi } from "vitest";

import { apiFetch, HttpError } from "~/shared/api/api-fetch";

function clearCsrfCookie() {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
}

afterEach(() => {
  clearCsrfCookie();
  vi.unstubAllGlobals();
});

describe("apiFetch", () => {
  it("returns the parsed JSON body of a successful response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ name: "Ada" })));

    await expect(apiFetch("/api/v1/users/me")).resolves.toEqual({ name: "Ada" });
  });

  it("returns undefined for a response with no body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(apiFetch("/api/v1/auth/logout", { method: "POST" })).resolves.toBeUndefined();
  });

  it("throws an HttpError with the status and the ProblemDetail message of a failed response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        Response.json({ status: 409, detail: "Username is already in use" }, { status: 409 }),
      ),
    );

    const error = await apiFetch("/api/v1/users", { method: "POST", body: "{}" }).catch((e) => e);

    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({ status: 409, message: "Username is already in use" });
  });

  it("falls back to the status text when a failed response is not ProblemDetail JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response("<html>Bad Gateway</html>", { status: 502, statusText: "Bad Gateway" }),
      ),
    );

    await expect(apiFetch("/api/v1/users/me")).rejects.toMatchObject({
      status: 502,
      message: "Bad Gateway",
    });
  });

  it("fetches the CSRF cookie once and sends it as a header on writes", async () => {
    const fetch = vi.fn((input: RequestInfo | URL) => {
      if (input === "/api/v1/auth/csrf") {
        document.cookie = "XSRF-TOKEN=token-123";
      }
      return Promise.resolve(new Response(null, { status: 204 }));
    });
    vi.stubGlobal("fetch", fetch);

    await apiFetch("/api/v1/auth/login", { method: "POST", body: "{}" });
    await apiFetch("/api/v1/auth/logout", { method: "POST" });

    const csrfCalls = fetch.mock.calls.filter(([input]) => input === "/api/v1/auth/csrf");
    const loginHeaders = new Headers((fetch.mock.calls[1] as unknown as [string, RequestInit])[1].headers);
    expect(csrfCalls).toHaveLength(1);
    expect(loginHeaders.get("X-XSRF-TOKEN")).toBe("token-123");
    expect(loginHeaders.get("Content-Type")).toBe("application/json");
  });

  it("does not ask for a CSRF token on reads", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json([]));
    vi.stubGlobal("fetch", fetch);

    await apiFetch("/api/v1/component-templates");

    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
