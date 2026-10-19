import { Link, useRouteLoaderData } from "react-router";
import type { clientLoader as privateLoader } from "./private-layout";

export function meta() {
  return [{ title: "Your account | Infracture" }];
}

export default function AccountRoute() {
  const { user } = useRouteLoaderData<typeof privateLoader>("routes/private-layout")!;

  return (
    <main className="mx-auto flex min-h-svh max-w-5xl flex-col items-start justify-center gap-6 px-6 py-12">
      <h1 className="type-page-title">Your account</h1>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 type-body">
        <dt className="text-muted-foreground">Name</dt>
        <dd>{user.displayName}</dd>
        <dt className="text-muted-foreground">Username</dt>
        <dd>{user.username}</dd>
        <dt className="text-muted-foreground">Email</dt>
        <dd>{user.email}</dd>
      </dl>
      <Link to="/" className="type-body underline underline-offset-4">
        Back to home
      </Link>
    </main>
  );
}
