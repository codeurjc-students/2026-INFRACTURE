import type { Route } from "./+types/account";
import { redirect, useRouteLoaderData } from "react-router";
import AppHeader from "~/components/app-header";
import { Field, FieldDescription, FieldLabel } from "~/components/ui/field";
import { Input } from "~/components/ui/input";
import {
  changeEmail,
  changePassword,
  removeAvatar,
  updateProfile,
  uploadAvatar,
} from "~/features/auth/api/auth-service";
import AccountForm, {
  type AccountFormResult,
} from "~/features/auth/components/account-form";
import AvatarForm from "~/features/auth/components/avatar-form";
import PasswordInput from "~/features/auth/components/password-input";
import { HttpError } from "~/shared/api/api-fetch";
import type { clientLoader as privateLoader } from "./private-layout";

export function meta() {
  return [{ title: "Your account | Infracture" }];
}

function saveAccount(form: FormData) {
  const field = (name: string) => String(form.get(name) ?? "");
  switch (form.get("intent")) {
    case "profile":
      return updateProfile({
        displayName: form.has("displayName") ? field("displayName") : undefined,
        username: form.has("username") ? field("username") : undefined,
      });
    case "avatar":
      return uploadAvatar(form.get("avatar") as Blob);
    case "remove-avatar":
      return removeAvatar();
    case "email":
      return changeEmail({
        email: field("email"),
        currentPassword: field("currentPassword"),
      });
    default:
      return changePassword({
        currentPassword: field("currentPassword"),
        newPassword: field("newPassword"),
      });
  }
}

// Messages the server writes for the user: invalid data, a taken name or email, an image too large and
// the image storage being down.
const USER_ERRORS = [400, 409, 413, 503];

// The route revalidates after this action, so the page and the header show the stored account.
export async function clientAction({
  request,
}: Route.ClientActionArgs): Promise<AccountFormResult> {
  try {
    await saveAccount(await request.formData());
    return { saved: true };
  } catch (error) {
    if (error instanceof HttpError && error.status === 401) {
      throw redirect(
        `/login?${new URLSearchParams({ redirectTo: "/account" })}`,
      );
    }
    return {
      error:
        error instanceof HttpError && USER_ERRORS.includes(error.status)
          ? error.message
          : "Something went wrong. Try again in a moment.",
    };
  }
}

export default function AccountRoute() {
  const { user } = useRouteLoaderData<typeof privateLoader>(
    "routes/private-layout",
  )!;

  return (
    <div className="min-h-svh">
      <AppHeader />
      <div className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
        <main className="flex max-w-3xl flex-col gap-6 pt-8">
          <h1 className="type-headline">Your account</h1>
          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-3 rounded-xl border bg-card p-5 type-body sm:p-6">
            <dt className="text-muted-foreground">Name</dt>
            <dd className="[overflow-wrap:anywhere]">{user.displayName}</dd>
            <dt className="text-muted-foreground">Username</dt>
            <dd className="[overflow-wrap:anywhere]">{user.username}</dd>
            <dt className="text-muted-foreground">Email</dt>
            <dd className="[overflow-wrap:anywhere]">{user.email}</dd>
          </dl>
          <AvatarForm user={user} />
          <AccountForm
            intent="profile"
            title="Profile"
            submitLabel="Save profile"
            pendingLabel="Saving…"
            savedMessage="Profile saved"
          >
            <Field>
              <FieldLabel htmlFor="account-displayName">Name</FieldLabel>
              <Input
                id="account-displayName"
                name="displayName"
                autoComplete="name"
                maxLength={30}
                defaultValue={user.displayName}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="account-username">Username</FieldLabel>
              <Input
                id="account-username"
                name="username"
                autoComplete="username"
                maxLength={20}
                defaultValue={user.username}
                aria-describedby="account-username-help"
                required
              />
              <FieldDescription id="account-username-help">
                3 to 20 letters, numbers or underscores.
              </FieldDescription>
            </Field>
          </AccountForm>
          <AccountForm
            intent="email"
            title="Email"
            description="You sign in with this email. Changing it signs you out on your other devices."
            submitLabel="Change email"
            pendingLabel="Changing…"
            savedMessage="Email changed"
          >
            <Field>
              <FieldLabel htmlFor="account-email">New email</FieldLabel>
              <Input
                id="account-email"
                name="email"
                type="email"
                autoComplete="email"
                maxLength={254}
                defaultValue={user.email}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="account-email-password">
                Current password
              </FieldLabel>
              <PasswordInput
                id="account-email-password"
                name="currentPassword"
                autoComplete="current-password"
                required
              />
            </Field>
          </AccountForm>
          <AccountForm
            intent="password"
            title="Password"
            description="Changing it signs you out on your other devices."
            submitLabel="Change password"
            pendingLabel="Changing…"
            savedMessage="Password changed"
          >
            <Field>
              <FieldLabel htmlFor="account-current-password">
                Current password
              </FieldLabel>
              <PasswordInput
                id="account-current-password"
                name="currentPassword"
                autoComplete="current-password"
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="account-new-password">
                New password
              </FieldLabel>
              <PasswordInput
                id="account-new-password"
                name="newPassword"
                autoComplete="new-password"
                aria-describedby="account-new-password-help"
                required
              />
              <FieldDescription id="account-new-password-help">
                At least 15 characters, mixing cases, a number and a symbol.
              </FieldDescription>
            </Field>
          </AccountForm>
        </main>
      </div>
    </div>
  );
}
