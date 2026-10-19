import { CircleAlertIcon } from "lucide-react";
import { Form, Link } from "react-router";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "~/components/ui/field";
import { Input } from "~/components/ui/input";
import { Spinner } from "~/components/ui/spinner";
import PasswordInput from "./password-input";

type RegisterFormProps = {
  error?: string;
  pending?: boolean;
};

export default function RegisterForm({ error, pending = false }: RegisterFormProps) {
  return (
    <Form method="post" noValidate className="flex flex-col gap-6">
      {error && (
        <Alert variant="destructive" className="enter-soft">
          <CircleAlertIcon />
          <AlertTitle>Could not create your account</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <FieldGroup className="gap-5 [@media(max-height:720px)]:gap-3">
        <Field>
          <FieldLabel htmlFor="register-displayName">Name</FieldLabel>
          <Input
            id="register-displayName"
            name="displayName"
            autoComplete="name"
            maxLength={30}
            required
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="register-username">Username</FieldLabel>
          <Input
            id="register-username"
            name="username"
            autoComplete="username"
            maxLength={20}
            aria-describedby="register-username-help"
            required
          />
          <FieldDescription id="register-username-help">
            3 to 20 letters, numbers or underscores.
          </FieldDescription>
        </Field>
        <Field>
          <FieldLabel htmlFor="register-email">Email</FieldLabel>
          <Input
            id="register-email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={254}
            required
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="register-password">Password</FieldLabel>
          <PasswordInput
            id="register-password"
            name="password"
            autoComplete="new-password"
            aria-describedby="register-password-help"
            required
          />
          <FieldDescription id="register-password-help">
            At least 15 characters, mixing cases, a number and a symbol.
          </FieldDescription>
        </Field>
        <Button type="submit" size="lg" disabled={pending}>
          {pending && <Spinner data-icon="inline-start" />}
          {pending ? "Creating account…" : "Create account"}
        </Button>
      </FieldGroup>
      <p className="type-body text-muted-foreground">
        Already have an account?{" "}
        <Link
          to="/login"
          className="text-foreground underline underline-offset-4"
        >
          Sign in
        </Link>
      </p>
    </Form>
  );
}
