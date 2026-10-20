import { Link, NavLink, useRouteLoaderData } from "react-router";
import Logo from "~/components/logo";
import type { clientLoader as privateLoader } from "~/routes/private-layout";

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join("");
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-2.5 py-1.5 type-body no-underline active:bg-accent motion-safe:transition-colors ${
    isActive
      ? "bg-accent text-foreground"
      : "text-muted-foreground pointer-fine:hover:text-foreground"
  }`;

// Full-width app bar of the private pages: logo on the left, sections in the middle, account on the right.
export default function AppHeader() {
  const user = useRouteLoaderData<typeof privateLoader>(
    "routes/private-layout",
  )?.user;

  return (
    <header className="sticky top-0 z-10 border-b bg-background">
      <div className="mx-auto grid h-14 max-w-7xl grid-cols-[1fr_auto_1fr] items-center gap-4 px-4 sm:px-6">
        <Link
          to="/"
          className="justify-self-start text-foreground"
          aria-label="Infracture home"
        >
          <Logo className="h-[18px] w-auto" />
        </Link>
        <nav aria-label="Main" className="flex min-w-0 items-center gap-1">
          <NavLink to="/projects" className={navLinkClass}>
            Projects
          </NavLink>
          <NavLink
            to="/catalogue"
            className={(state) =>
              `hidden sm:inline-flex ${navLinkClass(state)}`
            }
          >
            Component catalogue
          </NavLink>
        </nav>
        {user && (
          <Link
            to="/account"
            aria-label="Your account"
            className="col-start-3 grid size-8 justify-self-end place-items-center rounded-md border bg-card type-caption font-semibold text-foreground active:bg-accent pointer-fine:hover:bg-accent motion-safe:transition-colors"
          >
            {initials(user.displayName)}
          </Link>
        )}
      </div>
    </header>
  );
}
