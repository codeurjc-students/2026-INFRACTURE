import { type RouteConfig, index, layout, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("catalogue", "routes/component-catalogue.tsx"),
  route("login", "routes/login.tsx"),
  route("register", "routes/register.tsx"),
  route("logout", "routes/logout.tsx"),
  layout("routes/private-layout.tsx", [
    route("account", "routes/account.tsx"),
  ]),
] satisfies RouteConfig;
