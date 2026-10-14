import type { Route } from "./+types/home";
import { useRevalidator } from "react-router";
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
  const revalidator = useRevalidator();

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
          The catalogue could not be loaded. Please try again.
        </p>
        <button
          type="button"
          onClick={() => revalidator.revalidate()}
          disabled={revalidator.state === "loading"}
          className="mt-4 rounded-lg bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:cursor-wait disabled:opacity-60 dark:bg-blue-600 dark:hover:bg-blue-500"
        >
          {revalidator.state === "loading" ? "Loading…" : "Try again"}
        </button>
      </section>
    </main>
  );
}

export default function Home({
  loaderData,
}: Pick<Route.ComponentProps, "loaderData">) {
  return (
    <main className="mx-auto min-h-screen max-w-5xl px-6 py-12">
      <ComponentCatalogue
        initialPage={loaderData.componentTemplates}
      />
    </main>
  );
}
