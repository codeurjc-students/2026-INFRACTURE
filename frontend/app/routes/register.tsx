import type { Route } from "./+types/register";
import { redirect, useNavigation } from "react-router";
import {
  getCurrentUser,
  login,
  register,
} from "~/features/auth/api/auth-service";
import AuthLayout from "~/features/auth/components/auth-layout";
import RegisterForm from "~/features/auth/components/register-form";
import { HttpError } from "~/shared/api/api-fetch";

export function meta() {
  return [{ title: "Sign up | Infracture" }];
}

export async function clientLoader() {
  const user = await getCurrentUser().catch(() => null);
  if (user) {
    throw redirect("/");
  }
  return null;
}

export async function clientAction({ request }: Route.ClientActionArgs) {
  const form = await request.formData();
  const user = {
    username: String(form.get("username")),
    displayName: String(form.get("displayName")),
    email: String(form.get("email")),
    password: String(form.get("password")),
  };
  try {
    await register(user);
  } catch (error) {
    return {
      error:
        error instanceof HttpError &&
        (error.status === 400 || error.status === 409)
          ? error.message
          : "Something went wrong. Try again in a moment.",
    };
  }
  try {
    await login(user.email, user.password);
  } catch {
    return redirect("/login");
  }
  return redirect("/");
}

export default function RegisterRoute({
  actionData,
}: Pick<Route.ComponentProps, "actionData">) {
  const navigation = useNavigation();

  return (
    <AuthLayout
      headline={["Build it.", "Then break it."]}
      title="Sign up"
      description="Create your Infracture account to save and run your labs."
    >
      <RegisterForm
        error={actionData?.error}
        pending={navigation.state === "submitting"}
      />
    </AuthLayout>
  );
}
