import type { Route } from "./+types/login";
import { redirect, useNavigation } from "react-router";
import { getCurrentUser, login } from "~/features/auth/api/auth-service";
import AuthLayout from "~/features/auth/components/auth-layout";
import LoginForm from "~/features/auth/components/login-form";
import { safeRedirect } from "~/features/auth/session";
import { HttpError } from "~/shared/api/api-fetch";

export function meta() {
  return [{ title: "Sign in | Infracture" }];
}

function nextPage(request: Request) {
  return safeRedirect(new URL(request.url).searchParams.get("redirectTo"));
}

export async function clientLoader({ request }: Route.ClientLoaderArgs) {
  const user = await getCurrentUser().catch(() => null);
  if (user) {
    throw redirect(nextPage(request));
  }
  return null;
}

export async function clientAction({ request }: Route.ClientActionArgs) {
  const form = await request.formData();
  try {
    await login(String(form.get("email")), String(form.get("password")));
  } catch (error) {
    return {
      error:
        error instanceof HttpError && error.status === 401
          ? "Check your email and password, then try again."
          : "Something went wrong. Try again in a moment.",
    };
  }
  return redirect(nextPage(request));
}

export default function LoginRoute({ actionData }: Pick<Route.ComponentProps, "actionData">) {
  const navigation = useNavigation();

  return (
    <AuthLayout
      headline={["Break it", "on purpose."]}
      title="Sign in"
      description="Use the email and password of your Infracture account."
    >
      <LoginForm error={actionData?.error} pending={navigation.state === "submitting"} />
    </AuthLayout>
  );
}
