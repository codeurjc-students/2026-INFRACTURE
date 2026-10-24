import { afterEach, describe, expect, it, vi } from "vitest";

import { apiFetch, HttpError } from "~/shared/api/api-fetch";

function problem(status: number, detail: string) {
  return new Response(JSON.stringify({ status, detail }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });
}

function clearCsrfCookie() {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
}

afterEach(() => {
  clearCsrfCookie();
  vi.unstubAllGlobals();
});

describe("apiFetch", () => {
  it("returns the parsed JSON body of a successful response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ name: "Ada" })),
    );

    await expect(apiFetch("/api/v1/users/me")).resolves.toEqual({
      name: "Ada",
    });
  });

  it("returns undefined for a response with no body", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    );

    await expect(
      apiFetch("/api/v1/auth/logout", { method: "POST" }),
    ).resolves.toBeUndefined();
  });

  it("throws an HttpError with the status and the ProblemDetail message of a failed response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(problem(409, "Username is already in use")),
    );

    const error = await apiFetch("/api/v1/users", {
      method: "POST",
      body: "{}",
    }).catch((e) => e);

    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({
      status: 409,
      message: "Username is already in use",
    });
  });

  it("ignores a detail field when a failed response is not ProblemDetail", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          Response.json({ detail: "Internal stack trace" }, { status: 500 }),
        ),
    );

    await expect(apiFetch("/api/v1/users/me")).rejects.toMatchObject({
      status: 500,
      message: "Something went wrong. Try again in a moment.",
    });
  });

  it("uses a generic message when a failed response is not JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response("<html>Bad Gateway</html>", {
          status: 502,
          statusText: "Bad Gateway",
        }),
      ),
    );

    await expect(apiFetch("/api/v1/users/me")).rejects.toMatchObject({
      status: 502,
      message: "Something went wrong. Try again in a moment.",
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

    const csrfCalls = fetch.mock.calls.filter(
      ([input]) => input === "/api/v1/auth/csrf",
    );
    const loginHeaders = new Headers(
      (fetch.mock.calls[1] as unknown as [string, RequestInit])[1].headers,
    );
    expect(csrfCalls).toHaveLength(1);
    expect(loginHeaders.get("X-XSRF-TOKEN")).toBe("token-123");
    expect(loginHeaders.get("Content-Type")).toBe("application/json");
  });

  it("renews an expired session once and repeats the request", async () => {
    document.cookie = "XSRF-TOKEN=token-123";
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(problem(401, "Authentication is required."))
      .mockResolvedValueOnce(Response.json({ status: "SUCCESS" }))
      .mockResolvedValueOnce(Response.json({ name: "Ada" }));
    vi.stubGlobal("fetch", fetch);

    await expect(apiFetch("/api/v1/users/me")).resolves.toEqual({
      name: "Ada",
    });
    expect(fetch.mock.calls.map(([input]) => input)).toEqual([
      "/api/v1/users/me",
      "/api/v1/auth/refresh",
      "/api/v1/users/me",
    ]);
  });

  it("keeps the 401 when the session cannot be renewed", async () => {
    document.cookie = "XSRF-TOKEN=token-123";
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(problem(401, "Authentication is required."))
      .mockResolvedValueOnce(problem(401, "Authentication is required."));
    vi.stubGlobal("fetch", fetch);

    await expect(apiFetch("/api/v1/users/me")).rejects.toMatchObject({
      status: 401,
      message: "Authentication is required.",
    });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("does not renew the session for authentication requests", async () => {
    document.cookie = "XSRF-TOKEN=token-123";
    const fetch = vi
      .fn()
      .mockResolvedValue(problem(401, "Invalid email or password."));
    vi.stubGlobal("fetch", fetch);

    await expect(
      apiFetch("/api/v1/auth/login", { method: "POST", body: "{}" }),
    ).rejects.toMatchObject({ status: 401 });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("renews the session once for requests that expire together", async () => {
    document.cookie = "XSRF-TOKEN=token-123";
    let renewed = false;
    const fetch = vi.fn((input: RequestInfo | URL) => {
      if (input === "/api/v1/auth/refresh") {
        renewed = true;
        return Promise.resolve(Response.json({ status: "SUCCESS" }));
      }
      return Promise.resolve(
        renewed
          ? Response.json({ ok: true })
          : problem(401, "Authentication is required."),
      );
    });
    vi.stubGlobal("fetch", fetch);

    await Promise.all([
      apiFetch("/api/v1/users/me"),
      apiFetch("/api/v1/projects"),
    ]);

    const refreshCalls = fetch.mock.calls.filter(
      ([input]) => input === "/api/v1/auth/refresh",
    );
    expect(refreshCalls).toHaveLength(1);
  });

  it("does not ask for a CSRF token on reads", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json([]));
    vi.stubGlobal("fetch", fetch);

    await apiFetch("/api/v1/component-templates");

    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
