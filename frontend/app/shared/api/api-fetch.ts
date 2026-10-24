const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const AUTH_PATH = "/api/v1/auth/";
const CSRF_PATH = "/api/v1/auth/csrf";
const REFRESH_PATH = "/api/v1/auth/refresh";
const SAFE_METHODS = ["GET", "HEAD", "OPTIONS"];
const GENERIC_ERROR = "Something went wrong. Try again in a moment.";

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "HttpError";
  }
}

function readCookie(name: string) {
  return document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

// Errors come as ProblemDetail JSON with the detail field
function problemDetail(response: Response, text: string): string | undefined {
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.startsWith("application/problem+json")) {
    return undefined;
  }
  try {
    return JSON.parse(text).detail;
  } catch {
    return undefined;
  }
}

// It is needed to call the crsf endpoint before any write request, otherwise the server will reject the request with a 403 error.
// after the first call, the cookie is set and we can read it from the cookie.
async function csrfToken() {
  if (!readCookie(CSRF_COOKIE)) {
    await fetch(CSRF_PATH);
  }
  return readCookie(CSRF_COOKIE);
}

let renewal: Promise<boolean> | null = null;

function renewSession(): Promise<boolean> {
  renewal ??= send(REFRESH_PATH, { method: "POST" })
    .then((response) => response.ok)
    .finally(() => {
      renewal = null;
    });
  return renewal;
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let response = await send(path, init);
  if (
    response.status === 401 &&
    !path.startsWith(AUTH_PATH) &&
    (await renewSession())
  ) {
    response = await send(path, init);
  }
  //we use text in order to be able to accpet no content responses (204) and parse them as undefined, or 502 errors that are not json and have no detail
  const text = await response.text();

  if (!response.ok) {
    throw new HttpError(
      response.status,
      problemDetail(response, text) ?? GENERIC_ERROR,
    );
  }
  return (text ? JSON.parse(text) : undefined) as T;
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  const method = (init.method ?? "GET").toUpperCase();
  //if is not a read method --> then the CSRF token is needed
  if (!SAFE_METHODS.includes(method)) {
    const token = await csrfToken();
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  return fetch(path, { ...init, headers });
}
