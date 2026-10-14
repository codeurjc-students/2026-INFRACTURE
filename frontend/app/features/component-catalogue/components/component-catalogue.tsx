import { useState } from "react";
import type { PageDTO } from "~/shared/model/PageDTO";
import { getComponentTemplates } from "../api/component-template-service";
import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

interface ComponentCatalogueProps {
  initialPage: PageDTO<ComponentTemplateDTO>;
}

export default function ComponentCatalogue({
  initialPage,
}: ComponentCatalogueProps) {
  const [catalogue, setCatalogue] = useState(initialPage);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const componentTemplates = catalogue.content;
  const hasMore = catalogue.page.number + 1 < catalogue.page.totalPages;

  async function loadMore() {
    setLoading(true);
    setError(false);

    try {
      const nextPage = await getComponentTemplates(catalogue.page.number + 1);
      setCatalogue((current) => {
        const existingKeys = new Set(
          current.content.map((template) => template.key),
        );
        return {
          ...nextPage,
          content: [
            ...current.content,
            ...nextPage.content.filter(
              (template) => !existingKeys.has(template.key),
            ),
          ],
        };
      });
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  return (
    <section aria-labelledby="component-catalogue-heading">
      <header className="mb-8 border-b border-gray-200 pb-6 dark:border-gray-800">
        <p className="mb-2 font-mono text-sm font-medium uppercase tracking-wider text-blue-700 dark:text-blue-300">
          Infrastructure building blocks
        </p>
        <h1
          id="component-catalogue-heading"
          className="text-3xl font-semibold tracking-tight text-gray-950 dark:text-white"
        >
          Component catalogue
        </h1>
        <p className="mt-3 max-w-2xl text-gray-600 dark:text-gray-300">
          Choose from the component templates currently available in the
          platform catalogue.
        </p>
      </header>

      {componentTemplates.length === 0 ? (
        <p className="rounded-lg border border-dashed border-gray-300 p-6 text-gray-600 dark:border-gray-700 dark:text-gray-300">
          No component templates are currently available.
        </p>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {componentTemplates.map((template) => (
            <li
              key={template.key}
              className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900"
            >
              <h2 className="text-lg font-semibold text-gray-950 dark:text-white">
                {template.name}
              </h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div>
                  <dt className="text-gray-500 dark:text-gray-400">Type</dt>
                  <dd className="mt-1 font-medium text-gray-800 dark:text-gray-200">
                    {template.type}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500 dark:text-gray-400">Key</dt>
                  <dd className="mt-1 font-mono text-gray-800 dark:text-gray-200">
                    {template.key}
                  </dd>
                </div>
              </dl>
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p role="alert" className="mt-6 text-red-700 dark:text-red-300">
          Could not load more component templates. Please try again.
        </p>
      )}

      <p role="status" className="mt-6 text-gray-600 dark:text-gray-300">
        {loading
          ? "Loading more component templates…"
          : `${componentTemplates.length} of ${catalogue.page.totalElements} component templates`}
      </p>

      {hasMore && (
        <button
          type="button"
          onClick={loadMore}
          disabled={loading}
          className="mt-4 rounded-lg bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:cursor-wait disabled:opacity-60 dark:bg-blue-600 dark:hover:bg-blue-500"
        >
          {loading ? "Loading…" : error ? "Try again" : "More results"}
        </button>
      )}
    </section>
  );
}
