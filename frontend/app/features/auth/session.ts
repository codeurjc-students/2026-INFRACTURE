import { redirect } from "react-router";
import { HttpError } from "~/shared/api/api-fetch";
import { getCurrentUser } from "./api/auth-service";

export function safeRedirect(target: string | null | undefined, fallback = "/") {
  return target?.startsWith("/") && !target.startsWith("//") && !target.startsWith("/\\")
    ? target
    : fallback;
}

export async function requireUser(request: Request) {
  try {
    return await getCurrentUser();
  } catch (error) {
    if (error instanceof HttpError && error.status === 401) {
      const { pathname, search } = new URL(request.url);
      throw redirect(`/login?${new URLSearchParams({ redirectTo: pathname + search })}`);
    }
    throw error;
  }
}
