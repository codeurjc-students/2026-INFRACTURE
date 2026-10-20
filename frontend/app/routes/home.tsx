import type { Route } from "./+types/home";
import { Form, Link } from "react-router";
import Logo from "~/components/logo";
import { Button, buttonVariants } from "~/components/ui/button";
import { getCurrentUser } from "~/features/auth/api/auth-service";

export function meta() {
  return [{ title: "Infracture" }];
}

export async function clientLoader() {
  const user = await getCurrentUser().catch(() => null);
  return { user };
}

export default function Home({
  loaderData,
}: Pick<Route.ComponentProps, "loaderData">) {
  const { user } = loaderData;

  return (
    <main className="mx-auto flex min-h-svh max-w-5xl flex-col items-start justify-center gap-6 px-6 py-12">
      <Logo className="h-8 w-auto" />
      <p className="type-title text-muted-foreground">
        Design distributed systems, inject faults and see how they recover.
      </p>
      <div className="flex flex-wrap gap-3">
        {user && (
          <Link to="/projects" className={buttonVariants({ size: "lg" })}>
            Open your projects
          </Link>
        )}
        <Link
          to="/catalogue"
          className={buttonVariants({
            size: "lg",
            variant: user ? "outline" : "default",
          })}
        >
          Open component catalogue
        </Link>
      </div>
      {user ? (
        <Form
          method="post"
          action="/logout"
          className="flex items-center gap-3"
        >
          <p className="type-body text-muted-foreground">
            Signed in as{" "}
            <Link
              to="/account"
              className="text-foreground underline underline-offset-4"
            >
              {user.displayName}
            </Link>
          </p>
          <Button type="submit" variant="outline">
            Sign out
          </Button>
        </Form>
      ) : (
        <Link to="/login" className={buttonVariants({ variant: "outline" })}>
          Sign in
        </Link>
      )}
    </main>
  );
}
