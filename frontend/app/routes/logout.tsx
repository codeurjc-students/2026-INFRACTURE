import { redirect } from "react-router";
import { logout } from "~/features/auth/api/auth-service";


export async function clientAction() {
  await logout();
  return redirect("/login");
}
