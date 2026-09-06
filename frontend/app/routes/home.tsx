import type { Route } from "./+types/home";
import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import ComponentCatalogue from "~/features/component-catalogue/components/component-catalogue";

export function meta() {
  return [
    { title: "Component catalogue | Infracture" },
    {
      name: "description",
      content: "Explore the infrastructure components available in Infracture.",
    },
  ];
}

export async function clientLoader() {
  const componentTemplates = await getComponentTemplates();
  return { componentTemplates };
}

export function ErrorBoundary() {
  return (
    <main className="mx-auto min-h-screen max-w-5xl px-6 py-12">
      <section aria-labelledby="catalogue-error-heading">
        <h1
          id="catalogue-error-heading"
          className="text-3xl font-semibold tracking-tight text-gray-950 dark:text-white"
        >
          Component catalogue unavailable
        </h1>
        <p role="alert" className="mt-3 max-w-2xl text-gray-600 dark:text-gray-300">
          Check that PostgreSQL and the backend are running, then reload this
          page.
        </p>
      </section>
    </main>
  );
}

export default function Home({ loaderData }: Route.ComponentProps) {
  return (
    <main className="mx-auto min-h-screen max-w-5xl px-6 py-12">
      <ComponentCatalogue
        componentTemplates={loaderData.componentTemplates}
      />
    </main>
  );
}
