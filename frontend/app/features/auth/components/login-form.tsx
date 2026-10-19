import { CircleAlertIcon } from "lucide-react";
import { Form, Link } from "react-router";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Field, FieldGroup, FieldLabel } from "~/components/ui/field";
import { Input } from "~/components/ui/input";
import { Spinner } from "~/components/ui/spinner";
import PasswordInput from "./password-input";

type LoginFormProps = {
  error?: string;
  pending?: boolean;
};

export default function LoginForm({ error, pending = false }: LoginFormProps) {
  return (
    <Form method="post" noValidate className="flex flex-col gap-6">
      {error && (
        <Alert variant="destructive" className="enter-soft">
          <CircleAlertIcon />
          <AlertTitle>Could not sign in</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <FieldGroup className="gap-5 [@media(max-height:720px)]:gap-3">
        <Field>
          <FieldLabel htmlFor="login-email">Email</FieldLabel>
          <Input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={254}
            required
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="login-password">Password</FieldLabel>
          <PasswordInput
            id="login-password"
            name="password"
            autoComplete="current-password"
            required
          />
        </Field>
        <Button type="submit" size="lg" disabled={pending}>
          {pending && <Spinner data-icon="inline-start" />}
          {pending ? "Signing in…" : "Sign in"}
        </Button>
      </FieldGroup>
      <p className="type-body text-muted-foreground">
        New to Infracture?{" "}
        <Link
          to="/register"
          className="text-foreground underline underline-offset-4"
        >
          Create an account
        </Link>
      </p>
    </Form>
  );
}
