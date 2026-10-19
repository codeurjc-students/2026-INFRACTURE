import type { Route } from "./+types/private-layout";
import { Outlet } from "react-router";
import { requireUser } from "~/features/auth/session";

export async function clientLoader({ request }: Route.ClientLoaderArgs) {
  return { user: await requireUser(request) };
}

export default function PrivateLayout() {
  return <Outlet />;
}
