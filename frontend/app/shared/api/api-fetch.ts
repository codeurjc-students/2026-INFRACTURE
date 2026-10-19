const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_PATH = "/api/v1/auth/csrf";
const SAFE_METHODS = ["GET", "HEAD", "OPTIONS"];

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

// Errors come as JSON with the detail field
function problemDetail(text: string): string | undefined {
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

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
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

  const response = await fetch(path, { ...init, headers });
  //we use text in order to be able to accpet no content responses (204) and parse them as undefined, or 502 errors that are not json and have no detail
  const text = await response.text();

  if (!response.ok) {
    throw new HttpError(
      response.status,
      problemDetail(text) ?? response.statusText,
    );
  }
  return (text ? JSON.parse(text) : undefined) as T;
}
