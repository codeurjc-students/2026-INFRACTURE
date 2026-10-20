import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import { CircleXIcon } from "lucide-react";
import type { Route } from "./+types/root";
import Logo from "~/components/logo";
import { buttonVariants } from "~/components/ui/button";
import "./app.css";

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

export function HydrateFallback() {
  return (
    <main className="mx-auto min-h-screen max-w-5xl px-6 py-12">
      <p role="status" className="type-body text-muted-foreground">
        Loading…
      </p>
    </main>
  );
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let status = "Error";
  let title = "Something broke";
  let details =
    "An unexpected error occurred. Go back to the home page and try again.";
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    status = String(error.status);
    if (error.status === 404) {
      title = "Page not found";
      details = "The requested page could not be found.";
    } else {
      details = error.statusText || details;
    }
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  // Plain links: this boundary can render outside the router.
  return (
    <main className="mx-auto flex min-h-svh max-w-5xl flex-col items-start justify-center gap-6 px-6 py-12">
      <a href="/" aria-label="Infracture home" className="text-foreground">
        <Logo className="h-6 w-auto" />
      </a>
      <p className="inline-flex items-center gap-2 font-mono type-body text-destructive">
        <CircleXIcon aria-hidden="true" className="size-4" />
        {status}
      </p>
      <h1 className="type-headline">{title}</h1>
      <p className="max-w-[52ch] type-title text-muted-foreground">{details}</p>
      <a href="/" className={buttonVariants({ size: "lg", className: "px-4" })}>
        Go to home
      </a>
      {stack && (
        <pre className="w-full overflow-x-auto rounded-xl border bg-card p-4 font-mono type-small">
          <code>{stack}</code>
        </pre>
      )}
    </main>
  );
}
