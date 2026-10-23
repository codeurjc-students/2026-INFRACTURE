import type { ReactNode } from "react";
import {
  data,
  isRouteErrorResponse,
  Link,
  useRevalidator,
  useRouteError,
} from "react-router";
import AppHeader from "~/components/app-header";
import { Button, buttonVariants } from "~/components/ui/button";
import { HttpError } from "~/shared/api/api-fetch";

export default function DetailsPage({
  breadcrumb,
  children,
}: {
  breadcrumb?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="min-h-svh">
      <AppHeader />
      <div className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
        <main className="flex flex-col gap-6 pt-8">
          {breadcrumb}
          {children}
        </main>
      </div>
    </div>
  );
}

// Error boundary body for a project or scenario page: not found (404/400) or retry.
export function DetailsError({ subject }: { subject: "Project" | "Scenario" }) {
  const error = useRouteError();
  const revalidator = useRevalidator();

  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <DetailsPage>
        <div className="flex max-w-3xl flex-col items-start gap-6">
          <h1 className="type-headline">{`${subject} not found`}</h1>
          <p className="type-title text-muted-foreground">
            It does not exist, or it belongs to another account.
          </p>
          <Link
            to="/projects"
            className={buttonVariants({ size: "lg", className: "px-4" })}
          >
            Go to your projects
          </Link>
        </div>
      </DetailsPage>
    );
  }

  return (
    <DetailsPage>
      <section className="flex max-w-3xl flex-col items-start gap-4 rounded-xl border bg-card p-5 sm:p-8">
        <h1 className="type-page-title">{`${subject} unavailable`}</h1>
        <p role="alert" className="type-body text-muted-foreground">
          {`This ${subject.toLowerCase()} could not be loaded. Try again in a moment.`}
        </p>
        <Button
          type="button"
          size="lg"
          className="px-4"
          onClick={() => revalidator.revalidate()}
          disabled={revalidator.state === "loading"}
        >
          {revalidator.state === "loading" ? "Loading…" : "Try again"}
        </Button>
      </section>
    </DetailsPage>
  );
}

// A missing, foreign or malformed id becomes the 404 that DetailsError shows.
export async function loadOrNotFound<T>(load: Promise<T>) {
  try {
    return await load;
  } catch (error) {
    if (
      error instanceof HttpError &&
      (error.status === 404 || error.status === 400)
    ) {
      throw data(null, { status: 404 });
    }
    throw error;
  }
}
