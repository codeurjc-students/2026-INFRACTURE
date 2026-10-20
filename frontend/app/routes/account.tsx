import { useRouteLoaderData } from "react-router";
import AppHeader from "~/components/app-header";
import type { clientLoader as privateLoader } from "./private-layout";

export function meta() {
  return [{ title: "Your account | Infracture" }];
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
        </main>
      </div>
    </div>
  );
}
